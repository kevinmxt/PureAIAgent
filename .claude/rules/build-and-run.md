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

`mvn package -DskipTests` 会在 `target/` 下生成：

| 文件 | 说明 |
|------|------|
| `llm-chat.jar` | 可执行 fat JAR（含所有依赖） |
| `config.properties` | 配置文件（UTF-8，可直接编辑） |
| `run.bat` | 启动脚本 |

运行方式：
- **双击 `run.bat`** — 自动检测环境变量和配置文件，JAR 不存在时自动构建
- **命令行**: `java -jar target/llm-chat.jar`

运行前必须配置 API 密钥（二选一）：
- 在 `config.properties` 中取消注释 `api.key` 并填入密钥
- 设置环境变量 `OPENAI_API_KEY`

> 修改 `config.properties` 后无需重新打包，重启程序即可生效。
