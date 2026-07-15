# 构建与运行

本项目没有 Maven Wrapper，需要系统已安装 Maven (`mvn`)。

| 操作 | 命令 |
|------|------|
| 编译 | `mvn compile` |
| 运行 | `mvn exec:java -Dexec.mainClass="me.maxt.SimpleAIChat"` |
| 测试 | `mvn test` |

运行前需设置环境变量 `OPENAI_API_KEY`。
