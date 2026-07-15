package me.maxt;

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
import static org.mockito.ArgumentMatchers.anyList;
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
        when(apiClient.chat(anyList(), anyList()))
                .thenReturn(new Message("assistant", "你好！有什么可以帮助你的？"));

        chat.commonResponse("hi");

        List<Message> msgs = chat.getMessages();
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
        when(apiClient.chat(anyList(), anyList()))
                .thenReturn(new Message("assistant", "你好张三！很高兴认识你。"))
                .thenReturn(new Message("assistant", "你是张三。"));

        chat.commonResponse("hi,我是张三");
        chat.commonResponse("我是谁");

        List<Message> msgs = chat.getMessages();
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
        Message toolCallMsg = new Message("assistant", null, null,
                List.of(new ToolCall("call_1", "function", "run_shell_command",
                        "{\"command\":\"echo hello\"}")), null);

        when(apiClient.chat(anyList(), anyList()))
                .thenReturn(toolCallMsg)
                .thenReturn(new Message("assistant", "命令执行成功，输出: hello"));

        chat.commonResponse("执行 echo hello 命令");

        List<Message> msgs = chat.getMessages();
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
    // 4. API 错误处理
    // ================================================================

    @Test
    @DisplayName("API 错误: ApiException 被捕获并打印, 不崩溃")
    void apiErrorHandling() throws Exception {
        when(apiClient.chat(anyList(), anyList()))
                .thenThrow(new ApiException(401, """
                        {"error":{"message":"Invalid API key"}}"""));

        // 不应抛异常, 静默处理
        chat.commonResponse("hi");

        // messages 中只有 user 消息, assistant 消息未添加
        List<Message> msgs = chat.getMessages();
        assertEquals(1, msgs.size());
        assertEquals("user", msgs.get(0).getRole());
    }

    // ================================================================
    // 5. ShellTool 工具执行
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
}
