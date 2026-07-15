package me.maxt.api;

import me.maxt.model.Message;
import me.maxt.tool.Tool;

import java.util.List;

/**
 * AI 对话 API 客户端抽象。
 * 封装请求构建和响应解析，对调用方屏蔽模型格式差异。
 */
public interface ChatApiClient {

    /**
     * 非流式对话：发送完整对话历史和可用工具列表，返回助手的完整回复。
     */
    Message chat(List<Message> history, List<Tool> tools) throws Exception;

    /**
     * 流式对话：通过 {@link DeltaHandler} 实时推送增量内容，返回助手的完整回复。
     */
    Message chatStream(List<Message> history, DeltaHandler handler) throws Exception;

    /**
     * 获取历次请求的原始 JSON 响应，供调试使用。
     */
    List<String> getRawResponses();
}
