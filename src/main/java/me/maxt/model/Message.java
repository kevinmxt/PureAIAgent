package me.maxt.model;

import java.util.List;

public class Message {

    private String role;
    private String content;
    private String reasoningContent;
    private List<ToolCall> toolCalls;
    private String toolCallId;

    public Message() {}

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public Message(String role, String content, String reasoningContent,
                   List<ToolCall> toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getReasoningContent() { return reasoningContent; }
    public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Message{role='").append(role).append("'");
        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            sb.append(", reasoning='").append(reasoningContent).append("'");
        }
        sb.append(", content='").append(content).append("'");
        if (toolCalls != null && !toolCalls.isEmpty()) {
            sb.append(", toolCalls=").append(toolCalls);
        }
        sb.append("}");
        return sb.toString();
    }
}
