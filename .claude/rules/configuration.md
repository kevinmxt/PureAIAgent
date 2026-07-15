# 配置常量

所有配置集中在 `SimpleAIChat.java:33-42`。

| 常量 | 值 | 说明 |
|------|-----|------|
| `API_URL` | `https://api.deepseek.com/chat/completions` | 接口地址 |
| `API_KEY` | `System.getenv("OPENAI_API_KEY")` | 从环境变量读取 |
| `MODEL_NAME` | `deepseek-v4-flash` | 模型名称 |
| `SYSTEM_PROMPT` | 友好中文助手提示词 | 系统提示词 |
| `STREAM` | `false` | 是否流式输出，`true`=流式，`false`=非流式 |

关键全局状态（`SimpleAIChat.java:39-42`）：

| 字段 | 类型 | 用途 |
|------|------|------|
| `MESSAGES` | `List<Message>` | 完整对话历史，每次请求全部发送 |
| `RESPONSES` | `List<String>` | 原始 API 响应，供 debug 命令查看 |
| `TOOLS` | `List<Tool>` | 注册的工具列表，当前仅 `ShellTool` |
| `MAPPER` | `ObjectMapper` | Jackson 全局单例 |
