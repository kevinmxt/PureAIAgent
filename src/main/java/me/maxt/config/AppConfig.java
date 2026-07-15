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

    private static final String DEFAULT_EXCEL_WORK_DIR = "./excel_files";
    private static final String DEFAULT_EXCEL_SUB_MODEL_NAME = "deepseek-chat";
    private static final double DEFAULT_EXCEL_SUB_MODEL_TEMPERATURE = 0.0;
    private static final int DEFAULT_EXCEL_MAX_ROWS = 100;
    private static final boolean DEFAULT_EXCEL_RESTRICT_PATH = true;
    static final String DEFAULT_EXCEL_SUB_MODEL_PROMPT =
            "你是一个Excel操作翻译助手。将自然语言描述的Excel操作需求翻译为JSON操作序列。\n" +
            "\n" +
            "## 可用操作类型\n" +
            "\n" +
            "### read - 读取数据\n" +
            "{\"type\":\"read\",\"sheet\":\"Sheet1\",\"range\":\"A1:D10\",\"header\":true}\n" +
            "- sheet: Sheet名称（必填）\n" +
            "- range: Excel范围，如A1:D10（必填）\n" +
            "- header: 是否将首行作为表头（可选，默认true）\n" +
            "\n" +
            "### write - 写入数据（创建新Sheet或覆盖已有区域）\n" +
            "{\"type\":\"write\",\"sheet\":\"Sheet1\",\"range\":\"A1\",\"data\":\"| 列1 | 列2 |\\n|---|---|\\n| val1 | val2 |\",\"merge\":[]}\n" +
            "- range: 写入起始单元格（必填）\n" +
            "- data: Markdown格式的表格数据字符串（必填）。必须包含表头行和分隔行(|---|---|)\n" +
            "- merge: 合并单元格设置（可选），格式[{\"row\":0,\"col\":0,\"rowspan\":2,\"colspan\":1}]\n" +
            "  row/col是相对于数据区域的位置（0=数据第一行/列），rowspan/colspan是合并行数/列数\n" +
            "\n" +
            "### formula - 公式计算\n" +
            "{\"type\":\"formula\",\"sheet\":\"Sheet1\",\"range\":\"D11\",\"formula\":\"=SUM(D2:D10)\"}\n" +
            "- range: 公式写入的单元格（必填）\n" +
            "- formula: Excel公式，以=开头（必填）\n" +
            "\n" +
            "### chart - 生成图表\n" +
            "{\"type\":\"chart\",\"sheet\":\"Sheet1\",\"chart_type\":\"bar\",\"data_range\":\"A1:C4\",\"position\":\"K1\",\"title\":\"销售统计\"}\n" +
            "- chart_type: 图表类型，可选 bar(柱状图)、line(折线图)、pie(饼图)（必填）\n" +
            "- data_range: 图表数据源区域，首行为类别，首列为系列名，其余为数值（必填）\n" +
            "- position: 图表左上角放置位置（必填）\n" +
            "- title: 图表标题（可选）\n" +
            "\n" +
            "## 输出规则\n" +
            "1. 返回一个JSON数组[]，可以包含多个操作步骤\n" +
            "2. 只返回JSON数组，不要任何解释文字，不要用```json```包裹\n" +
            "3. 列号从A开始，行号从1开始\n" +
            "4. 列号超过Z用两个字母表示：AA, AB, ..., AZ, BA, ...\n" +
            "5. 如果用户描述不明确，做出合理假设\n" +
            "6. 写入新数据前确保Sheet已存在（先在之前步骤中创建）";

    // ============ 字段 ============

    private final String apiUrl;
    private final String apiKey;
    private final String modelName;
    private final String systemPrompt;
    private final boolean stream;
    private final double temperature;
    private final int maxTokens;

    private final String excelWorkDir;
    private final String excelSubModelUrl;
    private final String excelSubModelKey;
    private final String excelSubModelName;
    private final String excelSubModelPrompt;
    private final double excelSubModelTemperature;
    private final int excelMaxRows;
    private final boolean excelRestrictPath;

    // ============ 构造器 ============

    /** @deprecated 委托给 Builder，保留以兼容旧调用者 */
    private AppConfig(String apiUrl, String apiKey, String modelName,
                      String systemPrompt, boolean stream,
                      double temperature, int maxTokens) {
        this(new Builder()
                .apiUrl(apiUrl).apiKey(apiKey).modelName(modelName)
                .systemPrompt(systemPrompt).stream(stream)
                .temperature(temperature).maxTokens(maxTokens));
    }

    private AppConfig(Builder builder) {
        this.apiUrl = builder.apiUrl;
        this.apiKey = builder.apiKey;
        this.modelName = builder.modelName;
        this.systemPrompt = builder.systemPrompt;
        this.stream = builder.stream;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;

        this.excelWorkDir = builder.excelWorkDir;
        this.excelSubModelUrl = builder.excelSubModelUrl;
        this.excelSubModelKey = builder.excelSubModelKey;
        this.excelSubModelName = builder.excelSubModelName;
        this.excelSubModelPrompt = builder.excelSubModelPrompt;
        this.excelSubModelTemperature = builder.excelSubModelTemperature;
        this.excelMaxRows = builder.excelMaxRows;
        this.excelRestrictPath = builder.excelRestrictPath;
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
        return buildWithDefaults(null);
    }

    // ============ 加载实现 ============

    private static AppConfig loadFromPath(Path path) {
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            return buildWithDefaults(null);
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
        return new Builder()
                .apiUrl(props.getProperty("api.url", DEFAULT_API_URL))
                .apiKey(resolveApiKey(props.getProperty("api.key", "")))
                .modelName(props.getProperty("model.name", DEFAULT_MODEL_NAME))
                .systemPrompt(props.getProperty("system.prompt", DEFAULT_SYSTEM_PROMPT))
                .stream(Boolean.parseBoolean(props.getProperty("stream", String.valueOf(DEFAULT_STREAM))))
                .temperature(parseDouble(props.getProperty("model.temperature"), DEFAULT_TEMPERATURE))
                .maxTokens(parseInt(props.getProperty("model.max_tokens"), DEFAULT_MAX_TOKENS))
                .excelWorkDir(props.getProperty("excel.work.dir", DEFAULT_EXCEL_WORK_DIR))
                .excelSubModelUrl(props.getProperty("excel.sub_model.url", null))
                .excelSubModelKey(props.getProperty("excel.sub_model.key", null))
                .excelSubModelName(props.getProperty("excel.sub_model.name", DEFAULT_EXCEL_SUB_MODEL_NAME))
                .excelSubModelPrompt(props.getProperty("excel.sub_model.prompt", DEFAULT_EXCEL_SUB_MODEL_PROMPT))
                .excelSubModelTemperature(parseDouble(props.getProperty("excel.sub_model.temperature"), DEFAULT_EXCEL_SUB_MODEL_TEMPERATURE))
                .excelMaxRows(parseInt(props.getProperty("excel.max_rows"), DEFAULT_EXCEL_MAX_ROWS))
                .excelRestrictPath(Boolean.parseBoolean(props.getProperty("excel.restrict_path", "true")))
                .build();
    }

    private static AppConfig buildWithDefaults(String apiKey) {
        return new Builder()
                .apiKey(resolveApiKey(apiKey))
                .build();
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

    public String getExcelWorkDir()              { return excelWorkDir; }
    public String getExcelSubModelUrl()          { return excelSubModelUrl; }
    public String getExcelSubModelKey()          { return excelSubModelKey; }
    public String getExcelSubModelName()         { return excelSubModelName; }
    public String getExcelSubModelPrompt()       { return excelSubModelPrompt; }
    public double getExcelSubModelTemperature()  { return excelSubModelTemperature; }
    public int getExcelMaxRows()                 { return excelMaxRows; }
    public boolean isExcelRestrictPath()         { return excelRestrictPath; }

    // ============ Builder ============

    public static class Builder {
        String apiUrl = DEFAULT_API_URL;
        String apiKey = "";
        String modelName = DEFAULT_MODEL_NAME;
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        boolean stream = DEFAULT_STREAM;
        double temperature = DEFAULT_TEMPERATURE;
        int maxTokens = DEFAULT_MAX_TOKENS;

        String excelWorkDir = DEFAULT_EXCEL_WORK_DIR;
        String excelSubModelUrl;
        String excelSubModelKey;
        String excelSubModelName = DEFAULT_EXCEL_SUB_MODEL_NAME;
        String excelSubModelPrompt = DEFAULT_EXCEL_SUB_MODEL_PROMPT;
        double excelSubModelTemperature = DEFAULT_EXCEL_SUB_MODEL_TEMPERATURE;
        int excelMaxRows = DEFAULT_EXCEL_MAX_ROWS;
        boolean excelRestrictPath = DEFAULT_EXCEL_RESTRICT_PATH;

        public Builder apiUrl(String v)         { this.apiUrl = v; return this; }
        public Builder apiKey(String v)         { this.apiKey = v; return this; }
        public Builder modelName(String v)      { this.modelName = v; return this; }
        public Builder systemPrompt(String v)   { this.systemPrompt = v; return this; }
        public Builder stream(boolean v)        { this.stream = v; return this; }
        public Builder temperature(double v)    { this.temperature = v; return this; }
        public Builder maxTokens(int v)         { this.maxTokens = v; return this; }
        public Builder excelWorkDir(String v)              { this.excelWorkDir = v; return this; }
        public Builder excelSubModelUrl(String v)          { this.excelSubModelUrl = v; return this; }
        public Builder excelSubModelKey(String v)          { this.excelSubModelKey = v; return this; }
        public Builder excelSubModelName(String v)         { this.excelSubModelName = v; return this; }
        public Builder excelSubModelPrompt(String v)       { this.excelSubModelPrompt = v; return this; }
        public Builder excelSubModelTemperature(double v)  { this.excelSubModelTemperature = v; return this; }
        public Builder excelMaxRows(int v)                 { this.excelMaxRows = v; return this; }
        public Builder excelRestrictPath(boolean v)        { this.excelRestrictPath = v; return this; }

        public AppConfig build() { return new AppConfig(this); }
    }
}
