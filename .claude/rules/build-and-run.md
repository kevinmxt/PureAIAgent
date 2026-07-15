# 构建与运行

本项目没有 Maven Wrapper，需要系统已安装 Maven (`mvn`)。

| 操作 | 命令 |
|------|------|
| 编译 | `mvn compile` |
| 运行（开发模式） | `mvn exec:java -Dexec.mainClass="me.maxt.SimpleAIChat"` |
| 打包 | `mvn clean package -DskipTests` |
| 单元测试 | `mvn test` |
| 全部测试 | `mvn test -Pintegration` |

> 集成测试（`@Tag("integration")`）需要 `OPENAI_API_KEY` 环境变量才能运行。
> 默认 `mvn test` 只跑单元测试，集成测试需用 `-Pintegration` 激活。

### 打包运行

`mvn package -DskipTests` 会在 `target/` 下生成 `llm-chat.jar`（含所有依赖的 fat jar）。

运行方式：
- **双击 `run.bat`** — 自动检测 `OPENAI_API_KEY`，JAR 不存在时自动构建
- **命令行**: `java -jar target/llm-chat.jar`

运行前必须设置环境变量 `OPENAI_API_KEY`。
