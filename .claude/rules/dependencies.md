# 技术栈与依赖

- **JDK**: >= 21（`pom.xml` 中 `<maven.compiler.release>21</maven.compiler.release>`）
- **Jackson**: `jackson-databind` 2.18.2 — 使用 tree model（`ObjectNode`/`ArrayNode`/`JsonNode`），不使用 data binding 注解（除 `ToolCall` 的反序列化外）
- **JLine**: `jline` 3.26.1 — Windows 控制台 UTF-8 输入，解决 `chcp 65001` 下 Java `System.in`/`Console` 无法正确读取中文的问题
- **Apache POI**: `poi-ooxml` 5.3.0 — Excel .xlsx 文件读写、公式求值、图表生成（XDDF API）
- **HTTP**: `java.net.http.HttpClient`（JDK 11+ 内置）
- **JSON**: `Jackson ObjectMapper` 手动构建请求体和解析响应
- **配置**: `java.util.Properties`（JDK 内置）读取外部配置文件
