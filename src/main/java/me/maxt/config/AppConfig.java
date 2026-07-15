package me.maxt.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 应用配置，从 config.properties 加载（外部文件 &gt; 类路径 &gt; 默认值三级回退）。
 */
public class AppConfig {

    // ============ 默认值 ============

    private static final String DEFAULT_API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL_NAME = "deepseek-v4-flash";
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个友好、有帮助的AI助手。请用简洁清晰的中文回答问题。";
    private static final boolean DEFAULT_STREAM = false;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 1000;

    // ============ 字段 ============

    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final String systemPrompt;
    private final boolean stream;
    private final double temperature;
    private final int maxTokens;

    private AppConfig(String apiUrl, String apiKey, String modelName,
                      String systemPrompt, boolean stream,
                      double temperature, int maxTokens) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.systemPrompt = systemPrompt;
        this.stream = stream;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    // ============ 静态工厂 ============

    public static AppConfig load() {
        // 1) 外部文件: JAR 所在目录下的 config.properties
        Path jarDir = getJarDirectory();
        if (jarDir != null) {
            Path external = jarDir.resolve("config.properties");
            if (Files.exists(external)) {
                return loadFromPath(external);
            }
        }

        // 2) 类路径资源: JAR 内置的 /config.properties
        try (InputStream is = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (is != null) {
                return loadFromStream(is);
            }
        } catch (IOException e) {
            // 静默忽略
        }

        // 3) 默认值
        return new AppConfig(DEFAULT_API_URL, resolveApiKey(null),
                DEFAULT_MODEL_NAME, DEFAULT_SYSTEM_PROMPT,
                DEFAULT_STREAM, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
    }

    // ============ 加载实现 ============

    private static AppConfig loadFromPath(Path path) {
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            return new AppConfig(DEFAULT_API_URL, resolveApiKey(null),
                    DEFAULT_MODEL_NAME, DEFAULT_SYSTEM_PROMPT,
                    DEFAULT_STREAM, DEFAULT_TEMPERATURE, DEFAULT_MAX_TOKENS);
        }
        return buildFromProperties(props);
    }

    private static AppConfig loadFromStream(InputStream is) throws IOException {
        Properties props = new Properties();
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return buildFromProperties(props);
    }

    private static AppConfig buildFromProperties(Properties props) {
        String apiUrl = props.getProperty("api.url", DEFAULT_API_URL);
        String apiKey = resolveApiKey(props.getProperty("api.key", ""));
        String modelName = props.getProperty("model.name", DEFAULT_MODEL_NAME);
        String systemPrompt = props.getProperty("system.prompt", DEFAULT_SYSTEM_PROMPT);
        boolean stream = Boolean.parseBoolean(props.getProperty("stream", String.valueOf(DEFAULT_STREAM)));
        double temperature = parseDouble(props.getProperty("model.temperature"), DEFAULT_TEMPERATURE);
        int maxTokens = parseInt(props.getProperty("model.max_tokens"), DEFAULT_MAX_TOKENS);

        return new AppConfig(apiUrl, apiKey, modelName, systemPrompt,
                stream, temperature, maxTokens);
    }

    // ============ API 密钥解析 ============

    private static String resolveApiKey(String fromConfig) {
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig;
        }
        String env = System.getenv("OPENAI_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return "";
    }

    // ============ JAR 目录探测 ============

    private static Path getJarDirectory() {
        try {
            URI uri = AppConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            Path path = Paths.get(uri);
            if (path.toString().endsWith(".jar")) {
                return path.getParent();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ============ 解析工具 ============

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ============ Getter ============

    public String getApiUrl()          { return apiUrl; }
    public String getApiKey()          { return apiKey; }
    public String getModelName()       { return modelName; }
    public String getSystemPrompt()    { return systemPrompt; }
    public boolean isStream()          { return stream; }
    public double getTemperature()     { return temperature; }
    public int getMaxTokens()          { return maxTokens; }
}
