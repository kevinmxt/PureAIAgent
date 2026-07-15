# 代码地图与架构

## 文件结构

```
src/main/java/me/maxt/
├── SimpleAIChat.java              ← 主入口 (348行)
├── model/
│   ├── Message.java               ← 消息模型
│   └── ToolCall.java              ← 工具调用模型
└── tool/
    ├── Tool.java                  ← 工具接口 (扩展点)
    └── ShellTool.java             ← 唯一的工具实现
```

## 代码地图

```
┌─────────────────────────────────────────────────────────────┐
│                      SimpleAIChat                           │
│                       (主入口 :29)                           │
├─────────────────────────────────────────────────────────────┤
│ 配置常量 (:33-42)                                            │
│   API_URL, API_KEY, MODEL_NAME, SYSTEM_PROMPT, STREAM       │
├─────────────────────────────────────────────────────────────┤
│ 全局状态 (:39-42)                                            │
│   MESSAGES: List<Message>     ← 完整对话历史,每次全量发送     │
│   RESPONSES: List<String>     ← 原始响应,debug用             │
│   TOOLS: List<Tool>           ← 已注册工具列表                │
│   MAPPER: ObjectMapper        ← Jackson 单例                │
├─────────────────────────────────────────────────────────────┤
│ main() (:46)                                                 │
│   └─ 主循环: 读取输入 → STREAM? streamChat : commonResponse  │
├─────────────────────────────────────────────────────────────┤
│ ◆ commonResponse(HttpClient, userInput) (:95)                │
│   非流式对话 + 工具调用循环                                    │
│   ┌─────────────────────────────────────────────┐           │
│   │ 1. user msg → sendChatRequest()             │           │
│   │ 2. assistant msg → 检查 toolCalls            │           │
│   │ 3. 有工具调用 → executeTool() → tool msg     │           │
│   │    → 回到步骤1 (循环)                         │           │
│   │ 4. 无工具调用 → 输出思考 + 回答 → 结束       │           │
│   └─────────────────────────────────────────────┘           │
├─────────────────────────────────────────────────────────────┤
│ ◆ streamChat(HttpClient, userMessage) (:123)                 │
│   流式对话 (手动SSE解析,无工具调用处理)                        │
│   ┌─────────────────────────────────────────────┐           │
│   │ 1. HTTP请求(stream=true) → InputStream      │           │
│   │ 2. 逐行读 SSE: "data: {json}"               │           │
│   │ 3. parseDelta() 提取 delta.content /        │           │
│   │    delta.reasoning_content                  │           │
│   │ 4. 分阶段实时输出                             │           │
│   └─────────────────────────────────────────────┘           │
├─────────────────────────────────────────────────────────────┤
│ ◆ buildRequestBody(isStream) (:196)                          │
│   构建完整请求体,含system提示词+全部MESSAGES+tools定义        │
├─────────────────────────────────────────────────────────────┤
│ ◆ sendChatRequest(client, isStream) (:266)                   │
│   → parseAssistantMessage() / parseError()                   │
├─────────────────────────────────────────────────────────────┤
│ ◆ parseAssistantMessage(responseBody) (:288)                  │
│   解析 choices[0].message → Message 对象                     │
│   (提取 content, reasoning_content, tool_calls)              │
├─────────────────────────────────────────────────────────────┤
│ ◆ parseDelta(SSE data) (:313)                                │
│   流式增量解析 → DeltaResult {content, reasoningContent}     │
├─────────────────────────────────────────────────────────────┤
│ ◆ executeTool(ToolCall) (:250)                               │
│   按名称匹配 TOOLS → tool.execute(args)                      │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────┐         ┌──────────────────┐              │
│  │ <<interface>> │         │ Message (:5)     │              │
│  │ Tool          │         │ ──────────────── │              │
│  │ (:5)          │         │ + role           │              │
│  ├──────────────┤         │ + content         │              │
│  │ name():String │         │ + reasoningContent│              │
│  │ desc():String │         │ + toolCalls       │              │
│  │ params()      │         │ + toolCallId      │              │
│  │   :JsonNode   │         └────────┬─────────┘              │
│  │ execute(args) │                  │                        │
│  │   :String     │         ┌────────┴─────────┐              │
│  └──────┬───────┘         │ ToolCall (:8)     │              │
│         │                 │ ────────────────  │              │
│  ┌──────┴────────┐        │ + id              │              │
│  │ ShellTool (:8)│───────▶│ + type            │              │
│  │────────────── │ 引用   │ + function        │              │
│  │ name→run_shell│        │   └ FunctionCall  │              │
│  │ _command      │        │     + name        │              │
│  │ execute→cmd   │        │     + arguments   │              │
│  │ /c <command>  │        └──────────────────┘              │
│  └───────────────┘                                          │
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
  │            sendChatRequest()  → HTTP POST → DeepSeek API
  │               │                              │
  │               ▼                              ▼
  │            parseAssistantMessage()  ←─ 响应 JSON
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
  └─[流式]──▶ streamChat()  ← 无工具调用,手动SSE逐行解析
                 │
                 ▼
              parseDelta() × N → 实时输出
```

## 扩展指南

### 添加新工具
实现 `Tool` 接口 (`tool/Tool.java:5-18`) 的 4 个方法，然后在 `SimpleAIChat.java:42` 的 `TOOLS` 列表中注册即可。参考 `ShellTool.java`。

```
Tool 接口方法:
  name()        → 工具名称 (模型按名称选择)
  description() → 用途描述 (模型按描述判断何时调用)
  parameters()  → 参数 JSON Schema
  execute(JsonNode arguments) → 执行逻辑,返回文本
```

### 切换模型
修改 `SimpleAIChat.java:35` 的 `MODEL_NAME` 和 `:33` 的 `API_URL`。
