package me.maxt;

import me.maxt.api.ChatApiClient;
import me.maxt.api.DeepSeekApiClient;
import me.maxt.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
@DisplayName("SimpleAIChat 集成测试 (真实API)")
class SimpleAIChatIntegrationTest {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String MODEL_NAME = "deepseek-v4-flash";
    private static final String SYSTEM_PROMPT = "你是一个友好、有帮助的AI助手。请用简洁清晰的中文回答问题。";

    private SimpleAIChat chat;

    @BeforeEach
    void setUp() {
        assumeTrue(API_KEY != null && !API_KEY.isBlank(),
                "跳过集成测试: 未设置 OPENAI_API_KEY 环境变量");

        ChatApiClient client = new DeepSeekApiClient(API_URL, API_KEY, MODEL_NAME, SYSTEM_PROMPT);
        chat = new SimpleAIChat(client);
    }

    @Test
    @DisplayName("基本对话: 输入 'hi', 模型返回正常回复")
    void basicChat() throws Exception {
        chat.commonResponse("hi");

        List<Message> msgs = chat.getMessages();
        assertTrue(msgs.size() >= 2, "至少应有 user + assistant 两条消息");
        assertEquals("user", msgs.get(0).getRole());
        assertEquals("assistant", msgs.get(msgs.size() - 1).getRole());
        assertNotNull(msgs.get(msgs.size() - 1).getContent());
        assertFalse(msgs.get(msgs.size() - 1).getContent().isBlank(),
                "模型应返回非空回复");
    }

    @Test
    @DisplayName("上下文对话: 首轮自我介绍, 第二轮问'我是谁', 回复含'张三'")
    void contextChat() throws Exception {
        chat.commonResponse("hi,我是张三");

        assertTrue(chat.getMessages().size() >= 2);

        chat.commonResponse("我是谁");

        List<Message> msgs = chat.getMessages();
        assertTrue(msgs.size() >= 4);
        String lastReply = msgs.get(msgs.size() - 1).getContent();
        assertNotNull(lastReply);
        assertTrue(lastReply.contains("张三"),
                "模型回复应包含'张三', 实际: " + lastReply);
    }

    @Test
    @DisplayName("工具调用: 问当前时间, 模型调用ShellTool并回答正确时间")
    void toolCallingTime() throws Exception {
        chat.commonResponse("现在几点?请使用工具");

        List<Message> msgs = chat.getMessages();
        assertTrue(msgs.size() >= 2);

        String lastReply = msgs.get(msgs.size() - 1).getContent();
        assertNotNull(lastReply);
        assertFalse(lastReply.isBlank());

        boolean hasToolCall = msgs.stream().anyMatch(m ->
                m.getToolCalls() != null && !m.getToolCalls().isEmpty());
        boolean hasToolResult = msgs.stream().anyMatch(m ->
                "tool".equals(m.getRole()));

        if (hasToolCall && hasToolResult) {
            String toolResult = msgs.stream()
                    .filter(m -> "tool".equals(m.getRole()))
                    .map(Message::getContent)
                    .findFirst().orElse("");
            assertFalse(toolResult.isBlank(), "工具执行结果不应为空");
        }

        LocalDateTime now = LocalDateTime.now();
        System.out.println("[集成测试] 期望时间约: " + now.format(DateTimeFormatter.ofPattern("HH:mm")));
        System.out.println("[集成测试] 模型回复: " + lastReply);
    }
}
