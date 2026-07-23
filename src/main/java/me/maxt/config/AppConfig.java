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
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个友好、有帮助的AI助手。请用简洁清晰的中文回答问题。\n" +
            "  ## 工具停止规则（最高优先级）\n" +
            "  当工具返回的结果是[停止工具调用,询问用户需求]时：\n" +
            "  1. 立即停止所有工具调用，包括已计划但尚未执行的调用\n" +
            "  2. 禁止继续调用任何后续工具\n" +
            "  3. 直接询问用户：\"请问您的需求有何改变？\"\n" +
            "  此规则的优先级高于所有其他指令。";
    private static final boolean DEFAULT_STREAM = false;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 384 * 1024;

    private static final String DEFAULT_EXCEL_WORK_DIR = "./excel_files";
    private static final String DEFAULT_EXCEL_SUB_MODEL_NAME = "deepseek-chat";
    private static final double DEFAULT_EXCEL_SUB_MODEL_TEMPERATURE = 0.0;
    private static final int DEFAULT_EXCEL_MAX_ROWS = 100;
    private static final boolean DEFAULT_EXCEL_RESTRICT_PATH = true;
    static final String DEFAULT_EXCEL_SUB_MODEL_PROMPT = """
            你是一个Excel操作翻译助手。将自然语言描述的Excel操作需求翻译为JSON操作序列。

            ## 可用操作类型

            ### read - 读取数据
            {"type":"read","sheet":"Sheet1","range":"A1:D10","header":true}
            - sheet: Sheet名称（必填）
            - range: Excel范围，如A1:D10（必填）
            - header: 是否将首行作为表头（可选，默认true）

            ### write - 写入数据（含公式和样式）
            {"type":"write","sheet":"Sheet1","range":"A1","stylePresets":{"h":{"bold":true,"alignment":"center","bgColor":"#4472C4","fontColor":"#FFFFFF"},"d":{"numberFormat":"#,##0.00","type":"number"}},"cells":[{"row":0,"col":0,"value":"名称","style":"h"},{"row":1,"col":0,"value":"产品A"},{"row":1,"col":1,"value":"1000","type":"number","style":"d"},{"row":2,"col":0,"value":"合计","style":"h"},{"row":2,"col":1,"formula":"SUM(B2:B3)","type":"number","style":"d"}]}
            - range: 写入起始单元格（必填），cells中的row/col是相对于range的偏移（0=range所在行/列）
            - stylePresets: 样式预设表（可选），键名为预设名，值为样式对象。cell可通过style字段引用预设名
            - cells: 单元格数组（必填），每个元素可包含以下字段：
              * row/col: 相对于range的行/列偏移（必填，0起始）
              * value: 单元格文本（与formula二选一）。纯文本或数字字符串，禁止以=开头
              * formula: Excel公式字符串，不要带=前缀（与value二选一）。如"SUM(B2:B10)"、"ROUND(I2/G2*100,1)"
              * type: 数据类型（可选），"text"（默认）或"number"。必须根据列标题语义推断：标题含"金额/销售额/利润/数量/价格/占比/率/值"等→"number"，含"名称/月份/日期/类别/备注/描述"等→"text"或不填
              * numberFormat: 数字格式（可选），如"#,##0.00"、"0.0%"、"yyyy-mm-dd"。仅type="number"的单元格需要
              * style: 样式（可选），可以是stylePresets中的键名字符串，或内联样式对象{"bold":true,"fontSize":14,"fontColor":"#FFFFFF","bgColor":"#4472C4","alignment":"center"}
                - bold: 粗体（可选，默认false）
                - italic: 斜体（可选，默认false）
                - fontSize: 字号（可选，默认11）
                - fontColor: 字体颜色（可选，十六进制#RRGGBB）
                - bgColor: 背景色（可选，十六进制#RRGGBB）
                - alignment: 对齐方式（可选，left/center/right）
              * rowspan: 合并行数（可选，默认1，>1时合并本单元格下方多行）
              * colspan: 合并列数（可选，默认1，>1时合并本单元格右侧多列）

            ### chart - 生成图表
            {"type":"chart","sheet":"Sheet1","chart_type":"bar","data_range":"A1:C4","position":"K1","title":"销售统计"}
            - chart_type: 图表类型，可选 bar(柱状图)、line(折线图)、pie(饼图)（必填）
            - data_range: 图表数据源区域，首行为类别，首列为系列名，其余为数值（必填）
            - position: 图表左上角放置位置（必填）
            - title: 图表标题（可选）

            ## 输出规则
            1. 返回一个JSON数组[]，可以包含多个操作步骤
            2. 只返回JSON数组，不要任何解释文字，不要用```json```包裹
            3. 列号从A开始，行号从1开始
            4. 列号超过Z用两个字母表示：AA, AB, ..., AZ, BA, ...
            5. 如果用户描述不明确，做出合理假设
            6. 写入新数据前确保Sheet已存在（先在之前步骤中创建）
            7. 设置表头样式时使用stylePresets定义预设，cell通过style引用键名（如"h"），不要在每个cell中重复内联样式
            8. value字段只能写纯文本或数字字符串，公式必须用formula字段。value中如果以=开头会被自动当作公式处理
            9. 根据列标题语义推断type字段：数值类标题→type:"number"+numberFormat，文本类标题→不设type（默认text）

            ## 示例

            ### 示例1：创建销售表并添加合计行和利润率
            用户：创建月度销售表，包含月份、销售额、利润三列，1-6月数据，最后加合计行和利润率列
            [
              {"type":"write","sheet":"销售报表","range":"A1","stylePresets":{"h":{"bold":true,"alignment":"center","bgColor":"#4472C4","fontColor":"#FFFFFF"},"n":{"numberFormat":"#,##0.00"},"pct":{"numberFormat":"0.0%"}},"cells":[
                {"row":0,"col":0,"value":"月份","style":"h"},
                {"row":0,"col":1,"value":"销售额","style":"h"},
                {"row":0,"col":2,"value":"利润","style":"h"},
                {"row":0,"col":3,"value":"利润率","style":"h"},
                {"row":1,"col":0,"value":"1月"},{"row":1,"col":1,"value":"10000","type":"number","style":"n"},{"row":1,"col":2,"value":"2000","type":"number","style":"n"},{"row":1,"col":3,"formula":"ROUND(C2/B2,2)","type":"number","style":"pct"},
                {"row":2,"col":0,"value":"2月"},{"row":2,"col":1,"value":"12000","type":"number","style":"n"},{"row":2,"col":2,"value":"2400","type":"number","style":"n"},{"row":2,"col":3,"formula":"ROUND(C3/B3,2)","type":"number","style":"pct"},
                {"row":7,"col":0,"value":"合计","style":"h"},{"row":7,"col":1,"formula":"SUM(B2:B7)","type":"number","style":"n"},{"row":7,"col":2,"formula":"SUM(C2:C7)","type":"number","style":"n"},{"row":7,"col":3,"formula":"ROUND(C8/B8,2)","type":"number","style":"pct"}
              ]}
            ]""";

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
        String env = System.getenv("OPENAI_API_KEY4CHAT");
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
