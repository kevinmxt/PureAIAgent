package me.maxt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.api.ApiException;
import me.maxt.api.ChatApiClient;
import me.maxt.model.Message;
import me.maxt.model.ToolCall;
import me.maxt.tool.ShellTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpleAIChat Mock 单元测试")
class SimpleAIChatMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ChatApiClient apiClient;

    private SimpleAIChat chat;

    @BeforeEach
    void setUp() {
        chat = new SimpleAIChat(apiClient);
    }

    // ================================================================
    // 1. 基本对话
    // ================================================================

    @Test
    @DisplayName("基本对话: 输入 'hi', 返回正常回复")
    void basicChat() throws Exception {
        when(apiClient.sendNonStream(anyString())).thenReturn("""
                {"choices":[{"message":{"role":"assistant","content":"你好！有什么可以帮助你的？"}}]}""");

        chat.commonResponse("hi");

        List<Message> msgs = chat.messages;
        assertEquals(2, msgs.size());
        assertEquals("user", msgs.get(0).getRole());
        assertEquals("hi", msgs.get(0).getContent());
        assertEquals("assistant", msgs.get(1).getRole());
        assertEquals("你好！有什么可以帮助你的？", msgs.get(1).getContent());
    }

    // ================================================================
    // 2. 基于上下文的对话
    // ================================================================

    @Test
    @DisplayName("上下文对话: 第一轮自我介绍, 第二轮问'我是谁'")
    void contextChat() throws Exception {
        when(apiClient.sendNonStream(anyString()))
                .thenReturn("""
                        {"choices":[{"message":{"role":"assistant","content":"你好张三！很高兴认识你。"}}]}""")
                .thenReturn("""
                        {"choices":[{"message":{"role":"assistant","content":"你是张三。"}}]}""");

        chat.commonResponse("hi,我是张三");
        chat.commonResponse("我是谁");

        List<Message> msgs = chat.messages;
        assertEquals(4, msgs.size());
        assertEquals("hi,我是张三", msgs.get(0).getContent());
        assertEquals("你好张三！很高兴认识你。", msgs.get(1).getContent());
        assertEquals("我是谁", msgs.get(2).getContent());
        assertEquals("你是张三。", msgs.get(3).getContent());
    }

    // ================================================================
    // 3. 工具调用
    // ================================================================

    @Test
    @DisplayName("工具调用: 模型请求执行命令, 工具返回结果, 模型基于结果回答")
    void toolCalling() throws Exception {
        when(apiClient.sendNonStream(anyString()))
                .thenReturn("""
                        {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"run_shell_command","arguments":"{\\"command\\":\\"echo hello\\"}"}}]}}]}""")
                .thenReturn("""
                        {"choices":[{"message":{"role":"assistant","content":"命令执行成功，输出: hello"}}]}""");

        chat.commonResponse("执行 echo hello 命令");

        List<Message> msgs = chat.messages;
        assertEquals(4, msgs.size());
        assertEquals("user", msgs.get(0).getRole());
        assertEquals("assistant", msgs.get(1).getRole());
        assertNotNull(msgs.get(1).getToolCalls());
        assertEquals(1, msgs.get(1).getToolCalls().size());
        assertEquals("run_shell_command", msgs.get(1).getToolCalls().get(0).getFunction().getName());
        assertEquals("tool", msgs.get(2).getRole());
        assertNotNull(msgs.get(2).getToolCallId());
        assertEquals("assistant", msgs.get(3).getRole());
        assertTrue(msgs.get(3).getContent().contains("hello"));
    }

    // ================================================================
    // 4. parseAssistantMessage
    // ================================================================

    @Nested
    @DisplayName("响应解析: parseAssistantMessage")
    class ParseAssistantMessageTests {

        @Test
        @DisplayName("正常解析: 含 content 和 reasoning_content")
        void withReasoning() throws Exception {
            String json = """
                    {"choices":[{"message":{"role":"assistant","content":"答案是42","reasoning_content":"让我想想..."}}]}""";

            Message msg = chat.parseAssistantMessage(json);

            assertEquals("assistant", msg.getRole());
            assertEquals("答案是42", msg.getContent());
            assertEquals("让我想想...", msg.getReasoningContent());
            assertNull(msg.getToolCalls());
        }

        @Test
        @DisplayName("含 tool_calls: 反序列化为 ToolCall 列表")
        void withToolCalls() throws Exception {
            String json = """
                    {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_abc","type":"function","function":{"name":"run_shell_command","arguments":"{\\"command\\":\\"dir\\"}"}}]}}]}""";

            Message msg = chat.parseAssistantMessage(json);

            assertNotNull(msg.getToolCalls());
            assertEquals(1, msg.getToolCalls().size());
            ToolCall tc = msg.getToolCalls().get(0);
            assertEquals("call_abc", tc.getId());
            assertEquals("function", tc.getType());
            assertEquals("run_shell_command", tc.getFunction().getName());
            assertTrue(tc.getFunction().getArguments().contains("dir"));
        }
    }

    // ================================================================
    // 5. parseError
    // ================================================================

    @Nested
    @DisplayName("错误解析: parseError")
    class ParseErrorTests {

        @Test
        @DisplayName("有效错误 JSON: 提取 error.message")
        void validErrorJson() {
            String body = """
                    {"error":{"message":"Invalid API key","type":"auth_error"}}""";

            String result = chat.parseError(body);

            assertEquals("Invalid API key", result);
        }

        @Test
        @DisplayName("无效 JSON: 返回原始字符串")
        void invalidJson() {
            String body = "<html>500 Internal Server Error</html>";

            String result = chat.parseError(body);

            assertEquals("<html>500 Internal Server Error</html>", result);
        }
    }

    // ================================================================
    // 6. parseDelta
    // ================================================================

    @Nested
    @DisplayName("流式增量解析: parseDelta")
    class ParseDeltaTests {

        @Test
        @DisplayName("解析 content delta")
        void contentDelta() throws Exception {
            String data = """
                    {"choices":[{"delta":{"content":"你好"}}]}""";

            SimpleAIChat.DeltaResult r = chat.parseDelta(data);

            assertEquals("你好", r.content);
            assertNull(r.reasoningContent);
        }

        @Test
        @DisplayName("解析 reasoning_content delta")
        void reasoningDelta() throws Exception {
            String data = """
                    {"choices":[{"delta":{"reasoning_content":"我需要计算..."}}]}""";

            SimpleAIChat.DeltaResult r = chat.parseDelta(data);

            assertEquals("我需要计算...", r.reasoningContent);
            assertNull(r.content);
        }
    }

    // ================================================================
    // 7. ShellTool
    // ================================================================

    @Nested
    @DisplayName("ShellTool 工具执行")
    class ShellToolTests {

        private final ShellTool shellTool = new ShellTool();

        @Test
        @DisplayName("正常命令: echo hello")
        void echoCommand() throws Exception {
            ObjectNode args = MAPPER.createObjectNode();
            args.put("command", "echo hello");

            String result = shellTool.execute(args);

            assertTrue(result.contains("hello"));
        }

        @Test
        @DisplayName("异常命令: 不存在的命令")
        void invalidCommand() throws Exception {
            ObjectNode args = MAPPER.createObjectNode();
            args.put("command", "nonexistent_command_xyz");

            String result = shellTool.execute(args);

            assertNotNull(result);
            assertFalse(result.isBlank());
        }
    }

    // ================================================================
    // 8. buildRequestBody
    // ================================================================

    @Nested
    @DisplayName("请求体构建: buildRequestBody")
    class BuildRequestBodyTests {

        @Test
        @DisplayName("非流式请求体包含 tools 数组")
        void nonStreamIncludesTools() throws Exception {
            String body = chat.buildRequestBody(false);

            JsonNode root = MAPPER.readTree(body);
            assertTrue(root.has("tools"));
            assertTrue(root.get("tools").isArray());
            assertEquals(1, root.get("tools").size());
            assertEquals("run_shell_command",
                    root.get("tools").get(0).get("function").get("name").asText());
            assertFalse(root.get("stream").asBoolean());
        }

        @Test
        @DisplayName("流式请求体不含 tools, stream=true")
        void streamExcludesTools() throws Exception {
            String body = chat.buildRequestBody(true);

            JsonNode root = MAPPER.readTree(body);
            assertFalse(root.has("tools"));
            assertTrue(root.get("stream").asBoolean());
        }
    }

    // ================================================================
    // 9. API 错误处理
    // ================================================================

    @Test
    @DisplayName("sendChatRequest: 非 200 响应返回错误 Message")
    void apiErrorReturnsErrorText() throws Exception {
        when(apiClient.sendNonStream(anyString()))
                .thenThrow(new ApiException(401, """
                        {"error":{"message":"Invalid API key"}}"""));

        chat.messages.add(new Message("user", "hi"));

        Message msg = chat.sendChatRequest(false);

        assertEquals("assistant", msg.getRole());
        assertTrue(msg.getContent().contains("401"));
        assertTrue(msg.getContent().contains("Invalid API key"));
    }
}
