package me.maxt.tool.confirmation;

import me.maxt.model.ToolCall;

/**
 * 工具确认策略 — 在工具执行前决定是否允许执行。
 * 实现此接口可以将确认逻辑（交互式控制台 / CI 自动允许 / Web UI）从编排器中分离。
 */
public interface ToolConfirmationPolicy {

    /**
     * 对传入的工具调用产生确认决策。
     * 实现应自行管理"本次会话已允许"的缓存状态。
     */
    ConfirmationDecision confirm(ToolCall tc);
}
