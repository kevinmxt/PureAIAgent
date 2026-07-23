package me.maxt.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.api.ApiException;
import me.maxt.api.ChatApiClient;
import me.maxt.api.DeepSeekApiClient;
import me.maxt.model.Message;
import me.maxt.model.ToolCall;
import me.maxt.tool.Tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SKILL 工具包装器。将 SKILL.md 包装为 Tool，被调用时启动子 agent 多轮对话循环。
 */
public class SkillTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Skill skill;
    private final List<Message> parentMessages;
    private final ChatApiClient apiClient;
    private final List<Tool> subTools;
    private final int maxTurns;
    private final int contextMessages;
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    public SkillTool(Skill skill, List<Message> parentMessages, ChatApiClient apiClient,
                     List<Tool> allTools, int maxTurns, int contextMessages) {
        this.skill = skill;
        this.parentMessages = parentMessages;
        this.apiClient = apiClient;
        this.maxTurns = maxTurns;
        this.contextMessages = contextMessages;

        // 子 agent 工具列表：排除自身，防递归
        this.subTools = new ArrayList<>();
        for (Tool t : allTools) {
            if (!t.name().equals(skill.getName())) {
                subTools.add(t);
            }
        }
    }

    @Override
    public String name() {
        return skill.getName();
    }

    @Override
    public String description() {
        return skill.getDescription();
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode());
        return schema;
    }

    @Override
    public String execute(JsonNode arguments) throws Exception {
        interrupted.set(false);
        String skillName = skill.getName();
        System.out.println("[技能 " + skillName + "] 开始执行...");

        List<Message> subMessages = buildSubMessages();
        Thread listener = startInterruptListener();

        try {
            int turn = 0;
            while (turn < maxTurns) {
                if (interrupted.get()) {
                    System.out.println("[技能 " + skillName + "] 用户中断");
                    return "[技能 " + skillName + " 已中断] 用户取消了执行。";
                }

                Message response;
                try {
                    response = apiClient.chat(subMessages, subTools);
                } catch (ApiException e) {
                    return "[技能 " + skillName + " 执行失败] API错误: "
                            + DeepSeekApiClient.parseError(e.getMessage());
                }
                subMessages.add(response);

                if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                    for (ToolCall tc : response.getToolCalls()) {
                        System.out.println("[技能 " + skillName + " 工具调用] "
                                + tc.getFunction().getName() + ": " + tc.getFunction().getArguments());
                        String result = executeSubTool(tc);
                        System.out.println("[技能 " + skillName + " 工具结果] " + result);
                        subMessages.add(new Message("tool", result, null, null, tc.getId()));
                    }
                    turn++;
                    continue;
                }

                String finalContent = response.getContent() != null ? response.getContent() : "";
                System.out.println("[技能 " + skillName + "] 执行完成 (" + (turn + 1) + " 轮)");
                return finalContent;
            }

            return "[技能 " + skillName + " 已达最大轮数] 执行了 " + maxTurns
                    + " 轮后被中断。请尝试简化需求或增加 skill.max_turns 配置。";
        } finally {
            interrupted.set(true); // 停止监听线程
            try {
                listener.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 取消正在执行的子 agent。
     */
    public void cancel() {
        interrupted.set(true);
    }

    // ============ 内部方法 ============

    private List<Message> buildSubMessages() {
        List<Message> sub = new ArrayList<>();
        sub.add(new Message("system", skill.getContent()));

        int total = parentMessages.size();
        if (total == 0) {
            return sub;
        }

        // 找到最后一条 user 消息的位置，只复制到该位置（含），排除之后的消息
        // 这样可以避免将主对话中触发本 skill 的 assistant tool_calls 消息带入子 agent
        int lastUserIdx = -1;
        for (int i = total - 1; i >= 0; i--) {
            if ("user".equals(parentMessages.get(i).getRole())) {
                lastUserIdx = i;
                break;
            }
        }

        if (lastUserIdx >= 0) {
            int start = Math.max(0, lastUserIdx + 1 - contextMessages);
            sub.addAll(parentMessages.subList(start, lastUserIdx + 1));
        }

        return sub;
    }

    private String executeSubTool(ToolCall tc) {
        try {
            JsonNode args = MAPPER.readTree(tc.getFunction().getArguments());
            for (Tool tool : subTools) {
                if (tool.name().equals(tc.getFunction().getName())) {
                    return tool.execute(args);
                }
            }
            return "未找到工具: " + tc.getFunction().getName();
        } catch (Exception e) {
            return "工具执行出错: " + e.getMessage();
        }
    }

    private Thread startInterruptListener() {
        Thread listener = new Thread(() -> {
            try {
                while (!interrupted.get()) {
                    if (System.in.available() > 0) {
                        int c = System.in.read();
                        if (c == 'q' || c == 'Q') {
                            interrupted.set(true);
                            break;
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ignored) {
                // 监听线程退出
            }
        });
        listener.setDaemon(true);
        listener.start();
        return listener;
    }
}
