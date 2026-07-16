# 代码地图与架构

## 文件结构

```
src/main/java/me/maxt/
├── SimpleAIChat.java              ← 主入口,对话流程编排
├── config/
│   └── AppConfig.java             ← 配置加载（Properties 文件,三级回退）
├── api/
│   ├── ChatApiClient.java         ← API通信接口 (语义化对话方法)
│   ├── DeepSeekApiClient.java     ← DeepSeek实现 (封装所有模型格式)
│   ├── ApiException.java          ← API异常
│   ├── DeltaEvent.java            ← 流式增量事件 record
│   └── DeltaHandler.java          ← 流式回调函数式接口
├── model/
│   ├── Message.java               ← 消息模型
│   ├── ToolCall.java              ← 工具调用模型
│   ├── TokenUsage.java            ← 单次用量数据
│   └── TokenUsageStats.java       ← 用量统计（会话+总量累加）
└── tool/
    ├── Tool.java                  ← 工具接口 (扩展点)
    ├── ShellTool.java             ← Shell 命令执行工具
    └── excel/
        ├── ExcelTool.java         ← Excel 工具总协调器
        └── ExcelOperationExecutor.java ← POI 操作引擎

src/test/java/me/maxt/
├── SimpleAIChatMockTest.java      ← Mock单元测试 (6用例)
├── SimpleAIChatIntegrationTest.java ← 集成测试 (3用例)
├── api/
│   └── DeepSeekApiClientTest.java ← 解析逻辑测试 (6用例)
└── tool/excel/
    └── ExcelOperationExecutorTest.java ← Excel 操作引擎测试 (18用例)
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
│   - 工具调用确认 (callTool) → 分发执行 (executeTool)          │
│   - 特殊命令: 退出/history/debug/tokens                     │
│                                                            │
│ 不包含: 请求体构建、JSON解析、SSE解析                        │
├────────────────────────────────────────────────────────────┤
│ main()                                                      │
│   └─ AppConfig.load() → TerminalBuilder(UTF-8) → LineReader│
│      └─ new DeepSeekApiClient(config)                       │
│         → new SimpleAIChat(client) → 主循环                  │
├────────────────────────────────────────────────────────────┤
│ ◆ commonResponse(userInput)                                 │
│   ┌──────────────────────────────────────────────┐         │
│   │ 1. user msg → apiClient.chat(msgs, tools)    │         │
│   │ 2. 返回 Message → 检查 toolCalls              │         │
│   │ 3. 有工具调用 → callTool() → executeTool()              │         │
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
│ ◆ callTool(ToolCall) → 用户确认(1/2/3) → executeTool        │
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
    │ - apiUrl, apiKey      │  构造: 4参数(旧) / 6参数 / AppConfig
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
    │ parseUsage()          │  (package-private static)
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
    ┌──────┴──────────────────┐
    │                         │
┌───┴───────────┐  ┌──────────┴──────────────────────┐
│ ShellTool     │  │ ExcelTool (tool/excel/)         │
│ name→run_shell│  │ name→excel_tool                 │
│ _command      │  ├────────────────────────────────┤
└───────────────┘  │ ◆ execute()                     │
                   │   1. 解析参数+文件安全检查        │
                   │   2. 子模型LLM翻译NL→JSON操作序列 │
                   │   3. ExcelOperationExecutor执行   │
                   │   4. 返回结构化摘要               │
                   ├────────────────────────────────┤
                   │ ExcelOperationExecutor          │
                   │ ├─ read → Markdown表格+merge信息 │
                   │ ├─ write → Markdown→Excel       │
                   │ ├─ formula → 写入公式+即时求值    │
                   │ └─ chart → 柱状图/折线图/饼图    │
                   └────────────────────────────────┘
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
  │    callTool()   (用户确认)
  │        │
  │        ▼
  │    executeTool()
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
| Excel 引擎测试 | `ExcelOperationExecutorTest.java` | `mvn test` | 测试 POI 操作：坐标解析、Markdown解析、read/write/formula/chart |
| 集成测试 | `SimpleAIChatIntegrationTest.java` | `mvn test -Pintegration` | 真实 API, 需 `OPENAI_API_KEY` |

## 扩展指南

### 添加新工具
实现 `Tool` 接口 (`tool/Tool.java`)，然后传入 `SimpleAIChat` 的构造器或设置 tools 列表。参考 `ShellTool.java`（简单工具）和 `ExcelTool.java`（使用子模型 LLM 翻译自然语言指令的复杂工具）。

### ExcelTool 架构要点
- **子模型 LLM**: ExcelTool 内部创建独立的 `DeepSeekApiClient` 实例，通过 `config.properties` 中 `excel.sub_model.*` 系列配置项控制（默认复用主模型 URL 和 Key）
- **NL→JSON 翻译**: 子模型将自然语言指令翻译为 JSON 操作序列（四种类型: read/write/formula/chart），ExcelOperationExecutor 逐条执行
- **文件安全**: 默认仅允许操作 `excel.work.dir` 目录下的文件，可在配置中关闭限制
- **关键约束**: 同一个文件只能被 `new XSSFWorkbook()` 打开一次，`buildFileContext` 不再单独打开文件
- **POI 双写陷阱**: `XSSFWorkbook.write(OutputStream)` 后调用 `close()` 会触发 `OPCPackage.saveImpl()` 内部重新保存到原始文件，覆盖已写入内容并抛出 `EOFException: Unexpected end of ZLIB input stream`。正确做法: write 到 `ByteArrayOutputStream` → close workbook → 写字节到文件

### 切换模型/API
- **同协议模型（OpenAI 兼容）**: 修改 `config.properties` 中的 `api.url` 和 `model.name`，无需重新打包
- **不同协议模型**: 实现 `ChatApiClient` 接口，封装该模型的请求/响应格式，传入 `SimpleAIChat` 构造器
