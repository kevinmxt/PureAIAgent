package me.maxt.api;

import me.maxt.model.Message;
import me.maxt.model.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeepSeekApiClient 解析测试")
class DeepSeekApiClientTest {

    private final DeepSeekApiClient client = new DeepSeekApiClient(
            "https://test.api/chat", "test-key", "test-model", "测试提示词");

    @Nested
    @DisplayName("parseAssistantMessage")
    class ParseAssistantMessageTests {

        @Test
        @DisplayName("正常解析: 含 content 和 reasoning_content")
        void withReasoning() throws Exception {
            String json = """
                    {"choices":[{"message":{"role":"assistant","content":"答案是42","reasoning_content":"让我想想..."}}]}""";

            Message msg = client.parseAssistantMessage(json);

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

            Message msg = client.parseAssistantMessage(json);

            assertNotNull(msg.getToolCalls());
            assertEquals(1, msg.getToolCalls().size());
            ToolCall tc = msg.getToolCalls().get(0);
            assertEquals("call_abc", tc.getId());
            assertEquals("function", tc.getType());
            assertEquals("run_shell_command", tc.getFunction().getName());
            assertTrue(tc.getFunction().getArguments().contains("dir"));
        }
    }

    @Nested
    @DisplayName("parseError")
    class ParseErrorTests {

        @Test
        @DisplayName("有效错误 JSON: 提取 error.message")
        void validErrorJson() {
            String result = DeepSeekApiClient.parseError("""
                    {"error":{"message":"Invalid API key","type":"auth_error"}}""");
            assertEquals("Invalid API key", result);
        }

        @Test
        @DisplayName("无效 JSON: 返回原始字符串")
        void invalidJson() {
            String result = DeepSeekApiClient.parseError("<html>500 Error</html>");
            assertEquals("<html>500 Error</html>", result);
        }
    }

    @Nested
    @DisplayName("parseDelta")
    class ParseDeltaTests {

        @Test
        @DisplayName("解析 content delta")
        void contentDelta() throws Exception {
            DeltaEvent event = client.parseDelta("""
                    {"choices":[{"delta":{"content":"你好"}}]}""");
            assertEquals("你好", event.content());
            assertNull(event.reasoningContent());
        }

        @Test
        @DisplayName("解析 reasoning_content delta")
        void reasoningDelta() throws Exception {
            DeltaEvent event = client.parseDelta("""
                    {"choices":[{"delta":{"reasoning_content":"我需要计算..."}}]}""");
            assertEquals("我需要计算...", event.reasoningContent());
            assertNull(event.content());
        }
    }
}
