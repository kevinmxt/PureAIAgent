# 配置管理

所有配置由 `me.maxt.config.AppConfig` 统一管理。

## 配置文件

`config.properties` — UTF-8 编码的 Java Properties 文件，位于 JAR 同目录。

## 加载优先级

1. **外部文件**: JAR 所在目录下的 `config.properties`
2. **类路径**: JAR 内置的 `/config.properties`（自动包含）
3. **内置默认值**: `AppConfig` 中以 `private static final` 常量维护

## 可配置项

| 属性键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `api.url` | String | `https://api.deepseek.com/chat/completions` | API 接口地址 |
| `api.key` | String | 见下方 | API 密钥（三重回退） |
| `model.name` | String | `deepseek-v4-flash` | 模型名称 |
| `model.temperature` | double | `0.7` | 温度参数 0.0~2.0 |
| `model.max_tokens` | int | `1000` | 最大输出 token 数 |
| `system.prompt` | String | 友好中文助手提示词 | 系统提示词 |
| `stream` | boolean | `false` | 是否流式输出 |

## API 密钥回退规则

```
config.properties 的 api.key  >  OPENAI_API_KEY 环境变量  >  空字符串
```

三者均未设置时 API 调用将返回 401 错误。

## 运行时修改

用户可直接编辑 JAR 同目录下的 `config.properties`，修改后**无需重新打包**，重启程序即可生效。

## 测试

- 测试代码中可绕过 `AppConfig.load()`，直接使用 `DeepSeekApiClient` 的旧 4 参数构造器
- 集成测试未使用 `AppConfig`，保持自包含
