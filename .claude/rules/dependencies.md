# 技术栈与依赖

- **JDK**: >= 21（`pom.xml` 中 `<maven.compiler.release>21</maven.compiler.release>`）
- **Jackson**: `jackson-databind` 2.18.2 — 使用 tree model（`ObjectNode`/`ArrayNode`/`JsonNode`），不使用 data binding 注解（除 `ToolCall` 的反序列化外）
- **JLine**: `jline` 3.26.1 — Windows 控制台 UTF-8 输入，解决 `chcp 65001` 下 Java `System.in`/`Console` 无法正确读取中文的问题
- **HTTP**: `java.net.http.HttpClient`（JDK 11+ 内置）
- **JSON**: `Jackson ObjectMapper` 手动构建请求体和解析响应
