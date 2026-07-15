package me.maxt.api;

import java.io.InputStream;

/**
 * API 通信抽象，作为测试的核心 seam。
 * 后续重构时只要接口不变，测试代码无需修改。
 */
public interface ChatApiClient {

    /** 非流式请求，返回完整 JSON 响应体 */
    String sendNonStream(String requestBody) throws Exception;

    /** 流式请求，返回 SSE 事件流 */
    InputStream sendStream(String requestBody) throws Exception;
}
