# 代码地图与架构

## 文件结构

```
src/main/java/me/maxt/
├── SimpleAIChat.java              ← 主入口
├── api/
│   ├── ChatApiClient.java         ← API通信接口 (测试seam)
│   ├── DeepSeekApiClient.java     ← DeepSeek HTTP实现
│   └── ApiException.java          ← API异常
├── model/
│   ├── Message.java               ← 消息模型
│   └── ToolCall.java              ← 工具调用模型
└── tool/
    ├── Tool.java                  ← 工具接口 (扩展点)
    └── ShellTool.java             ← 唯一的工具实现

src/test/java/me/maxt/
├── SimpleAIChatMockTest.java      ← Mock单元测试 (14用例)
└── SimpleAIChatIntegrationTest.java ← 集成测试 (3用例,@Tag("integration"))
```

## 代码地图

```
┌─────────────────────────────────────────────────────────────┐
│                      SimpleAIChat                           │
│         构造器: SimpleAIChat(ChatApiClient)                  │
├─────────────────────────────────────────────────────────────┤
│ 配置常量 (static)                                            │
│   API_URL, API_KEY, MODEL_NAME, SYSTEM_PROMPT, STREAM       │
├─────────────────────────────────────────────────────────────┤
│ 实例状态                                                     │
│   messages: List<Message>     ← 完整对话历史                 │
│   responses: List<String>     ← 原始响应,debug用             │
│   tools: List<Tool>           ← 已注册工具列表                │
│   apiClient: ChatApiClient    ← 注入的API客户端              │
├─────────────────────────────────────────────────────────────┤
│ main()                                                       │
│   └─ DeepSeekApiClient → SimpleAIChat → 主循环              │
├─────────────────────────────────────────────────────────────┤
│ ◆ commonResponse(userInput)                                  │
│   非流式对话 + 工具调用循环                                    │
│   ┌─────────────────────────────────────────────┐           │
│   │ 1. user msg → sendChatRequest()             │           │
│   │ 2. assistant msg → 检查 toolCalls            │           │
│   │ 3. 有工具调用 → executeTool() → tool msg     │           │
│   │    → 回到步骤1 (循环)                         │           │
│   │ 4. 无工具调用 → 输出思考 + 回答 → 结束       │           │
│   └─────────────────────────────────────────────┘           │
├─────────────────────────────────────────────────────────────┤
│ ◆ streamChat(userMessage)                                    │
│   流式对话 (手动SSE解析,无工具调用处理)                        │
│   ┌─────────────────────────────────────────────┐           │
│   │ 1. apiClient.sendStream(request) → InputStream│         │
│   │ 2. 逐行读 SSE: "data: {json}"               │           │
│   │ 3. parseDelta() 提取 delta.content /        │           │
│   │    delta.reasoning_content                  │           │
│   │ 4. 分阶段实时输出                             │           │
│   └─────────────────────────────────────────────┘           │
├─────────────────────────────────────────────────────────────┤
│ ◆ sendChatRequest(isStream) → apiClient.sendNonStream()      │
│   调用接口, 捕获 ApiException → 转换为 error Message         │
├─────────────────────────────────────────────────────────────┤
│ ◆ buildRequestBody(isStream)                                 │
│   构建完整请求体,含system提示词+全部messages+tools定义        │
├─────────────────────────────────────────────────────────────┤
│ ◆ parseAssistantMessage(responseBody) → Message              │
│   解析 choices[0].message (content/reasoning/tool_calls)     │
├─────────────────────────────────────────────────────────────┤
│ ◆ parseDelta(SSE data) → DeltaResult {content, reasoning}    │
│ ◆ parseError(responseBody) → 提取 error.message              │
│ ◆ executeTool(ToolCall) → 匹配 tools, 执行并返回文本          │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ <<interface>>     │  │ Message          │                 │
│  │ ChatApiClient     │  │ ──────────────── │                 │
│  │ (api/)            │  │ + role           │                 │
│  ├──────────────────┤  │ + content         │                 │
│  │ sendNonStream()   │  │ + reasoningContent│                 │
│  │ sendStream()      │  │ + toolCalls       │                 │
│  └────────┬─────────┘  │ + toolCallId      │                 │
│           │             └────────┬─────────┘                 │
│  ┌────────┴─────────┐           │                            │
│  │ DeepSeekApiClient │  ┌────────┴─────────┐                 │
│  │ (api/)            │  │ ToolCall         │                 │
│  ├──────────────────┤  │ ────────────────  │                 │
│  │ - apiUrl, apiKey  │  │ + id              │                 │
│  │ - httpClient      │  │ + type            │                 │
│  │ buildRequest()    │  │ + function        │                 │
│  └──────────────────┘  │   └ FunctionCall  │                 │
│                         │     + name        │                 │
│  ┌──────────────────┐  │     + arguments   │                 │
│  │ <<interface>>     │  └──────────────────┘                 │
│  │ Tool              │                                       │
│  │ (tool/)           │  ┌──────────────────┐                 │
│  ├──────────────────┤  │ ShellTool        │                 │
│  │ name():String     │  │ (tool/)          │                 │
│  │ description()     │  ├──────────────────┤                 │
│  │ parameters()      │  │ name→run_shell   │                 │
│  │ execute(args)     │  │ _command         │                 │
│  └──────────────────┘  │ execute→cmd /c    │                 │
│                         └──────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
```

## 数据流

```
用户输入
  │
  ▼
SimpleAIChat.main()  ← 主循环,按 STREAM 分流
  │
  ├─[非流式]──▶ commonResponse()  ← 支持 Function Calling 循环
  │               │
  │               ▼
  │            sendChatRequest()  → ChatApiClient.sendNonStream()
  │               │                    │
  │               ▼                    ▼
  │            parseAssistantMessage() ←─ DeepSeekApiClient
  │               │                    (HTTP → DeepSeek API)
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
  └─[流式]──▶ streamChat()  ← 无工具调用,手动SSE逐行解析
                 │
                 ▼
              apiClient.sendStream() → InputStream
                 │
                 ▼
              parseDelta() × N → 实时输出
```

## 测试体系

| 类型 | 文件 | 运行命令 | 说明 |
|------|------|----------|------|
| Mock 单元测试 | `SimpleAIChatMockTest.java` | `mvn test` | 通过 Mock `ChatApiClient` 测试,不联网 |
| 集成测试 | `SimpleAIChatIntegrationTest.java` | `mvn test -Pintegration` | 真实 API,需 `OPENAI_API_KEY` |

测试类**只依赖 `ChatApiClient` 接口**，不依赖具体实现。后续重构代码时，只要接口不变，测试代码无需修改。

## 扩展指南

### 添加新工具
实现 `Tool` 接口 (`tool/Tool.java`) 的 4 个方法，然后在 `SimpleAIChat` 的 tools 列表中注册即可。参考 `ShellTool.java`。

```
Tool 接口方法:
  name()        → 工具名称 (模型按名称选择)
  description() → 用途描述 (模型按描述判断何时调用)
  parameters()  → 参数 JSON Schema
  execute(JsonNode arguments) → 执行逻辑,返回文本
```

### 切换模型/API
修改 `SimpleAIChat` 顶部的 `MODEL_NAME` 和 `API_URL` 常量。

### 替换 API 客户端
实现 `ChatApiClient` 接口 (`api/ChatApiClient.java`)，然后传入 `SimpleAIChat` 构造器即可。
