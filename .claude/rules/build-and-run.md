# 构建与运行

本项目没有 Maven Wrapper，需要系统已安装 Maven (`mvn`)。

| 操作 | 命令 |
|------|------|
| 编译 | `mvn compile` |
| 运行 | `mvn exec:java -Dexec.mainClass="me.maxt.SimpleAIChat"` |
| 单元测试 | `mvn test` |
| 全部测试 | `mvn test -Pintegration` |

> 集成测试（`@Tag("integration")`）需要 `OPENAI_API_KEY` 环境变量才能运行。
> 默认 `mvn test` 只跑单元测试，集成测试需用 `-Pintegration` 激活。
