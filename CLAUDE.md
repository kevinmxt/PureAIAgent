# CLAUDE.md

一个极简的 AI 命令行对话程序，JDK 21 原生 HTTP 客户端 + Jackson JSON，调用 DeepSeek API，支持 Function Calling 工具调用（Shell 命令执行、Excel 操作）。

## 快速开始

- 编译: `mvn compile`
- 运行: `mvn exec:java -Dexec.mainClass="me.maxt.SimpleAIChat"`
- 测试: `mvn test`

## 详细规则

- [构建与运行](.claude/rules/build-and-run.md)
- [代码地图与架构](.claude/rules/architecture.md)
- [配置常量](.claude/rules/configuration.md)
- [输出格式规范](.claude/rules/output-format.md)
- [技术栈与依赖](.claude/rules/dependencies.md)
