package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.template.VKOfficeTemplateData;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 对齐 docs/office.html 中的 Excel 模板示例。 */
public class VostokOfficeExcelTemplateDocExampleTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.Office.close();
        Vostok.File.close();
    }

    @Test
    void testDocExample() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        Vostok.Office.init(new VKOfficeConfig());

        VKExcelWorkbook tpl = new VKExcelWorkbook().addSheet(new VKExcelSheet("Orders")
                .addCell(VKExcelCell.stringCell(1, 1, "订单号"))
                .addCell(VKExcelCell.stringCell(1, 2, "{{orderNo}}"))
                .addCell(VKExcelCell.stringCell(2, 1, "{{#items as item keepPlaceholderRows=false}}"))
                .addCell(VKExcelCell.stringCell(3, 1, "{{item.name}}"))
                .addCell(VKExcelCell.stringCell(3, 2, "{{item.qty}}"))
                .addCell(VKExcelCell.stringCell(3, 3, "{{item.amount}}"))
                .addCell(VKExcelCell.stringCell(4, 1, "{{/items}}"))
                .addCell(VKExcelCell.stringCell(5, 1, "{{?vip}}"))
                .addCell(VKExcelCell.stringCell(6, 1, "VIP折扣"))
                .addCell(VKExcelCell.stringCell(6, 2, "{{vipDiscount}}"))
                .addCell(VKExcelCell.stringCell(7, 1, "{{/vip}}"))
                .addCell(VKExcelCell.stringCell(8, 1, "总计"))
                .addCell(VKExcelCell.stringCell(8, 2, "{{total}}")));
        Vostok.Office.writeExcel("tpl/order.xlsx", tpl);

        VKOfficeTemplateData data = VKOfficeTemplateData.create()
                .put("orderNo", "A20260304001")
                .put("items", List.of(
                        Map.of("name", "可乐", "qty", 2, "amount", "8.00"),
                        Map.of("name", "薯片", "qty", 1, "amount", "6.00")
                ))
                .put("vip", true)
                .put("vipDiscount", "2.00")
                .put("total", "12.00");
        Vostok.Office.renderExcelTemplate("tpl/order.xlsx", "out/order.xlsx", data);

        VKExcelSheet sheet = Vostok.Office.readExcel("out/order.xlsx").getSheet("Orders");
        assertEquals("A20260304001", sheet.getCell(1, 2).value());
        assertEquals("可乐", sheet.getCell(2, 1).value());
        assertEquals("2", sheet.getCell(2, 2).value());
        assertEquals("8.00", sheet.getCell(2, 3).value());
        assertEquals("薯片", sheet.getCell(3, 1).value());
        assertEquals("1", sheet.getCell(3, 2).value());
        assertEquals("6.00", sheet.getCell(3, 3).value());
        assertEquals("VIP折扣", sheet.getCell(4, 1).value());
        assertEquals("2.00", sheet.getCell(4, 2).value());
        assertEquals("总计", sheet.getCell(5, 1).value());
        assertEquals("12.00", sheet.getCell(5, 2).value());
        assertNull(sheet.getCell(6, 1));
    }
}
