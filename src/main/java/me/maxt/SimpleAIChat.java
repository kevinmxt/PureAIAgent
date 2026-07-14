package me.maxt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.model.Message;
import me.maxt.model.ToolCall;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 * 使用JDK21原生HTTP客户端 + Jackson JSON解析
 * 支持OpenAI兼容接口（可接入各种大模型API）
 */
public class SimpleAIChat {

    // ============ 配置区域 ============

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String MODEL_NAME = "deepseek-v4-flash";
    private static final String SYSTEM_PROMPT = "你是一个友好、有帮助的AI助手。请用简洁清晰的中文回答问题。";
    private static final boolean STREAM = false;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<Message> MESSAGES = new ArrayList<>();
    private static final List<String> RESPONSES = new ArrayList<>();

    // ============ 主程序 ============

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("  简单AI对话程序 (JDK21原生实现)");
        System.out.println("  输入 '退出' 或 'exit' 结束对话");
        System.out.println("  输入 '对话历史' 或 'history' 查看历史对话");
        System.out.println("  输入 'debug' 查看调试信息");
        System.out.println("=================================\n");

        try (HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()) {

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\n你: ");
                    String userInput = scanner.nextLine().trim();

                    if (userInput.isEmpty()) {
                        continue;
                    }
                    if ("退出".equals(userInput) || "exit".equalsIgnoreCase(userInput)) {
                        System.out.println("再见！期待下次对话~");
                        break;
                    }
                    if ("对话历史".equals(userInput) || "history".equalsIgnoreCase(userInput)) {
                        System.out.println(MESSAGES);
                        continue;
                    }
                    if ("debug".equals(userInput)) {
                        System.out.println(RESPONSES);
                        continue;
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

    // ============ 非流式对话 ============

    private static void commonResponse(HttpClient httpClient, String userInput) throws Exception {
        MESSAGES.add(new Message("user", userInput));
        Message assistantMsg = sendChatRequest(httpClient);
        MESSAGES.add(assistantMsg);

        if (assistantMsg.getReasoningContent() != null && !assistantMsg.getReasoningContent().isEmpty()) {
            System.out.println("[思考过程] " + assistantMsg.getReasoningContent());
            System.out.println("---");
        }
        System.out.println("AI: " + (assistantMsg.getContent() != null ? assistantMsg.getContent() : ""));
    }

    // ============ 流式对话 ============

    private static void streamChat(HttpClient client, String userMessage) throws Exception {
        MESSAGES.add(new Message("user", userMessage));

        String requestBody = buildRequestBody(true);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("API错误 (状态码: " + response.statusCode() + "): " + parseError(errorBody));
            return;
        }

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        boolean inReasoning = false;
        boolean startedContent = false;

        System.out.print("AI: ");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    DeltaResult delta = parseDelta(data);

                    if (delta.reasoningContent != null && !delta.reasoningContent.isEmpty()) {
                        if (!inReasoning) {
                            System.out.println();
                            System.out.print("[思考过程] ");
                            inReasoning = true;
                        }
                        System.out.print(delta.reasoningContent);
                        reasoningBuilder.append(delta.reasoningContent);
                    }

                    if (delta.content != null && !delta.content.isEmpty()) {
                        if (inReasoning) {
                            System.out.println();
                            System.out.println("---");
                            System.out.print("AI: ");
                            inReasoning = false;
                        }
                        if (!startedContent) {
                            startedContent = true;
                        }
                        System.out.print(delta.content);
                        contentBuilder.append(delta.content);
                    }
                }
            }
        }
        System.out.println();

        String fullReasoning = reasoningBuilder.length() > 0 ? reasoningBuilder.toString() : null;
        MESSAGES.add(new Message("assistant", contentBuilder.toString(), fullReasoning, null, null));
    }

    // ============ 请求构建 ============

    private static String buildRequestBody(boolean isStream) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", MODEL_NAME);
        root.put("temperature", 0.7);
        root.put("max_tokens", 1000);
        root.put("stream", isStream);

        ArrayNode messagesArray = root.putArray("messages");

        ObjectNode sysNode = messagesArray.addObject();
        sysNode.put("role", "system");
        sysNode.put("content", SYSTEM_PROMPT);

        for (Message msg : MESSAGES) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");

            if (msg.getReasoningContent() != null) {
                msgNode.put("reasoning_content", msg.getReasoningContent());
            }
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                ArrayNode tcArray = msgNode.putArray("tool_calls");
                for (ToolCall tc : msg.getToolCalls()) {
                    ObjectNode tcNode = tcArray.addObject();
                    tcNode.put("id", tc.getId());
                    tcNode.put("type", tc.getType());
                    ObjectNode funcNode = tcNode.putObject("function");
                    funcNode.put("name", tc.getFunction().getName());
                    funcNode.put("arguments", tc.getFunction().getArguments());
                }
            }
            if (msg.getToolCallId() != null) {
                msgNode.put("tool_call_id", msg.getToolCallId());
            }
        }

        return MAPPER.writeValueAsString(root);
    }

    // ============ HTTP 请求 ============

    private static Message sendChatRequest(HttpClient client) throws Exception {
        String requestBody = buildRequestBody(STREAM);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return parseAssistantMessage(response.body());
        } else {
            String errorMsg = parseError(response.body());
            return new Message("assistant", "API错误 (状态码: " + response.statusCode() + "): " + errorMsg);
        }
    }

    // ============ 响应解析 ============

    private static Message parseAssistantMessage(String responseBody) throws Exception {
        RESPONSES.add(responseBody);
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode message = root.get("choices").get(0).get("message");

        String content = "";
        if (message.has("content") && !message.get("content").isNull()) {
            content = message.get("content").asText();
        }

        String reasoningContent = null;
        if (message.has("reasoning_content") && !message.get("reasoning_content").isNull()) {
            reasoningContent = message.get("reasoning_content").asText();
        }

        List<ToolCall> toolCalls = null;
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            toolCalls = MAPPER.readValue(
                    message.get("tool_calls").traverse(),
                    new TypeReference<List<ToolCall>>() {});
        }

        return new Message("assistant", content, reasoningContent, toolCalls, null);
    }

    private static DeltaResult parseDelta(String data) throws Exception {
        RESPONSES.add(data);
        JsonNode chunk = MAPPER.readTree(data);
        JsonNode choice = chunk.get("choices").get(0);
        JsonNode delta = choice.get("delta");

        DeltaResult result = new DeltaResult();

        if (delta.has("content") && !delta.get("content").isNull()) {
            result.content = delta.get("content").asText();
        }
        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
            result.reasoningContent = delta.get("reasoning_content").asText();
        }

        return result;
    }

    private static String parseError(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            if (root.has("error") && root.get("error").has("message")) {
                return root.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }

    // ============ 内部类 ============

    private static class DeltaResult {
        String content;
        String reasoningContent;
    }
}
