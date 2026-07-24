package me.maxt.tool.confirmation;

import me.maxt.model.ToolCall;

/**
 * 总是允许工具执行的策略，用于 CI/无头环境或子 agent。
 */
public class AlwaysAllowPolicy implements ToolConfirmationPolicy {

    @Override
    public ConfirmationDecision confirm(ToolCall tc) {
        return ConfirmationDecision.ALLOW;
    }
}
