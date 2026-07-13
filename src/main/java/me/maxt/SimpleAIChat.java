package me.maxt;

import me.maxt.model.Dialog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 最简单的AI对话程序
 * 使用JDK21原生HTTP客户端，不依赖任何第三方框架
 * 支持OpenAI兼容接口（可接入各种大模型API）
 */
public class SimpleAIChat {

    // ============ 配置区域 ============
    // 请替换为你自己的API配置

    // API地址（支持OpenAI、DeepSeek、智谱等兼容接口）
    private static final String API_URL = "https://api.deepseek.com/chat/completions";

    // API密钥（请替换为你自己的密钥）
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");

    // 模型名称
    private static final String MODEL_NAME = "deepseek-v4-flash";

    // 系统提示词（定义AI的角色和行为）
    private static final String SYSTEM_PROMPT = "你是一个友好、有帮助的AI助手。请用简洁清晰的中文回答问题。";

    private static final boolean STREAM = true;

    /**
     * 对话历史
     */
    private static final List<Dialog> DIALOGS = new ArrayList<>();

    // ============ 主程序 ============

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  简单AI对话程序 (JDK21原生实现)");
        System.out.println("  输入 '退出' 或 'exit' 结束对话");
        System.out.println("=================================\n");

        // 创建HTTP客户端（使用虚拟线程，提升性能）
        try (HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()) {


            // 读取用户输入
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\n你: ");
                    String userInput = scanner.nextLine().trim();

                    // 检查退出条件
                    if (userInput.isEmpty()) {
                        continue;
                    }
                    if ("退出".equals(userInput) || "exit".equalsIgnoreCase(userInput)) {
                        System.out.println("再见！期待下次对话~");
                        break;
                    }

                    if (STREAM) {
                        streamChat(httpClient, userInput);
                    } else {
                        commonResponse(httpClient, userInput);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("程序运行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void commonResponse(HttpClient httpClient, String userInput) throws Exception {
        // 发送请求并获取回复
        String aiResponse = chat(httpClient, userInput);

        DIALOGS.add(new Dialog(userInput, aiResponse));
        // 显示AI回复
        System.out.println("AI: " + aiResponse);
    }

    /**
     * 发送对话请求到AI接口
     *
     * @param client HTTP客户端
     * @param userMessage 用户新消息
     * @return AI的回复文本
     */
    private static String chat(HttpClient client, String userMessage) throws Exception {
        // 构建符合OpenAI格式的请求体
        String requestBody = buildRequestBody(userMessage);

        // 创建HTTP请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        // 发送请求（使用同步方式，简单直接）
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 检查响应状态
        if (response.statusCode() == 200) {
            return extractContent(response.body());
        } else {
            return "API错误 (状态码: " + response.statusCode() + "): " + response.body();
        }
    }

    /**
     * 构建请求体JSON字符串
     * 使用简单字符串拼接（避免引入JSON库）
     *
     * @param userMessage 用户消息
     * @return JSON格式的请求体
     */
    private static String buildRequestBody(String userMessage) {

        // 对用户消息进行简单的JSON转义
        String escapedMessage = escapeJson(userMessage);
        String escapedSystem = escapeJson(SYSTEM_PROMPT);

        String messages = buildMessages(escapedMessage);

        // 手动构建JSON（最简单的方式，无需第三方库）
        return """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "%s"},
                    %s
                ],
                "temperature": 0.7,
                "max_tokens": 1000,
                "stream": %b
            }
            """.formatted(MODEL_NAME, escapedSystem, messages, STREAM);
    }

    private static String buildMessages(String userMessage) {
        StringBuilder sb = new StringBuilder();
        for (Dialog dialog : DIALOGS) {
            sb.append("{\"role\": \"user\", \"content\": \"").append(dialog.getUser()).append("\"},");
            sb.append("{\"role\": \"assistant\", \"content\": \"").append(dialog.getAi()).append("\"},");
        }
        sb.append("{\"role\": \"user\", \"content\": \"").append(userMessage).append("\"}");
        return  sb.toString();
    }

    /**
     * 从API响应中提取AI回复内容
     * 使用简单的字符串截取（不使用JSON解析库）
     *
     * @param responseBody API返回的JSON字符串
     * @return 提取的内容文本
     */
    private static String extractContent(String responseBody) {
        try {
            // 查找content字段的位置
            String contentTag = "\"content\":\"";
            int contentStart = responseBody.indexOf(contentTag);
            if (contentStart == -1) {
                return "无法解析的响应格式";
            }

            // 从content字段开始位置截取
            contentStart += contentTag.length();
            int contentEnd = contentStart;

            // 处理转义字符，找到真正地结束引号
            boolean escaped = false;
            while (contentEnd < responseBody.length()) {
                char c = responseBody.charAt(contentEnd);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                }
                contentEnd++;
            }

            // 提取内容并还原转义字符
            String content = responseBody.substring(contentStart, contentEnd);
            return unescapeJson(content);

        } catch (Exception e) {
            return "解析响应出错: " + e.getMessage();
        }
    }

    /**
     * 简单的JSON字符串转义
     * 处理可能导致JSON格式错误的特殊字符
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 还原JSON转义的字符串
     *
     * @param text 转义后的文本
     * @return 还原的文本
     */
    private static String unescapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }


    /**
     * 流式发送对话请求并实时输出回复
     *
     * @param client HTTP客户端
     * @param userMessage 用户消息
     */
    private static void streamChat(HttpClient client, String userMessage) throws Exception {
        String requestBody = buildRequestBody(userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        // 使用InputStream读取流式响应
        HttpResponse<java.io.InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            // 非200状态码，读取错误信息
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("API错误 (状态码: " + response.statusCode() + "): " + errorBody);
            return;
        }

        System.out.print("AI: ");
        StringBuilder history = new StringBuilder();
        // 逐行读取SSE流
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // SSE格式：data: {...}
                if (line.startsWith("data: ")) {
                    String data = line.substring(6); // 去掉"data: "前缀

                    // 检查是否结束标记
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    // 提取content并实时输出
                    String content = extractStreamContent(data);
                    if (content != null && !content.isEmpty()) {
                        System.out.print(content);
                        history.append(content);
                    }
                }
            }
        }
        DIALOGS.add(new Dialog(userMessage, history.toString()));
    }

    /**
     * 从流式响应的data中提取content
     * SSE数据格式：{"choices":[{"delta":{"content":"文本内容"}}]}
     */
    private static String extractStreamContent(String data) {
        try {
            // 查找delta中的content字段
            String contentTag = "\"content\":\"";
            int contentStart = data.indexOf(contentTag);
            if (contentStart == -1) {
                return ""; // 有些chunk可能没有content（如角色定义）
            }

            contentStart += contentTag.length();
            int contentEnd = contentStart;

            // 处理转义字符，找到真正的结束引号
            boolean escaped = false;
            while (contentEnd < data.length()) {
                char c = data.charAt(contentEnd);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                }
                contentEnd++;
            }

            String content = data.substring(contentStart, contentEnd);
            return unescapeJson(content);

        } catch (Exception e) {
            return "";
        }
    }

}
