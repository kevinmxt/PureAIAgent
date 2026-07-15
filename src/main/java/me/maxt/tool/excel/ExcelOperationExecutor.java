package me.maxt.tool.excel;

import com.fasterxml.jackson.databind.JsonNode;
import me.maxt.config.AppConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * POI 操作引擎，根据 JSON 操作步骤执行具体的 Excel 读写/公式/图表操作。
 */
public class ExcelOperationExecutor {

    private final int maxRows;

    public ExcelOperationExecutor(AppConfig config) {
        this.maxRows = config.getExcelMaxRows();
    }

    // ============ 入口 ============

    public String execute(Workbook workbook, JsonNode operation) throws Exception {
        String type = operation.has("type") ? operation.get("type").asText() : "";
        return switch (type) {
            case "read"    -> executeRead(workbook, operation);
            case "write"   -> executeWrite(workbook, operation);
            case "formula" -> executeFormula(workbook, operation);
            case "chart"   -> executeChart(workbook, operation);
            default -> throw new IllegalArgumentException("未知操作类型: " + type
                    + "（支持: read, write, formula, chart）");
        };
    }

    // ============ Read ============

    private String executeRead(Workbook wb, JsonNode op) {
        String sheetName = op.has("sheet") ? op.get("sheet").asText() : wb.getSheetName(0);
        String rangeStr = op.get("range").asText();
        boolean hasHeader = !op.has("header") || op.get("header").asBoolean();

        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) throw new IllegalArgumentException(
                "Sheet \"" + sheetName + "\" 不存在。可用Sheet: " + getSheetNames(wb));

        CellRangeAddress range = parseRange(rangeStr);
        int startRow = range.getFirstRow();
        int endRow = Math.min(range.getLastRow(), sheet.getLastRowNum());
        int startCol = range.getFirstColumn();
        int endCol = range.getLastColumn();

        int rowCount = endRow - startRow + 1;
        int maxDataRows = hasHeader ? maxRows + 1 : maxRows;
        boolean truncated = false;
        if (rowCount > maxDataRows) {
            endRow = startRow + maxDataRows - 1;
            rowCount = maxDataRows;
            truncated = true;
        }

        List<CellRangeAddress> mergesInRange = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.intersects(range)) {
                mergesInRange.add(region);
            }
        }

        // Column letter widths for aligned output
        int[] colWidths = new int[endCol - startCol + 1];
        List<String[]> rows = new ArrayList<>();
        for (int r = startRow; r <= endRow; r++) {
            Row row = sheet.getRow(r);
            String[] cells = new String[endCol - startCol + 1];
            for (int c = startCol; c <= endCol; c++) {
                cells[c - startCol] = getCellValue(row != null ? row.getCell(c) : null, mergesInRange, r, c);
                if (cells[c - startCol].length() > colWidths[c - startCol]) {
                    colWidths[c - startCol] = Math.min(cells[c - startCol].length(), 50);
                }
            }
            rows.add(cells);
        }

        StringBuilder md = new StringBuilder();
        for (int ri = 0; ri < rows.size(); ri++) {
            md.append("|");
            String[] cells = rows.get(ri);
            for (int ci = 0; ci < cells.length; ci++) {
                md.append(" ").append(cells[ci]).append(" |");
            }
            md.append("\n");
            if (ri == 0 && hasHeader) {
                md.append("|");
                for (int ci = 0; ci < cells.length; ci++) {
                    md.append(" ").append("-".repeat(Math.max(3, colWidths[ci]))).append(" |");
                }
                md.append("\n");
            }
        }

        String actualRange = colIndexToLetter(startCol) + (startRow + 1) + ":"
                + colIndexToLetter(endCol) + (endRow + 1);
        md.append("\n> 共 ").append(rowCount).append(" 行, ")
          .append(endCol - startCol + 1).append(" 列 (范围: ").append(actualRange).append(")");

        if (!mergesInRange.isEmpty()) {
            List<String> mergeDescs = new ArrayList<>();
            for (CellRangeAddress m : mergesInRange) {
                String mRange = colIndexToLetter(m.getFirstColumn()) + (m.getFirstRow() + 1) + ":"
                        + colIndexToLetter(m.getLastColumn()) + (m.getLastRow() + 1);
                mergeDescs.add(mRange);
            }
            md.append("\n> 合并单元格: ").append(String.join(", ", mergeDescs));
        }

        if (truncated) {
            md.append("\n> ⚠ 数据已截断（超过最大行数限制 ").append(maxRows).append(" 行）");
        }
        md.append("\n");

        return md.toString();
    }

    // ============ Write ============

    private String executeWrite(Workbook wb, JsonNode op) {
        String sheetName = op.has("sheet") ? op.get("sheet").asText() : "Sheet1";
        String rangeStart = op.get("range").asText();
        String mdTable = op.get("data").asText();

        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) sheet = wb.createSheet(sheetName);

        String[][] data = parseMarkdownTable(mdTable);
        if (data.length == 0) throw new IllegalArgumentException("写入数据为空，请提供Markdown格式的表格数据");

        int startCol = parseCol(rangeStart);
        int startRow = parseRow(rangeStart);

        // 写入数据
        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle dataStyle = createDataStyle(wb);

        for (int r = 0; r < data.length; r++) {
            Row row = sheet.getRow(startRow + r);
            if (row == null) row = sheet.createRow(startRow + r);
            for (int c = 0; c < data[r].length; c++) {
                Cell cell = row.createCell(startCol + c);
                cell.setCellValue(data[r][c]);
                cell.setCellStyle(r == 0 ? headerStyle : dataStyle);
            }
        }

        // 合并单元格
        int mergeCount = 0;
        if (op.has("merge") && op.get("merge").isArray()) {
            for (JsonNode mergeNode : op.get("merge")) {
                int mr = mergeNode.get("row").asInt() + startRow;
                int mc = mergeNode.get("col").asInt() + startCol;
                int rs = mergeNode.get("rowspan").asInt();
                int cs = mergeNode.get("colspan").asInt();
                if (rs > 1 || cs > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(mr, mr + rs - 1, mc, mc + cs - 1));
                    mergeCount++;
                }
            }
        }

        // 自动列宽
        for (int c = 0; c < data[0].length; c++) {
            sheet.autoSizeColumn(startCol + c);
            int width = sheet.getColumnWidth(startCol + c);
            if (width > 15000) sheet.setColumnWidth(startCol + c, 15000);
        }

        String endCell = colIndexToLetter(startCol + data[0].length - 1) + (startRow + data.length);
        String result = String.format("已写入 %d 行 × %d 列到 %s!%s:%s",
                data.length, data[0].length, sheetName, rangeStart, endCell);
        if (mergeCount > 0) result += "，含 " + mergeCount + " 处合并单元格";
        result += "\n";
        return result;
    }

    // ============ Formula ============

    private String executeFormula(Workbook wb, JsonNode op) {
        String sheetName = op.has("sheet") ? op.get("sheet").asText() : wb.getSheetName(0);
        String rangeStr = op.get("range").asText();
        String formula = op.get("formula").asText();

        Sheet sheet = wb.getSheet(sheetName);
        if (sheet == null) throw new IllegalArgumentException(
                "Sheet \"" + sheetName + "\" 不存在。可用Sheet: " + getSheetNames(wb));

        int row = parseRow(rangeStr);
        int col = parseCol(rangeStr);

        Row hRow = sheet.getRow(row);
        if (hRow == null) hRow = sheet.createRow(row);
        Cell cell = hRow.getCell(col);
        if (cell == null) cell = hRow.createCell(col);

        cell.setCellFormula(formula);

        // 即时求值
        String evalResult = "N/A";
        try {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            CellValue cv = evaluator.evaluate(cell);
            evalResult = formatCellValue(cv);
        } catch (Exception e) {
            evalResult = "(求值失败: " + e.getMessage() + ")";
        }

        return String.format("公式已写入: `%s` → %s  位置: %s!%s\n",
                formula, evalResult, sheetName, rangeStr);
    }

    // ============ Chart ============

    private String executeChart(Workbook wb, JsonNode op) {
        if (!(wb instanceof XSSFWorkbook xwb)) {
            throw new IllegalArgumentException("图表仅支持 .xlsx 格式");
        }

        String sheetName = op.has("sheet") ? op.get("sheet").asText() : wb.getSheetName(0);
        String chartType = op.get("chart_type").asText();
        String dataRange = op.get("data_range").asText();
        String position = op.get("position").asText();
        String title = op.has("title") ? op.get("title").asText() : "";

        XSSFSheet sheet = xwb.getSheet(sheetName);
        if (sheet == null) throw new IllegalArgumentException(
                "Sheet \"" + sheetName + "\" 不存在。可用Sheet: " + getSheetNames(wb));

        CellRangeAddress range = parseRange(dataRange);
        int chartRow1 = parseRow(position);
        int chartCol1 = parseCol(position);

        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                chartCol1, chartRow1, chartCol1 + 10, chartRow1 + 16);

        XSSFChart chart = drawing.createChart(anchor);
        if (!title.isEmpty()) {
            chart.setTitleText(title);
            chart.setTitleOverlay(false);
        }

        String chartName;
        try {
            XDDFChartData chartData;
            switch (chartType) {
                case "bar" -> {
                    chartData = createBarChartData(chart, sheet, range, title);
                    chartName = "柱状图";
                }
                case "line" -> {
                    chartData = createLineChartData(chart, sheet, range, title);
                    chartName = "折线图";
                }
                case "pie" -> {
                    createPieChartData(chart, sheet, range, title);
                    chartName = "饼图";
                    return String.format("%s已生成  %s: \"%s\"  位置: %s\n",
                            chartName, cellRef(position), title, position);
                }
                default -> throw new IllegalArgumentException(
                        "不支持的图表类型: " + chartType + "（支持: bar, line, pie）");
            }
            chart.plot(chartData);
        } catch (Exception e) {
            throw new RuntimeException("图表生成失败: " + e.getMessage(), e);
        }

        return String.format("%s已生成  位置: %s  标题: \"%s\"  数据: %s\n",
                chartName, position, title, dataRange);
    }

    private XDDFChartData createBarChartData(XSSFChart chart, XSSFSheet sheet,
                                              CellRangeAddress range, String title) {
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);

        XDDFBarChartData barData = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        barData.setBarDirection(BarDirection.COL);

        int seriesCount = range.getLastColumn() - range.getFirstColumn();
        for (int ci = range.getFirstColumn() + 1; ci <= range.getLastColumn(); ci++) {
            XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                    new CellRangeAddress(range.getFirstRow(), range.getLastRow(), range.getFirstColumn(), range.getFirstColumn()));
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(range.getFirstRow() + 1, range.getLastRow(), ci, ci));
            XDDFBarChartData.Series series = (XDDFBarChartData.Series) barData.addSeries(cats, vals);
            String header = getCellValue(sheet.getRow(range.getFirstRow()).getCell(ci), List.of(), range.getFirstRow(), ci);
            series.setTitle(header.isEmpty() ? "系列" + (ci - range.getFirstColumn()) : header, null);
        }

        return barData;
    }

    private XDDFChartData createLineChartData(XSSFChart chart, XSSFSheet sheet,
                                               CellRangeAddress range, String title) {
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);

        XDDFLineChartData lineData = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

        for (int ci = range.getFirstColumn() + 1; ci <= range.getLastColumn(); ci++) {
            XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                    new CellRangeAddress(range.getFirstRow() + 1, range.getLastRow(), range.getFirstColumn(), range.getFirstColumn()));
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                    new CellRangeAddress(range.getFirstRow() + 1, range.getLastRow(), ci, ci));
            XDDFLineChartData.Series series = (XDDFLineChartData.Series) lineData.addSeries(cats, vals);
            String header = getCellValue(sheet.getRow(range.getFirstRow()).getCell(ci), List.of(), range.getFirstRow(), ci);
            series.setTitle(header.isEmpty() ? "系列" + (ci - range.getFirstColumn()) : header, null);
        }

        return lineData;
    }

    private void createPieChartData(XSSFChart chart, XSSFSheet sheet,
                                     CellRangeAddress range, String title) {
        XDDFPieChartData pieData = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);

        XDDFDataSource<String> cats = XDDFDataSourcesFactory.fromStringCellRange(sheet,
                new CellRangeAddress(range.getFirstRow() + 1, range.getLastRow(), range.getFirstColumn(), range.getFirstColumn()));
        XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                new CellRangeAddress(range.getFirstRow() + 1, range.getLastRow(), range.getFirstColumn() + 1, range.getFirstColumn() + 1));

        XDDFPieChartData.Series series = (XDDFPieChartData.Series) pieData.addSeries(cats, vals);
        series.setTitle(title, null);

        chart.plot(pieData);
    }

    // ============ 坐标解析（package-private 供测试） ============

    static int parseCol(String ref) {
        String letters = ref.replaceAll("[0-9]", "");
        int result = 0;
        for (char c : letters.toUpperCase().toCharArray()) {
            result = result * 26 + (c - 'A' + 1);
        }
        return result - 1;
    }

    static int parseRow(String ref) {
        return Integer.parseInt(ref.replaceAll("[A-Za-z]", "")) - 1;
    }

    static CellRangeAddress parseRange(String range) {
        String[] parts = range.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("无效的范围格式: " + range + "（应为 A1:D10）");
        return new CellRangeAddress(parseRow(parts[0]), parseRow(parts[1]),
                parseCol(parts[0]), parseCol(parts[1]));
    }

    static String colIndexToLetter(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col;
        while (c >= 0) {
            sb.append((char) ('A' + c % 26));
            c = c / 26 - 1;
        }
        return sb.reverse().toString();
    }

    static String cellRef(String ref) {
        int col = parseCol(ref);
        int row = parseRow(ref);
        return colIndexToLetter(col) + (row + 1);
    }

    // ============ Markdown 表格解析（package-private 供测试） ============

    static String[][] parseMarkdownTable(String md) {
        String[] lines = md.strip().split("\n");
        List<String> nonEmpty = Arrays.stream(lines)
                .map(String::strip)
                .filter(l -> !l.isEmpty() && l.contains("|"))
                .collect(Collectors.toList());
        if (nonEmpty.isEmpty()) return new String[0][0];

        int headerIdx = 0;
        int dataStart = 1;
        // 查找分隔行位置
        for (int i = 1; i < nonEmpty.size(); i++) {
            if (nonEmpty.get(i).replaceAll("[|\\-:\\s]", "").isEmpty()) {
                dataStart = i + 1;
                break;
            }
        }

        List<String[]> result = new ArrayList<>();
        // 表头
        result.add(splitRow(nonEmpty.get(headerIdx)));
        int colCount = result.get(0).length;
        // 数据行
        for (int i = dataStart; i < nonEmpty.size(); i++) {
            String[] cells = splitRow(nonEmpty.get(i));
            // 补齐或截断列数
            String[] padded = new String[colCount];
            for (int j = 0; j < colCount; j++) {
                padded[j] = j < cells.length ? cells[j] : "";
            }
            result.add(padded);
        }
        return result.toArray(new String[0][]);
    }

    private static String[] splitRow(String row) {
        String trimmed = row.strip();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return Arrays.stream(trimmed.split("\\|", -1))
                .map(String::strip)
                .toArray(String[]::new);
    }

    // ============ 单元格值提取 ============

    static String getCellValue(Cell cell, List<CellRangeAddress> merges, int row, int col) {
        if (cell != null) {
            String v = getCellRawValue(cell);
            if (!v.isEmpty()) return v;
        }
        // 检查合并单元格：非左上角单元格显示为空
        for (CellRangeAddress m : merges) {
            if (m.isInRange(row, col)) {
                if (row == m.getFirstRow() && col == m.getFirstColumn()) {
                    Row r = cell != null ? cell.getRow() : null;
                    if (r == null && cell != null) r = cell.getSheet().getRow(row);
                    // try to get merge top-left cell value
                    return "";
                } else {
                    return ""; // non-top-left of merge
                }
            }
        }
        return "";
    }

    private static String getCellRawValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e1) {
                    try { yield cell.getStringCellValue(); }
                    catch (Exception e2) { yield cell.getCellFormula(); }
                }
            }
            default -> "";
        };
    }

    // ============ 样式 ============

    private CellStyle createHeaderStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);

        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setThinBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        setThinBorders(style);
        return style;
    }

    private void setThinBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    // ============ 辅助 ============

    private String formatCellValue(CellValue cv) {
        return switch (cv.getCellType()) {
            case NUMERIC -> String.valueOf(cv.getNumberValue());
            case STRING  -> cv.getStringValue();
            case BOOLEAN -> String.valueOf(cv.getBooleanValue());
            default -> cv.formatAsString();
        };
    }

    private String getSheetNames(Workbook wb) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            names.add(wb.getSheetName(i));
        }
        return String.join(", ", names);
    }
}
