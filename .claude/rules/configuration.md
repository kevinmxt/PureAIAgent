# 配置常量

所有配置集中在 `SimpleAIChat.java:30-35`。

| 常量 | 值 | 说明 |
|------|-----|------|
| `API_URL` | `https://api.deepseek.com/chat/completions` | 接口地址 |
| `API_KEY` | `System.getenv("OPENAI_API_KEY")` | 从环境变量读取 |
| `MODEL_NAME` | `deepseek-v4-flash` | 模型名称 |
| `SYSTEM_PROMPT` | 友好中文助手提示词 | 系统提示词 |
| `STREAM` | `false` | 是否流式输出，`true`=流式，`false`=非流式 |
| `MAPPER` | `ObjectMapper` | Jackson 全局单例，供 `executeTool()` 解析工具参数 |

关键实例字段（`SimpleAIChat.java:37-39`）：

| 字段 | 类型 | 用途 |
|------|------|------|
| `apiClient` | `ChatApiClient` | API 通信接口，构造时注入 |
| `messages` | `List<Message>` | 完整对话历史，每次请求全部发送 |
| `tools` | `List<Tool>` | 注册的工具列表，默认含 `ShellTool` |

## 编码

- `run.bat` 通过 `chcp 65001` 设置控制台为 UTF-8，`-Dfile.encoding=UTF-8` 设置 JVM 编码
- `main()` 使用 JLine `LineReader`（UTF-8 编码）读取输入，解决 Windows 下 Java `System.in`/`Console` 无法正确处理 UTF-8 控制台输入的问题
