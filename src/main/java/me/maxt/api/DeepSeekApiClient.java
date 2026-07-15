package me.maxt.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.model.Message;
import me.maxt.model.ToolCall;
import me.maxt.tool.Tool;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek API 客户端实现，封装所有 DeepSeek/OpenAI 消息格式细节。
 */
public class DeepSeekApiClient implements ChatApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final String systemPrompt;
    private final HttpClient httpClient;
    private final List<String> rawResponses = new ArrayList<>();

    public DeepSeekApiClient(String apiUrl, String apiKey, String modelName, String systemPrompt) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.systemPrompt = systemPrompt;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // ============ ChatApiClient 实现 ============

    @Override
    public Message chat(List<Message> history, List<Tool> tools) throws Exception {
        String requestBody = buildRequestBody(history, tools, false);
        String responseBody = sendNonStream(requestBody);
        return parseAssistantMessage(responseBody);
    }

    @Override
    public Message chatStream(List<Message> history, DeltaHandler handler) throws Exception {
        String requestBody = buildRequestBody(history, List.of(), true);
        InputStream bodyStream = sendStream(requestBody);

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    DeltaEvent event = parseDelta(data);
                    handler.onDelta(event);

                    if (event.content() != null) {
                        contentBuilder.append(event.content());
                    }
                    if (event.reasoningContent() != null) {
                        reasoningBuilder.append(event.reasoningContent());
                    }
                }
            }
        }

        String fullReasoning = reasoningBuilder.length() > 0 ? reasoningBuilder.toString() : null;
        return new Message("assistant", contentBuilder.toString(), fullReasoning, null, null);
    }

    @Override
    public List<String> getRawResponses() {
        return rawResponses;
    }

    // ============ HTTP ============

    private String sendNonStream(String requestBody) throws Exception {
        HttpRequest request = buildRequest(requestBody);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        }
        throw new ApiException(response.statusCode(), response.body());
    }

    private InputStream sendStream(String requestBody) throws Exception {
        HttpRequest request = buildRequest(requestBody);
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            return response.body();
        }
        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        throw new ApiException(response.statusCode(), errorBody);
    }

    private HttpRequest buildRequest(String requestBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
    }

    // ============ 请求体构建 ============

    private String buildRequestBody(List<Message> history, List<Tool> tools, boolean stream) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", 0.7);
        root.put("max_tokens", 1000);
        root.put("stream", stream);

        ArrayNode messagesArray = root.putArray("messages");

        ObjectNode sysNode = messagesArray.addObject();
        sysNode.put("role", "system");
        sysNode.put("content", systemPrompt);

        for (Message msg : history) {
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

        if (!stream && !tools.isEmpty()) {
            ArrayNode toolsArray = root.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                ObjectNode funcNode = toolNode.putObject("function");
                funcNode.put("name", tool.name());
                funcNode.put("description", tool.description());
                funcNode.set("parameters", tool.parameters());
            }
        }

        return MAPPER.writeValueAsString(root);
    }

    // ============ 响应解析 ============

    Message parseAssistantMessage(String responseBody) throws Exception {
        rawResponses.add(responseBody);
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

    DeltaEvent parseDelta(String data) throws Exception {
        rawResponses.add(data);
        JsonNode chunk = MAPPER.readTree(data);
        JsonNode choice = chunk.get("choices").get(0);
        JsonNode delta = choice.get("delta");

        String content = delta.has("content") && !delta.get("content").isNull()
                ? delta.get("content").asText() : null;
        String reasoning = delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()
                ? delta.get("reasoning_content").asText() : null;

        return new DeltaEvent(content, reasoning);
    }

    public static String parseError(String responseBody) {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            if (root.has("error") && root.get("error").has("message")) {
                return root.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }
}
