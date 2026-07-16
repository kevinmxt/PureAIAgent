package me.maxt.tool.excel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.api.ChatApiClient;
import me.maxt.api.DeepSeekApiClient;
import me.maxt.config.AppConfig;
import me.maxt.model.Message;
import me.maxt.tool.Tool;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 操作工具。AI 通过 Function Calling 传入自然语言指令，
 * 工具内部用子模型 LLM 翻译为 JSON 操作序列，再通过 POI 执行。
 *
 * <h3>工具参数</h3>
 * <ul>
 *   <li>{@code instruction} (必填) — 自然语言操作描述</li>
 *   <li>{@code file_path} (可选) — 文件路径，不传用默认目录+时间戳文件名</li>
 *   <li>{@code options} (可选) — {@code max_rows}, {@code sheet_name}</li>
 * </ul>
 */
public class ExcelTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfig config;
    private final ChatApiClient subModelClient;
    private final ExcelOperationExecutor executor;

    public ExcelTool(AppConfig config) {
        this.config = config;

        String subUrl = config.getExcelSubModelUrl() != null
                ? config.getExcelSubModelUrl() : config.getApiUrl();
        String subKey = config.getExcelSubModelKey() != null
                ? config.getExcelSubModelKey() : config.getApiKey();

        this.subModelClient = new DeepSeekApiClient(
                subUrl, subKey, config.getExcelSubModelName(),
                config.getExcelSubModelPrompt(),
                config.getExcelSubModelTemperature(),
                config.getMaxTokens());
        this.executor = new ExcelOperationExecutor(config);
    }

    // ============ Tool 接口 ============

    @Override
    public String name() {
        return "excel_tool";
    }

    @Override
    public String description() {
        return "操作Excel(.xlsx)文件：读取数据（返回Markdown表格）、创建/写入数据（接受Markdown表格）、"
                + "使用Excel公式计算（写入公式并即时求值）、生成图表（柱状图/折线图/饼图，嵌入Excel）。"
                + "只需要用自然语言描述需求，例如\"读取Sheet1的销售数据\"、"
                + "\"创建一个包含产品名和销量的表格\"、\"对B列求平均值\"、\"生成柱状图\"。";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = schema.putObject("properties");

        ObjectNode instr = props.putObject("instruction");
        instr.put("type", "string");
        instr.put("description", "自然语言描述，说明要对Excel做什么操作。"
                + "例如：'读取Sheet1的A1到D10'、'在A1创建月度销售表，包含月份、销售额、利润三列'、"
                + "'对B列求合计'、'用A1到B6的数据生成柱状图'");

        ObjectNode fp = props.putObject("file_path");
        fp.put("type", "string");
        fp.put("description", "Excel文件路径。可以是相对于工作目录的文件名，也可以是绝对路径。"
                + "不指定则自动创建新文件（时间戳命名）。");

        ObjectNode opts = props.putObject("options");
        opts.put("type", "object");
        ObjectNode optProps = opts.putObject("properties");
        ObjectNode mr = optProps.putObject("max_rows");
        mr.put("type", "integer");
        mr.put("description", "读取数据时的最大返回行数");
        ObjectNode sn = optProps.putObject("sheet_name");
        sn.put("type", "string");
        sn.put("description", "指定目标Sheet名称");

        ArrayNode required = schema.putArray("required");
        required.add("instruction");

        return schema;
    }

    // ============ 执行入口 ============

    @Override
    public String execute(JsonNode arguments) throws Exception {
        String instruction = arguments.get("instruction").asText();
        String filePathStr = arguments.has("file_path") && !arguments.get("file_path").isNull()
                ? arguments.get("file_path").asText() : null;
        JsonNode options = arguments.has("options") && !arguments.get("options").isNull()
                ? arguments.get("options") : null;

        Path filePath = resolveFilePath(filePathStr);
        String fileContext = buildFileContext(filePath, options);

        // 子模型翻译 NL → JSON 操作序列
        ArrayNode operations = translateToOperations(instruction, fileContext);

        // 打开工作簿（仅一次） → 执行操作 → 按需保存
        Workbook workbook = openWorkbook(filePath);

        StringBuilder result = new StringBuilder();
        result.append("## Excel 操作结果\n\n**文件**: ").append(filePath.toAbsolutePath()).append("\n\n");

        boolean hasModifications = false;
        for (int i = 0; i < operations.size(); i++) {
            JsonNode op = operations.get(i);
            String opType = op.has("type") ? op.get("type").asText() : "未知";
            if ("write".equals(opType) || "formula".equals(opType) || "chart".equals(opType)) {
                hasModifications = true;
            }
            try {
                String opResult = executor.execute(workbook, op);
                result.append("### 操作 ").append(i + 1).append(": ").append(opType).append("\n");
                result.append(opResult).append("\n");
            } catch (Exception e) {
                result.append("### 操作 ").append(i + 1).append(" 失败 (").append(opType).append(")\n");
                result.append("错误: ").append(e.getMessage()).append("\n\n");
            }
        }

        if (hasModifications) {
            // 先写到内存，再关闭 workbook（close 会触发 POI 内部 re-save，避免覆盖已写入文件）
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            workbook.close();
            Files.createDirectories(filePath.getParent());
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                bos.writeTo(fos);
            }
            result.append("> 文件已保存: ").append(filePath.toAbsolutePath()).append("\n");
        } else {
            workbook.close();
        }
        return result.toString();
    }

    // ============ 文件路径处理 ============

    private Path resolveFilePath(String filePathStr) throws IOException {
        Path workDir = Path.of(config.getExcelWorkDir()).normalize().toAbsolutePath();
        Files.createDirectories(workDir);

        Path resolved;
        if (filePathStr == null || filePathStr.isBlank()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            resolved = workDir.resolve("excel_" + timestamp + ".xlsx");
        } else {
            Path raw = Path.of(filePathStr);
            resolved = raw.isAbsolute() ? raw.normalize() : workDir.resolve(raw).normalize();
        }

        // 安全检查
        if (config.isExcelRestrictPath()) {
            if (!resolved.startsWith(workDir)) {
                throw new SecurityException(
                        "安全限制：不允许访问工作目录以外的文件。\n"
                        + "  工作目录: " + workDir.toAbsolutePath() + "\n"
                        + "  请求路径: " + resolved.toAbsolutePath() + "\n"
                        + "  如需解除限制，请将 config.properties 中 excel.restrict_path 设为 false");
            }
        }

        // 扩展名检查
        String fileName = resolved.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw new IllegalArgumentException("仅支持 .xlsx 或 .xls 格式: " + fileName);
        }

        return resolved;
    }

    // ============ 文件上下文 ============

    private String buildFileContext(Path filePath, JsonNode options) {
        StringBuilder ctx = new StringBuilder();
        if (!Files.exists(filePath)) {
            ctx.append("文件状态: 新建文件\n");
        } else {
            ctx.append("文件状态: 已存在\n");
        }
        ctx.append("文件名: ").append(filePath.getFileName()).append("\n");
        if (options != null && options.has("sheet_name")) {
            ctx.append("用户指定Sheet: ").append(options.get("sheet_name").asText()).append("\n");
        }
        return ctx.toString();
    }

    // ============ 子模型调用 ============

    private ArrayNode translateToOperations(String instruction, String fileContext) throws Exception {
        String prompt = config.getExcelSubModelPrompt() + "\n\n## 当前文件信息\n" + fileContext;
        String jsonStr = callSubModel(instruction, prompt);
        ArrayNode ops = tryParseJsonArray(jsonStr);
        if (ops != null) return ops;

        // 重试一次，更严格的提示
        String retryPrompt = prompt + "\n\n【重要】请只返回有效的JSON数组。不要包含任何说明文字、markdown代码块标记或其他内容。";
        jsonStr = callSubModel(instruction, retryPrompt);
        ops = tryParseJsonArray(jsonStr);
        if (ops != null) return ops;

        throw new IllegalArgumentException(
                "子模型返回了无效的JSON。请简化你的指令后重试。\n原始响应前200字符: "
                + jsonStr.substring(0, Math.min(200, jsonStr.length())));
    }

    private String callSubModel(String instruction, String prompt) throws Exception {
        List<Message> msgs = new ArrayList<>();
        msgs.add(new Message("system", prompt));
        msgs.add(new Message("user", instruction));
        Message response = subModelClient.chat(msgs, List.of());
        return extractJsonArray(response.getContent());
    }

    private String extractJsonArray(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        int bracket = trimmed.indexOf('[');
        if (bracket >= 0) {
            int lastBracket = trimmed.lastIndexOf(']');
            if (lastBracket > bracket) {
                trimmed = trimmed.substring(bracket, lastBracket + 1);
            }
        }
        return trimmed;
    }

    private ArrayNode tryParseJsonArray(String text) {
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node instanceof ArrayNode arr) return arr;
        } catch (Exception ignored) {}
        return null;
    }

    // ============ 工作簿操作 ============

    private Workbook openWorkbook(Path filePath) throws IOException {
        if (Files.exists(filePath)) {
            try {
                return new XSSFWorkbook(filePath.toFile());
            } catch (Exception e) {
                throw new IOException("无法打开文件: " + filePath, e);
            }
        }
        return new XSSFWorkbook();
    }

}
