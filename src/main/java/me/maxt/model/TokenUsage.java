package me.maxt.model;

public class TokenUsage {

    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private int promptCacheHitTokens;
    private int promptCacheMissTokens;
    private int reasoningTokens;

    public TokenUsage() {}

    public TokenUsage(int promptTokens, int completionTokens, int totalTokens,
                      int promptCacheHitTokens, int promptCacheMissTokens, int reasoningTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.promptCacheHitTokens = promptCacheHitTokens;
        this.promptCacheMissTokens = promptCacheMissTokens;
        this.reasoningTokens = reasoningTokens;
    }

    public void add(TokenUsage other) {
        this.promptTokens += other.promptTokens;
        this.completionTokens += other.completionTokens;
        this.totalTokens += other.totalTokens;
        this.promptCacheHitTokens += other.promptCacheHitTokens;
        this.promptCacheMissTokens += other.promptCacheMissTokens;
        this.reasoningTokens += other.reasoningTokens;
    }

    // ============ getters & setters ============

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public int getPromptCacheHitTokens() { return promptCacheHitTokens; }
    public void setPromptCacheHitTokens(int promptCacheHitTokens) { this.promptCacheHitTokens = promptCacheHitTokens; }

    public int getPromptCacheMissTokens() { return promptCacheMissTokens; }
    public void setPromptCacheMissTokens(int promptCacheMissTokens) { this.promptCacheMissTokens = promptCacheMissTokens; }

    public int getReasoningTokens() { return reasoningTokens; }
    public void setReasoningTokens(int reasoningTokens) { this.reasoningTokens = reasoningTokens; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Token 用量:\n");
        sb.append("  Prompt: ").append(promptTokens).append(" tokens\n");
        sb.append("    ├─ 命中缓存: ").append(promptCacheHitTokens).append("\n");
        sb.append("    └─ 未命中缓存: ").append(promptCacheMissTokens).append("\n");
        sb.append("  Completion: ").append(completionTokens).append(" tokens\n");
        sb.append("    └─ 推理: ").append(reasoningTokens).append("\n");
        sb.append("  Total: ").append(totalTokens).append(" tokens");
        return sb.toString();
    }
}
