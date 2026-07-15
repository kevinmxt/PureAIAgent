package me.maxt.api;

/**
 * 流式响应的增量事件，替代 SimpleAIChat.DeltaResult。
 */
public record DeltaEvent(String content, String reasoningContent) {
}
