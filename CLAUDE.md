# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建和运行

- 编译: `mvn compile`
- 运行: `mvn exec:java -Dexec.mainClass="me.maxt.SimpleAIChat"`
- 测试: `mvn test`

本项目没有 Maven Wrapper，需要系统已安装 Maven (`mvn`)。

## 架构概述

一个极简的 AI 命令行对话程序，使用 JDK 21 原生 HTTP 客户端调用 OpenAI 兼容接口（默认 DeepSeek），JSON 处理使用 Jackson。

- **`me.maxt.SimpleAIChat`** — 主程序入口。支持两种对话模式：普通响应 (`commonResponse`) 和流式响应 (`streamChat`)，通过 `STREAM` 常量切换。每次请求都会将完整对话历史 (`MESSAGES`) 作为上下文发送。API Key 从环境变量 `OPENAI_API_KEY` 读取。请求体构建和响应解析均使用 Jackson 的 tree model (`ObjectMapper`/`JsonNode`)。
- **`me.maxt.model.Message`** — 消息模型，含 `role`、`content`、`reasoningContent`、`toolCalls`、`toolCallId` 字段，适配 DeepSeek/OpenAI 消息格式。每轮对话存两条 Message（user + assistant）。
- **`me.maxt.model.ToolCall`** — 工具调用模型，含 `id`、`type`、`function`（内部类，含 `name`、`arguments`），支持函数调用场景。

### 配置常量（在 SimpleAIChat 顶部）
- `API_URL` — 接口地址，默认 `https://api.deepseek.com/chat/completions`
- `API_KEY` — 从环境变量 `OPENAI_API_KEY` 获取
- `MODEL_NAME` — 模型名称，默认 `deepseek-v4-flash`
- `SYSTEM_PROMPT` — 系统提示词
- `STREAM` — 是否使用流式输出

### 输出格式
- 正式回答以 `AI: ` 为前缀
- 思考内容（`reasoning_content`）以 `[思考过程] ` 为前缀，与正式回答用 `---` 分隔
- 流式模式下，思考和正式内容分阶段实时输出

## 关键注意事项

- JDK 版本要求 >= 21
- Jackson 版本 2.18.2（`jackson-databind`）
