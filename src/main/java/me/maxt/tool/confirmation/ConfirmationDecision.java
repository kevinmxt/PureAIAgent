package me.maxt.tool.confirmation;

/**
 * 工具确认决策 — 每个工具调用执行前产生一个决策。
 */
public enum ConfirmationDecision {

    /** 本次允许执行 */
    ALLOW,

    /** 本次允许，且记住此操作 — 会话内后续相同参数自动 ALLOW */
    ALLOW_ALWAYS,

    /** 本次拒绝执行 */
    DENY,

    /** 停止所有工具调用，让 AI 询问用户需求 */
    STOP_SESSION
}
