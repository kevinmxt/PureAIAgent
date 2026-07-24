package me.maxt.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.AgentLoop;
import me.maxt.api.ApiException;
import me.maxt.api.ChatApiClient;
import me.maxt.api.DeepSeekApiClient;
import me.maxt.model.Message;
import me.maxt.model.ToolCall;
import me.maxt.tool.Tool;
import me.maxt.tool.confirmation.AlwaysAllowPolicy;

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
            AgentLoop loop = new AgentLoop(apiClient, subTools,
                    new AlwaysAllowPolicy(), maxTurns,
                    () -> interrupted.get());

            AgentLoop.Callback callback = new AgentLoop.Callback() {
                @Override
                public void onBeforeTool(ToolCall tc) {
                    System.out.println("[技能 " + skillName + " 工具调用] "
                            + tc.getFunction().getName() + ": " + tc.getFunction().getArguments());
                }

                @Override
                public void onAfterTool(ToolCall tc, String result) {
                    System.out.println("[技能 " + skillName + " 工具结果] " + result);
                }
            };

            Message finalMsg;
            try {
                finalMsg = loop.run(subMessages, callback);
            } catch (ApiException e) {
                return "[技能 " + skillName + " 执行失败] API错误: "
                        + DeepSeekApiClient.parseError(e.getMessage());
            }

            if (interrupted.get()) {
                System.out.println("[技能 " + skillName + "] 用户中断");
                return "[技能 " + skillName + " 已中断] 用户取消了执行。";
            }

            // 最后一条 assistant 消息仍含 toolCalls → 达到最大轮数
            Message lastMsg = subMessages.get(subMessages.size() - 1);
            if ("assistant".equals(lastMsg.getRole())
                    && lastMsg.getToolCalls() != null && !lastMsg.getToolCalls().isEmpty()) {
                return "[技能 " + skillName + " 已达最大轮数] 执行了 " + maxTurns
                        + " 轮后被中断。请尝试简化需求或增加 skill.max_turns 配置。";
            }

            int assistantCount = 0;
            for (Message m : subMessages) {
                if ("assistant".equals(m.getRole())) assistantCount++;
            }
            System.out.println("[技能 " + skillName + "] 执行完成 (" + assistantCount + " 轮)");
            return finalMsg != null && finalMsg.getContent() != null ? finalMsg.getContent() : "";
        } finally {
            interrupted.set(true);
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
