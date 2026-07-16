package me.maxt.tool.excel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.maxt.config.AppConfig;
import me.maxt.tool.excel.ExcelOperationExecutor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExcelOperationExecutor 测试")
class ExcelOperationExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 使用 Builder 创建最小配置（maxRows=100）
    private final AppConfig config = new AppConfig.Builder().build();
    private final ExcelOperationExecutor executor = new ExcelOperationExecutor(config);

    @Nested
    @DisplayName("坐标解析")
    class CoordinateParsingTests {

        @Test
        @DisplayName("parseCol: A=0, B=1, Z=25, AA=26")
        void parseCol() {
            assertEquals(0, ExcelOperationExecutor.parseCol("A1"));
            assertEquals(1, ExcelOperationExecutor.parseCol("B1"));
            assertEquals(25, ExcelOperationExecutor.parseCol("Z1"));
            assertEquals(26, ExcelOperationExecutor.parseCol("AA1"));
            assertEquals(27, ExcelOperationExecutor.parseCol("AB1"));
        }

        @Test
        @DisplayName("parseRow: 1→0, 10→9")
        void parseRow() {
            assertEquals(0, ExcelOperationExecutor.parseRow("A1"));
            assertEquals(9, ExcelOperationExecutor.parseRow("A10"));
            assertEquals(99, ExcelOperationExecutor.parseRow("Z100"));
        }

        @Test
        @DisplayName("parseRange: A1:C3 → CellRangeAddress(0,2,0,2)")
        void parseRange() {
            CellRangeAddress range = ExcelOperationExecutor.parseRange("A1:C3");
            assertEquals(0, range.getFirstRow());
            assertEquals(2, range.getLastRow());
            assertEquals(0, range.getFirstColumn());
            assertEquals(2, range.getLastColumn());
        }

        @Test
        @DisplayName("colIndexToLetter: 0→A, 25→Z, 26→AA")
        void colIndexToLetter() {
            assertEquals("A", ExcelOperationExecutor.colIndexToLetter(0));
            assertEquals("Z", ExcelOperationExecutor.colIndexToLetter(25));
            assertEquals("AA", ExcelOperationExecutor.colIndexToLetter(26));
            assertEquals("AB", ExcelOperationExecutor.colIndexToLetter(27));
        }
    }

    @Nested
    @DisplayName("Read 操作")
    class ReadTests {

        private XSSFWorkbook wb;

        @BeforeEach
        void setUp() {
            wb = new XSSFWorkbook();
        }

        @Test
        @DisplayName("读取简单数据返回 Markdown 表格")
        void readSimpleData() throws Exception {
            Sheet sheet = wb.createSheet("Sheet1");
            Row row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("产品");
            row0.createCell(1).setCellValue("销量");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("A");
            row1.createCell(1).setCellValue(100);
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("B");
            row2.createCell(1).setCellValue(200);

            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "read");
            op.put("sheet", "Sheet1");
            op.put("range", "A1:B3");
            op.put("header", true);

            String result = executor.execute(wb, op);
            assertTrue(result.contains("产品"));
            assertTrue(result.contains("100"));
            assertTrue(result.contains("共 3 行"));
        }

        @Test
        @DisplayName("Sheet 不存在时抛出异常含可用 Sheet 列表")
        void sheetNotFound() {
            wb.createSheet("Sheet1");
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "read");
            op.put("sheet", "NonExistent");
            op.put("range", "A1:A1");

            Exception e = assertThrows(IllegalArgumentException.class, () -> executor.execute(wb, op));
            assertTrue(e.getMessage().contains("Sheet1"));
            assertTrue(e.getMessage().contains("NonExistent"));
        }
    }

    @Nested
    @DisplayName("Write 操作（cells 数组格式）")
    class WriteTests {

        private XSSFWorkbook wb;

        @BeforeEach
        void setUp() {
            wb = new XSSFWorkbook();
        }

        @Test
        @DisplayName("cells 数组基本写入，数值列用 type:number")
        void writeCells() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "产品");
            cells.addObject().put("row", 0).put("col", 1).put("value", "销量");
            cells.addObject().put("row", 1).put("col", 0).put("value", "苹果");
            cells.addObject().put("row", 1).put("col", 1).put("value", "100").put("type", "number");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("4 个单元格"));

            Sheet sheet = wb.getSheet("Sheet1");
            assertNotNull(sheet);
            assertEquals("产品", sheet.getRow(0).getCell(0).getStringCellValue());
            // type=number 应存储为 NUMERIC
            Cell numCell = sheet.getRow(1).getCell(1);
            assertEquals(CellType.NUMERIC, numCell.getCellType());
            assertEquals(100.0, numCell.getNumericCellValue(), 0.001);
        }

        @Test
        @DisplayName("自动创建不存在的 Sheet")
        void autoCreateSheet() throws Exception {
            assertNull(wb.getSheet("新Sheet"));

            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "新Sheet");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "x");

            executor.execute(wb, op);
            assertNotNull(wb.getSheet("新Sheet"));
        }

        @Test
        @DisplayName("formula 字段写入公式并求值")
        void writeFormula() throws Exception {
            // 先写入数据
            Sheet sheet = wb.createSheet("Sheet1");
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue(10);
            r0.createCell(1).setCellValue(20);

            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A2");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("formula", "SUM(A1:B1)");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("1 个公式"));
        }

        @Test
        @DisplayName("value 以 = 开头自动识别为公式")
        void valueWithEqualsSign() throws Exception {
            Sheet sheet = wb.createSheet("Sheet1");
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue(10);
            r0.createCell(1).setCellValue(20);

            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A2");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "=SUM(A1:B1)");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("1 个公式"));
            // 验证确实是公式而非纯文本
            Cell cell = sheet.getRow(1).getCell(0);
            assertEquals(CellType.FORMULA, cell.getCellType());
        }

        @Test
        @DisplayName("stylePresets 预设引用")
        void stylePresets() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ObjectNode sp = op.putObject("stylePresets");
            ObjectNode hStyle = sp.putObject("h");
            hStyle.put("bold", true);
            hStyle.put("fontSize", 14);
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "标题").put("style", "h");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("1 个单元格"));

            Cell cell = wb.getSheet("Sheet1").getRow(0).getCell(0);
            assertTrue(wb.getFontAt(cell.getCellStyle().getFontIndex()).getBold());
        }

        @Test
        @DisplayName("内联 style 对象")
        void inlineStyle() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            ObjectNode cellObj = cells.addObject();
            cellObj.put("row", 0);
            cellObj.put("col", 0);
            cellObj.put("value", "数据");
            ObjectNode inlineStyle = cellObj.putObject("style");
            inlineStyle.put("bold", true);
            inlineStyle.put("alignment", "center");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("1 个单元格"));

            Cell cell = wb.getSheet("Sheet1").getRow(0).getCell(0);
            assertTrue(wb.getFontAt(cell.getCellStyle().getFontIndex()).getBold());
            assertEquals(HorizontalAlignment.CENTER, cell.getCellStyle().getAlignment());
        }

        @Test
        @DisplayName("rowspan/colspan 合并单元格")
        void mergeCells() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "大标题").put("rowspan", 1).put("colspan", 2);

            String result = executor.execute(wb, op);
            assertTrue(result.contains("1 处合并单元格"));

            Sheet sheet = wb.getSheet("Sheet1");
            assertEquals(1, sheet.getNumMergedRegions());
        }

        @Test
        @DisplayName("numberFormat 配合 type:number 正确存储为数值")
        void numberFormat() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "1000.5")
                    .put("type", "number").put("numberFormat", "#,##0.00");

            executor.execute(wb, op);

            Cell cell = wb.getSheet("Sheet1").getRow(0).getCell(0);
            assertEquals(CellType.NUMERIC, cell.getCellType());
            assertEquals(1000.5, cell.getNumericCellValue(), 0.001);
            String format = cell.getCellStyle().getDataFormatString();
            assertEquals("#,##0.00", format);
        }

        @Test
        @DisplayName("无 type 字段时默认为 text")
        void defaultTypeText() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "12345");

            executor.execute(wb, op);

            Cell cell = wb.getSheet("Sheet1").getRow(0).getCell(0);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("12345", cell.getStringCellValue());
        }

        @Test
        @DisplayName("type:number 值非数字时兜底存为文本")
        void numberTypeFallback() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            ArrayNode cells = op.putArray("cells");
            cells.addObject().put("row", 0).put("col", 0).put("value", "N/A").put("type", "number");

            executor.execute(wb, op);

            Cell cell = wb.getSheet("Sheet1").getRow(0).getCell(0);
            assertEquals(CellType.STRING, cell.getCellType());
        }

        @Test
        @DisplayName("cells 数组为空时抛异常")
        void emptyCells() {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            op.putArray("cells");

            assertThrows(IllegalArgumentException.class, () -> executor.execute(wb, op));
        }
    }

    @Nested
    @DisplayName("Chart 操作")
    class ChartTests {

        private XSSFWorkbook wb;

        @BeforeEach
        void setUp() {
            wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("月份");
            header.createCell(1).setCellValue("销售额");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("1月");
            row1.createCell(1).setCellValue(1000);
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("2月");
            row2.createCell(1).setCellValue(2000);
        }

        @Test
        @DisplayName("创建柱状图不抛异常")
        void createBarChart() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "chart");
            op.put("sheet", "Sheet1");
            op.put("chart_type", "bar");
            op.put("data_range", "A1:B3");
            op.put("position", "D1");
            op.put("title", "月度销售");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("柱状图"));
            assertTrue(result.contains("月度销售"));
        }

        @Test
        @DisplayName("创建折线图不抛异常")
        void createLineChart() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "chart");
            op.put("sheet", "Sheet1");
            op.put("chart_type", "line");
            op.put("data_range", "A1:B3");
            op.put("position", "D1");
            op.put("title", "趋势");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("折线图"));
        }

        @Test
        @DisplayName("创建饼图不抛异常")
        void createPieChart() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "chart");
            op.put("sheet", "Sheet1");
            op.put("chart_type", "pie");
            op.put("data_range", "A1:B3");
            op.put("position", "D1");
            op.put("title", "占比");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("饼图"));
        }

        @Test
        @DisplayName("不支持的图表类型抛出异常")
        void unsupportedChartType() {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "chart");
            op.put("sheet", "Sheet1");
            op.put("chart_type", "scatter");
            op.put("data_range", "A1:B3");
            op.put("position", "D1");

            assertThrows(Exception.class, () -> executor.execute(wb, op));
        }
    }

    @Nested
    @DisplayName("未知操作类型")
    class UnknownOperationTests {

        @Test
        @DisplayName("未知 type 抛出 IllegalArgumentException")
        void unknownType() {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "unknown");
            XSSFWorkbook wb = new XSSFWorkbook();

            Exception e = assertThrows(IllegalArgumentException.class, () -> executor.execute(wb, op));
            assertTrue(e.getMessage().contains("read, write, chart"));
        }
    }
}
