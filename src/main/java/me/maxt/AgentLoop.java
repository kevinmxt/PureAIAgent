package me.maxt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.maxt.api.ChatApiClient;
import me.maxt.model.Message;
import me.maxt.model.ToolCall;
import me.maxt.tool.Tool;
import me.maxt.tool.confirmation.ConfirmationDecision;
import me.maxt.tool.confirmation.ToolConfirmationPolicy;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Agent 循环 — 执行 "调用 API → 检查 toolCalls → 确认 → 执行工具 → 重复" 的多轮对话循环。
 * <p>
 * 同时被 {@link SimpleAIChat}（主对话，交互式确认）和 {@code SkillTool}（子 agent，自动执行）复用。
 */
public class AgentLoop {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatApiClient apiClient;
    private final List<Tool> tools;
    private final ToolConfirmationPolicy confirmationPolicy;
    private final int maxTurns;
    private final BooleanSupplier cancelCheck;

    /**
     * 循环回调 — 调用方通过此接口观察工具执行过程。
     */
    public interface Callback {

        /** 工具即将执行（在确认之前），用于打印日志 */
        default void onBeforeTool(ToolCall tc) {}

        /** 工具已执行，result 为执行结果 */
        default void onAfterTool(ToolCall tc, String result) {}
    }

    public AgentLoop(ChatApiClient apiClient, List<Tool> tools,
                     ToolConfirmationPolicy confirmationPolicy, int maxTurns) {
        this(apiClient, tools, confirmationPolicy, maxTurns, null);
    }

    public AgentLoop(ChatApiClient apiClient, List<Tool> tools,
                     ToolConfirmationPolicy confirmationPolicy, int maxTurns,
                     BooleanSupplier cancelCheck) {
        this.apiClient = apiClient;
        this.tools = tools;
        this.confirmationPolicy = confirmationPolicy;
        this.maxTurns = maxTurns;
        this.cancelCheck = cancelCheck;
    }

    // ============ 主循环 ============

    /**
     * 执行 agent 循环，直到 AI 返回不含 toolCalls 的最终回答、达到最大轮数、或用户要求停止。
     * 过程中会修改传入的 messages 列表（追加 assistant 和 tool 消息）。
     *
     * @param messages  对话历史（会被修改）
     * @param callback  观察者回调
     * @return 最终的 assistant Message
     */
    public Message run(List<Message> messages, Callback callback) throws Exception {
        int turn = 0;

        while (turn < maxTurns) {
            if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                // 返回已有消息列表中的最后一条 assistant 消息
                for (int i = messages.size() - 1; i >= 0; i--) {
                    if ("assistant".equals(messages.get(i).getRole())) {
                        return messages.get(i);
                    }
                }
                return null;
            }

            Message response = apiClient.chat(messages, tools);
            messages.add(response);

            if (response.getToolCalls() == null || response.getToolCalls().isEmpty()) {
                return response;
            }

            for (ToolCall tc : response.getToolCalls()) {
                callback.onBeforeTool(tc);

                ConfirmationDecision decision = confirmationPolicy.confirm(tc);

                switch (decision) {
                    case STOP_SESSION -> {
                        messages.add(new Message("tool", "[停止工具调用,询问用户需求]",
                                null, null, tc.getId()));
                        callback.onAfterTool(tc, "[停止工具调用,询问用户需求]");
                    }
                    case DENY -> {
                        messages.add(new Message("tool", "拒绝本次调用",
                                null, null, tc.getId()));
                        callback.onAfterTool(tc, "拒绝本次调用");
                    }
                    case ALLOW, ALLOW_ALWAYS -> {
                        String result = executeTool(tc);
                        messages.add(new Message("tool", result, null, null, tc.getId()));
                        callback.onAfterTool(tc, result);
                    }
                }
            }

            turn++;
        }

        return messages.get(messages.size() - 1);
    }

    // ============ 工具执行 ============

    private String executeTool(ToolCall tc) {
        try {
            JsonNode args = MAPPER.readTree(tc.getFunction().getArguments());
            for (Tool tool : tools) {
                if (tool.name().equals(tc.getFunction().getName())) {
                    return tool.execute(args);
                }
            }
            return "未找到工具: " + tc.getFunction().getName();
        } catch (Exception e) {
            return "工具执行出错: " + e.getMessage();
        }
    }
}
