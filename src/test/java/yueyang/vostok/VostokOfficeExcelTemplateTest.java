package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeExcelTemplateTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.Office.close();
        Vostok.File.close();
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        Vostok.Office.init(new VKOfficeConfig());
    }

    @Test
    void testRenderVariableAndConditionBlock() {
        init();
        VKExcelSheet sheet = new VKExcelSheet("S1")
                .addCell(VKExcelCell.stringCell(1, 1, "订单号"))
                .addCell(VKExcelCell.stringCell(1, 2, "{{orderNo}}"))
                .addCell(VKExcelCell.stringCell(2, 1, "{{?vip}}"))
                .addCell(VKExcelCell.stringCell(3, 1, "VIP: {{vipLevel}}"))
                .addCell(VKExcelCell.stringCell(4, 1, "{{/vip}}"))
                .addCell(VKExcelCell.stringCell(5, 1, "END"));
        Vostok.Office.writeExcel("tpl/basic.xlsx", new VKExcelWorkbook().addSheet(sheet));

        Vostok.Office.renderExcelTemplate("tpl/basic.xlsx", "out/basic.xlsx",
                Map.of("orderNo", "A001", "vip", true, "vipLevel", "S"),
                VKExcelTemplateOptions.defaults());
        VKExcelSheet rendered = Vostok.Office.readExcel("out/basic.xlsx").getSheet("S1");
        assertEquals("A001", rendered.getCell(1, 2).value());
        assertEquals("VIP: S", rendered.getCell(2, 1).value());
        assertEquals("END", rendered.getCell(3, 1).value());
        assertNull(rendered.getCell(4, 1));
    }

    @Test
    void testLoopDefaultKeepPlaceholderRows() {
        init();
        writeLoopTemplate("tpl/keep-true.xlsx", "{{#items as item}}");

        Vostok.Office.renderExcelTemplate("tpl/keep-true.xlsx", "out/keep-true.xlsx",
                Map.of("items", List.of(
                        Map.of("name", "A"),
                        Map.of("name", "B")
                )), VKExcelTemplateOptions.defaults());

        VKExcelSheet rendered = Vostok.Office.readExcel("out/keep-true.xlsx").getSheet("Orders");
        assertEquals("HEAD", rendered.getCell(1, 1).value());
        assertEquals("", rendered.getCell(2, 1).value());
        assertEquals("A", rendered.getCell(3, 1).value());
        assertEquals("B", rendered.getCell(4, 1).value());
        assertEquals("", rendered.getCell(5, 1).value());
        assertEquals("TAIL", rendered.getCell(6, 1).value());
    }

    @Test
    void testLoopKeepPlaceholderRowsFalseStartFromStartRow() {
        init();
        writeLoopTemplate("tpl/keep-false.xlsx", "{{#items as item keepPlaceholderRows=false}}");

        Vostok.Office.renderExcelTemplate("tpl/keep-false.xlsx", "out/keep-false.xlsx",
                Map.of("items", List.of(
                        Map.of("name", "A"),
                        Map.of("name", "B")
                )), VKExcelTemplateOptions.defaults());

        VKExcelSheet rendered = Vostok.Office.readExcel("out/keep-false.xlsx").getSheet("Orders");
        assertEquals("HEAD", rendered.getCell(1, 1).value());
        assertEquals("A", rendered.getCell(2, 1).value());
        assertEquals("B", rendered.getCell(3, 1).value());
        assertEquals("TAIL", rendered.getCell(4, 1).value());
        assertNull(rendered.getCell(5, 1));
    }

    @Test
    void testLoopEmptyListWithTwoKeepStrategies() {
        init();
        writeLoopTemplate("tpl/empty-keep.xlsx", "{{#items as item}}");
        writeLoopTemplate("tpl/empty-drop.xlsx", "{{#items as item keepPlaceholderRows=false}}");

        Vostok.Office.renderExcelTemplate("tpl/empty-keep.xlsx", "out/empty-keep.xlsx",
                Map.of("items", List.of()), VKExcelTemplateOptions.defaults());
        Vostok.Office.renderExcelTemplate("tpl/empty-drop.xlsx", "out/empty-drop.xlsx",
                Map.of("items", List.of()), VKExcelTemplateOptions.defaults());

        VKExcelSheet keepSheet = Vostok.Office.readExcel("out/empty-keep.xlsx").getSheet("Orders");
        assertEquals("HEAD", keepSheet.getCell(1, 1).value());
        assertEquals("", keepSheet.getCell(2, 1).value());
        assertEquals("", keepSheet.getCell(3, 1).value());
        assertEquals("TAIL", keepSheet.getCell(4, 1).value());

        VKExcelSheet dropSheet = Vostok.Office.readExcel("out/empty-drop.xlsx").getSheet("Orders");
        assertEquals("HEAD", dropSheet.getCell(1, 1).value());
        assertEquals("TAIL", dropSheet.getCell(2, 1).value());
        assertNull(dropSheet.getCell(3, 1));
    }

    @Test
    void testTargetSheetsFilter() {
        init();
        VKExcelWorkbook workbook = new VKExcelWorkbook()
                .addSheet(new VKExcelSheet("S1").addCell(VKExcelCell.stringCell(1, 1, "{{name}}")))
                .addSheet(new VKExcelSheet("S2").addCell(VKExcelCell.stringCell(1, 1, "{{name}}")));
        Vostok.Office.writeExcel("tpl/target.xlsx", workbook);

        VKExcelTemplateOptions options = VKExcelTemplateOptions.defaults().targetSheets(List.of("S1"));
        Vostok.Office.renderExcelTemplate("tpl/target.xlsx", "out/target.xlsx", Map.of("name", "Tom"), options);

        VKExcelWorkbook rendered = Vostok.Office.readExcel("out/target.xlsx");
        assertEquals("Tom", rendered.getSheet("S1").getCell(1, 1).value());
        assertEquals("{{name}}", rendered.getSheet("S2").getCell(1, 1).value());
    }

    @Test
    void testMalformedMarkerThrowsParseError() {
        init();
        VKExcelSheet sheet = new VKExcelSheet("S1")
                .addCell(VKExcelCell.stringCell(1, 1, "{{#items as item}}"))
                .addCell(VKExcelCell.stringCell(2, 1, "{{item.name}}"));
        Vostok.Office.writeExcel("tpl/broken.xlsx", new VKExcelWorkbook().addSheet(sheet));

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.renderExcelTemplate(
                        "tpl/broken.xlsx",
                        "out/broken.xlsx",
                        Map.of("items", List.of()),
                        VKExcelTemplateOptions.defaults()));
        assertEquals(VKOfficeErrorCode.PARSE_ERROR, ex.getErrorCode());
    }

    private void writeLoopTemplate(String path, String loopStart) {
        VKExcelSheet sheet = new VKExcelSheet("Orders")
                .addCell(VKExcelCell.stringCell(1, 1, "HEAD"))
                .addCell(VKExcelCell.stringCell(2, 1, loopStart))
                .addCell(VKExcelCell.stringCell(3, 1, "{{item.name}}"))
                .addCell(VKExcelCell.stringCell(4, 1, "{{/items}}"))
                .addCell(VKExcelCell.stringCell(5, 1, "TAIL"));
        Vostok.Office.writeExcel(path, new VKExcelWorkbook().addSheet(sheet));
    }
}
