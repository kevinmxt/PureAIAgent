package me.maxt;

import me.maxt.api.ApiException;
import me.maxt.api.ChatApiClient;
import me.maxt.api.DeepSeekApiClient;
import me.maxt.api.DeltaEvent;
import me.maxt.api.DeltaHandler;
import me.maxt.config.AppConfig;
import me.maxt.model.Message;
import me.maxt.model.TokenUsageStats;
import me.maxt.model.ToolCall;
import me.maxt.tool.ShellTool;
import me.maxt.tool.Tool;
import me.maxt.tool.confirmation.AlwaysAllowPolicy;
import me.maxt.tool.confirmation.InteractiveConsolePolicy;
import me.maxt.tool.confirmation.ToolConfirmationPolicy;
import me.maxt.tool.excel.ExcelTool;
import me.maxt.skill.Skill;
import me.maxt.skill.SkillLoader;
import me.maxt.skill.SkillTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * AI 命令行对话程序。
 * 对话流程编排，不包含任何模型专属的请求构建/响应解析逻辑。
 */
public class SimpleAIChat {

    private final ChatApiClient apiClient;
    private final List<Message> messages = new ArrayList<>();
    private final List<Tool> tools;
    private ToolConfirmationPolicy confirmationPolicy = new AlwaysAllowPolicy();
    private Terminal terminal;
    private LineReader reader;

    public SimpleAIChat(ChatApiClient apiClient) {
        this.apiClient = apiClient;
        this.tools = new ArrayList<>(List.of(new ShellTool()));
    }

    public SimpleAIChat(ChatApiClient apiClient, AppConfig config) {
        this.apiClient = apiClient;
        List<Tool> baseTools = new ArrayList<>();
        baseTools.add(new ShellTool());
        baseTools.add(new ExcelTool(config));

        // 加载 SKILL
        SkillLoader loader = new SkillLoader(Path.of(config.getSkillDir()));
        List<Skill> skills = loader.load();

        // 收集内置工具名
        Set<String> builtInNames = new HashSet<>();
        for (Tool t : baseTools) {
            builtInNames.add(t.name());
        }

        // 注册技能为工具
        int registered = 0;
        for (Skill skill : skills) {
            if (builtInNames.contains(skill.getName())) {
                System.out.println("[警告] SKILL '" + skill.getName() + "' 与内置工具重名，已跳过");
                continue;
            }
            baseTools.add(new SkillTool(skill, messages, apiClient, baseTools,
                    config.getSkillMaxTurns(), config.getSkillContextMessages()));
            builtInNames.add(skill.getName());
            registered++;
        }

        this.tools = baseTools;
        if (registered > 0) {
            System.out.println("已加载 " + registered + " 个SKILL");
        }
    }

    SimpleAIChat(ChatApiClient apiClient, List<Tool> tools) {
        this.apiClient = apiClient;
        this.tools = new ArrayList<>(tools);
    }

    // ============ 访问器 ============

    public List<Message> getMessages() {
        return messages;
    }

    public void setTerminal(Terminal terminal, LineReader reader) {
        this.terminal = terminal;
        this.reader = reader;
        this.confirmationPolicy = new InteractiveConsolePolicy(terminal, reader);
    }

    // ============ 主程序 ============

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  简单AI对话程序 (JDK21原生实现)");
        System.out.println("  输入 '退出' 或 'exit' 结束对话");
        System.out.println("  输入 '对话历史' 或 'history' 查看历史对话");
        System.out.println("  输入 '清空历史' 或 'clear' 清除历史对话");
        System.out.println("  输入 'debug' 查看调试信息");
        System.out.println("  输入 'tokens' 查看 Token 用量统计");
        System.out.println("  输入 '/reload-skills' 重新加载 SKILL");
        System.out.println("  输入 '/skill名' 触发 SKILL（如 /code-review）");
        System.out.println("=================================\n");

        AppConfig config = AppConfig.load();
        ChatApiClient client = new DeepSeekApiClient(config);
        SimpleAIChat chat = new SimpleAIChat(client, config);

        try {
            Terminal terminal = TerminalBuilder.builder()
                    .encoding(StandardCharsets.UTF_8)
                    .build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
            chat.setTerminal(terminal, reader);

            while (true) {
                String userInput = reader.readLine("\n你: ");
                if (userInput == null) {
                    break;
                }
                userInput = userInput.trim();

                if (userInput.isEmpty()) {
                    continue;
                }
                if ("退出".equals(userInput) || "exit".equalsIgnoreCase(userInput)) {
                    System.out.println("再见！期待下次对话~");
                    break;
                }
                if ("对话历史".equals(userInput) || "history".equalsIgnoreCase(userInput)) {
                    System.out.println(chat.messages);
                    continue;
                }
                if ("清空历史".equals(userInput) || "clear".equalsIgnoreCase(userInput)) {
                    chat.messages.clear();
                    System.out.println("已清空对话历史");
                    continue;
                }
                if ("debug".equals(userInput)) {
                    System.out.println(chat.apiClient.getRawResponses());
                    continue;
                }
                if ("tokens".equals(userInput)) {
                    System.out.println(TokenUsageStats.summary());
                    continue;
                }
                if ("/reload-skills".equals(userInput)) {
                    chat.reloadSkills(config);
                    continue;
                }

                if (config.isStream()) {
                    chat.streamChat(userInput);
                } else {
                    chat.commonResponse(userInput);
                }
            }
        } catch (Exception e) {
            System.err.println("程序运行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============ 非流式对话 ============

    void commonResponse(String userInput) throws Exception {
        TokenUsageStats.clearSession();
        messages.add(new Message("user", userInput));

        AgentLoop loop = new AgentLoop(apiClient, tools, confirmationPolicy, Integer.MAX_VALUE);
        AgentLoop.Callback callback = new AgentLoop.Callback() {
            @Override
            public void onBeforeTool(ToolCall tc) {
                System.out.println("[工具调用] " + tc.getFunction().getName() + ": " + tc.getFunction().getArguments());
            }

            @Override
            public void onAfterTool(ToolCall tc, String result) {
                System.out.println("[工具结果] " + result);
            }
        };

        Message assistantMsg;
        try {
            assistantMsg = loop.run(messages, callback);
        } catch (ApiException e) {
            System.out.println("API错误 (状态码: " + e.getStatusCode() + "): "
                    + DeepSeekApiClient.parseError(e.getMessage()));
            return;
        }

        if (assistantMsg.getReasoningContent() != null && !assistantMsg.getReasoningContent().isEmpty()) {
            System.out.println("[思考过程] " + assistantMsg.getReasoningContent());
            System.out.println("---");
        }
        System.out.println("AI: " + (assistantMsg.getContent() != null ? assistantMsg.getContent() : ""));
        TokenUsageStats.accumulateTotal();
    }

    // ============ 流式对话 ============

    void streamChat(String userMessage) throws Exception {
        TokenUsageStats.clearSession();
        messages.add(new Message("user", userMessage));

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        boolean[] inReasoning = {false};
        boolean[] startedContent = {false};

        DeltaHandler handler = event -> {
            if (event.reasoningContent() != null && !event.reasoningContent().isEmpty()) {
                if (!inReasoning[0]) {
                    System.out.println();
                    System.out.print("[思考过程] ");
                    inReasoning[0] = true;
                }
                System.out.print(event.reasoningContent());
                reasoningBuilder.append(event.reasoningContent());
            }

            if (event.content() != null && !event.content().isEmpty()) {
                if (inReasoning[0]) {
                    System.out.println();
                    System.out.println("---");
                    System.out.print("AI: ");
                    inReasoning[0] = false;
                }
                if (!startedContent[0]) {
                    startedContent[0] = true;
                }
                System.out.print(event.content());
                contentBuilder.append(event.content());
            }
        };

        System.out.print("AI: ");
        try {
            Message result = apiClient.chatStream(messages, handler);
            System.out.println();
            messages.add(result);
            TokenUsageStats.accumulateTotal();
        } catch (ApiException e) {
            System.out.println();
            System.out.println("API错误 (状态码: " + e.getStatusCode() + "): "
                    + DeepSeekApiClient.parseError(e.getMessage()));
        }
    }

    // ============ 工具执行 ============

    void reloadSkills(AppConfig config) {
        // 移除已有的 SkillTool（保留 ShellTool 和 ExcelTool）
        List<Tool> baseTools = new ArrayList<>();
        Set<String> builtInNames = new HashSet<>();
        for (Tool t : tools) {
            if (t instanceof SkillTool) {
                continue;
            }
            baseTools.add(t);
            builtInNames.add(t.name());
        }

        // 重新加载
        SkillLoader loader = new SkillLoader(Path.of(config.getSkillDir()));
        List<Skill> skills = loader.load();

        int registered = 0;
        for (Skill skill : skills) {
            if (builtInNames.contains(skill.getName())) {
                System.out.println("[警告] SKILL '" + skill.getName() + "' 与内置工具重名，已跳过");
                continue;
            }
            baseTools.add(new SkillTool(skill, messages, apiClient, baseTools,
                    config.getSkillMaxTurns(), config.getSkillContextMessages()));
            builtInNames.add(skill.getName());
            registered++;
        }

        this.tools.clear();
        this.tools.addAll(baseTools);
        System.out.println("已重新加载 " + registered + " 个SKILL，共 " + this.tools.size() + " 个工具");
    }

}
