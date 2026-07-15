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
    @DisplayName("Markdown 表格解析")
    class MarkdownParsingTests {

        @Test
        @DisplayName("标准表格: 表头+分隔行+数据行")
        void standardTable() {
            String md = """
                    | 姓名 | 年龄 | 城市 |
                    | --- | --- | --- |
                    | 张三 | 25 | 北京 |
                    | 李四 | 30 | 上海 |""";

            String[][] data = ExcelOperationExecutor.parseMarkdownTable(md);
            assertEquals(3, data.length);
            assertArrayEquals(new String[]{"姓名", "年龄", "城市"}, data[0]);
            assertArrayEquals(new String[]{"张三", "25", "北京"}, data[1]);
            assertArrayEquals(new String[]{"李四", "30", "上海"}, data[2]);
        }

        @Test
        @DisplayName("无分隔行: 表头按数据行处理")
        void noSeparator() {
            String md = """
                    | A | B |
                    | 1 | 2 |""";

            String[][] data = ExcelOperationExecutor.parseMarkdownTable(md);
            assertEquals(2, data.length);
        }

        @Test
        @DisplayName("单列表格")
        void singleColumn() {
            String md = """
                    | 名称 |
                    | --- |
                    | A |
                    | B |""";

            String[][] data = ExcelOperationExecutor.parseMarkdownTable(md);
            assertEquals(3, data.length);
            assertEquals(1, data[0].length);
            assertEquals("名称", data[0][0]);
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
    @DisplayName("Write 操作")
    class WriteTests {

        private XSSFWorkbook wb;

        @BeforeEach
        void setUp() {
            wb = new XSSFWorkbook();
        }

        @Test
        @DisplayName("写入 Markdown 表格数据到 Excel")
        void writeData() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "Sheet1");
            op.put("range", "A1");
            op.put("data", "| 名称 | 数量 |\n| --- | --- |\n| 苹果 | 10 |\n| 香蕉 | 20 |");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("已写入 3 行"));
            assertTrue(result.contains("Sheet1!A1"));

            // 验证写入结果
            Sheet sheet = wb.getSheet("Sheet1");
            assertNotNull(sheet);
            assertEquals("名称", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("苹果", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(20.0, Double.parseDouble(sheet.getRow(2).getCell(1).getStringCellValue()));
        }

        @Test
        @DisplayName("自动创建不存在的 Sheet")
        void autoCreateSheet() throws Exception {
            assertNull(wb.getSheet("新Sheet"));

            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "write");
            op.put("sheet", "新Sheet");
            op.put("range", "A1");
            op.put("data", "| x |\n| --- |\n| 1 |");

            executor.execute(wb, op);
            assertNotNull(wb.getSheet("新Sheet"));
        }
    }

    @Nested
    @DisplayName("Formula 操作")
    class FormulaTests {

        private XSSFWorkbook wb;

        @BeforeEach
        void setUp() {
            wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("Sheet1");
            Row row1 = sheet.createRow(0);  // A1=10, B1=20
            row1.createCell(0).setCellValue(10);
            row1.createCell(1).setCellValue(20);
            Row row2 = sheet.createRow(1);  // A2=30, B2=40
            row2.createCell(0).setCellValue(30);
            row2.createCell(1).setCellValue(40);
        }

        @Test
        @DisplayName("SUM 公式写入并求值")
        void sumFormula() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "formula");
            op.put("sheet", "Sheet1");
            op.put("range", "A3");
            op.put("formula", "SUM(A1:A2)");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("SUM(A1:A2)"));
            assertTrue(result.contains("40"));
        }

        @Test
        @DisplayName("AVERAGE 公式写入并求值")
        void averageFormula() throws Exception {
            ObjectNode op = MAPPER.createObjectNode();
            op.put("type", "formula");
            op.put("sheet", "Sheet1");
            op.put("range", "C1");
            op.put("formula", "AVERAGE(A1:B2)");

            String result = executor.execute(wb, op);
            assertTrue(result.contains("25"));
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
            assertTrue(e.getMessage().contains("未知操作类型"));
        }
    }
}
