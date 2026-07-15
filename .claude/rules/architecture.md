# 代码地图与架构

## 文件结构

```
src/main/java/me/maxt/
├── SimpleAIChat.java              ← 主入口,对话流程编排
├── api/
│   ├── ChatApiClient.java         ← API通信接口 (语义化对话方法)
│   ├── DeepSeekApiClient.java     ← DeepSeek实现 (封装所有模型格式)
│   ├── ApiException.java          ← API异常
│   ├── DeltaEvent.java            ← 流式增量事件 record
│   └── DeltaHandler.java          ← 流式回调函数式接口
├── model/
│   ├── Message.java               ← 消息模型
│   └── ToolCall.java              ← 工具调用模型
└── tool/
    ├── Tool.java                  ← 工具接口 (扩展点)
    └── ShellTool.java             ← 唯一的工具实现

src/test/java/me/maxt/
├── SimpleAIChatMockTest.java      ← Mock单元测试 (6用例)
├── SimpleAIChatIntegrationTest.java ← 集成测试 (3用例)
└── api/
    └── DeepSeekApiClientTest.java ← 解析逻辑测试 (6用例)
```

## 代码地图

```
┌────────────────────────────────────────────────────────────┐
│                    SimpleAIChat                            │
│             构造器: (ChatApiClient [, List<Tool>])          │
├────────────────────────────────────────────────────────────┤
│ 职责: 对话流程编排                                          │
│   - 用户输入循环 (main)                                     │
│   - 工具调用循环 (commonResponse)                           │
│   - 流式输出格式化 (streamChat)                             │
│   - 工具执行分发 (executeTool)                              │
│                                                            │
│ 不包含: 请求体构建、JSON解析、SSE解析                        │
├────────────────────────────────────────────────────────────┤
│ main()                                                      │
│   └─ TerminalBuilder(UTF-8) → LineReader                    │
│      └─ new DeepSeekApiClient(url,key,model,prompt)          │
│         → new SimpleAIChat(client) → 主循环                  │
├────────────────────────────────────────────────────────────┤
│ ◆ commonResponse(userInput)                                 │
│   ┌──────────────────────────────────────────────┐         │
│   │ 1. user msg → apiClient.chat(msgs, tools)    │         │
│   │ 2. 返回 Message → 检查 toolCalls              │         │
│   │ 3. 有工具调用 → executeTool() → tool msg      │         │
│   │    → 回到步骤1 (循环)                          │         │
│   │ 4. 无工具调用 → 输出思考+回答 → 结束          │         │
│   └──────────────────────────────────────────────┘         │
├────────────────────────────────────────────────────────────┤
│ ◆ streamChat(userMessage)                                   │
│   ┌──────────────────────────────────────────────┐         │
│   │ 1. user msg → apiClient.chatStream(msgs, h)  │         │
│   │ 2. DeltaHandler 实时输出 content/reasoning   │         │
│   │ 3. 返回完整 Message → 记录到 messages         │         │
│   └──────────────────────────────────────────────┘         │
├────────────────────────────────────────────────────────────┤
│ ◆ executeTool(ToolCall) → 工具分发                          │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│ <<interface>> ChatApiClient (api/)                         │
├────────────────────────────────────────────────────────────┤
│ + chat(List<Message>, List<Tool>) → Message                │
│ + chatStream(List<Message>, DeltaHandler) → Message        │
│ + getRawResponses() → List<String>                         │
└────────────┬───────────────────────────────────────────────┘
             │
    ┌────────┴──────────────┐
    │ DeepSeekApiClient     │  ← 封装所有 DeepSeek/OpenAI 格式
    │ (api/)                │
    ├───────────────────────┤
    │ - apiUrl, apiKey      │  构造: (url, key, model, prompt)
    │ - modelName           │
    │ - systemPrompt        │
    │ - httpClient          │
    ├───────────────────────┤
    │ chat()                │  → buildRequestBody → HTTP → parse
    │ chatStream()          │  → buildRequestBody → HTTP → SSE逐行解析
    │ getRawResponses()     │
    ├───────────────────────┤
    │ buildRequestBody()    │  (private)
    │ parseAssistantMessage()│  (package-private, 供测试)
    │ parseDelta()          │  (package-private, 供测试)
    │ parseError()          │  (public static)
    └───────────────────────┘

┌──────────────────────────┐
│ <<interface>> Tool       │    DeltaEvent (record)
│ (tool/)                  │    ├── content: String
├──────────────────────────┤    └── reasoningContent: String
│ name(): String           │
│ description(): String    │    DeltaHandler (@FunctionalInterface)
│ parameters(): JsonNode   │    └── onDelta(DeltaEvent)
│ execute(JsonNode):String │
└──────────┬───────────────┘
    ┌──────┴────────┐
    │ ShellTool     │
    │ name→run_shell│
    │ _command      │
    └───────────────┘
```

## 数据流

```
用户输入
  │
  ▼
SimpleAIChat.main()
  │
  ├─[非流式]──▶ commonResponse()
  │               │
  │               ▼
  │            apiClient.chat(history, tools)
  │               │
  │               ▼
  │            DeepSeekApiClient
  │            ├─ buildRequestBody (含 system prompt + messages + tools)
  │            ├─ HTTP POST → DeepSeek API
  │            └─ parseAssistantMessage → Message
  │               │
  │               ▼
  │            Message (assistant)
  │               │
  │        ┌──────┴──────┐
  │        │ has toolCalls?
  │        ▼              ▼
  │    executeTool()   输出思考+回答
  │        │
  │        ▼
  │    Message (tool) → 回到 commonResponse 循环
  │
  └─[流式]──▶ streamChat()
                 │
                 ▼
              apiClient.chatStream(history, handler)
                 │
                 ▼
              DeepSeekApiClient
              ├─ buildRequestBody (stream=true)
              ├─ HTTP POST(stream) → SSE InputStream
              └─ parseDelta × N → handler.onDelta(event)
                 │
                 ▼
              DeltaHandler 实时打印
                 │
                 ▼
              返回完整 Message
```

## 测试体系

| 类型 | 文件 | 运行命令 | 说明 |
|------|------|----------|------|
| Mock 单元测试 | `SimpleAIChatMockTest.java` | `mvn test` | Mock ChatApiClient, 测试对话流程 |
| 解析测试 | `DeepSeekApiClientTest.java` | `mvn test` | 测试响应解析准确性 |
| 集成测试 | `SimpleAIChatIntegrationTest.java` | `mvn test -Pintegration` | 真实 API, 需 `OPENAI_API_KEY` |

## 扩展指南

### 添加新工具
实现 `Tool` 接口 (`tool/Tool.java`)，然后传入 `SimpleAIChat` 的构造器或设置 tools 列表。参考 `ShellTool.java`。

### 切换模型/API
- **同协议模型（OpenAI 兼容）**: 修改 `SimpleAIChat.main()` 中 `DeepSeekApiClient` 的 url/model 参数
- **不同协议模型**: 实现 `ChatApiClient` 接口，封装该模型的请求/响应格式，传入 `SimpleAIChat` 构造器
