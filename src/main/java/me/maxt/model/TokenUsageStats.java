package me.maxt.model;

/**
 * Token 用量统计，维护当前会话和总量两个累加器。
 * 实例化后注入到 DeepSeekApiClient，解析 API 响应时自动累加。
 */
public class TokenUsageStats {

    private final TokenUsage currentSessionUsage = new TokenUsage();
    private final TokenUsage totalUsage = new TokenUsage();

    public void accumulateSession(TokenUsage usage) {
        currentSessionUsage.add(usage);
    }

    public void accumulateTotal() {
        totalUsage.add(currentSessionUsage);
    }

    public void clearSession() {
        currentSessionUsage.setPromptTokens(0);
        currentSessionUsage.setCompletionTokens(0);
        currentSessionUsage.setTotalTokens(0);
        currentSessionUsage.setPromptCacheHitTokens(0);
        currentSessionUsage.setPromptCacheMissTokens(0);
        currentSessionUsage.setReasoningTokens(0);
    }

    public TokenUsage getSessionUsage() {
        return currentSessionUsage;
    }

    public TokenUsage getTotalUsage() {
        return totalUsage;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("┌─ 当前会话用量 ─────────────────\n");
        sb.append(currentSessionUsage.toString().replaceAll("(?m)^", "│ "));
        sb.append("\n├─ 累计总用量 ───────────────────\n");
        sb.append(totalUsage.toString().replaceAll("(?m)^", "│ "));
        return sb.toString();
    }
}
