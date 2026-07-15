package me.maxt.api;

/**
 * 流式 delta 事件处理器，在 chatStream 中实时回调。
 */
@FunctionalInterface
public interface DeltaHandler {
    void onDelta(DeltaEvent event) throws Exception;
}
