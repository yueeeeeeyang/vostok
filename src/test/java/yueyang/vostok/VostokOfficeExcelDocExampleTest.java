package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelReadOptions;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 对齐 docs/office.html 中的 Excel 示例。 */
public class VostokOfficeExcelDocExampleTest {
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

        VKExcelWorkbook workbook = new VKExcelWorkbook()
                .addSheet(new VKExcelSheet("Orders")
                        .addCell(VKExcelCell.stringCell(1, 1, "orderId"))
                        .addCell(VKExcelCell.stringCell(1, 2, "amount"))
                        .addCell(VKExcelCell.stringCell(2, 1, "A001"))
                        .addCell(VKExcelCell.numberCell(2, 2, "99.5")));

        Vostok.Office.writeExcel("excel/orders.xlsx", workbook);

        VKExcelWorkbook read = Vostok.Office.readExcel("excel/orders.xlsx");
        assertEquals("A001", read.getSheet("Orders").getCell(2, 1).value());

        AtomicInteger rows = new AtomicInteger();
        Vostok.Office.readExcelRows("excel/orders.xlsx", "Orders", VKExcelReadOptions.defaults(), row -> rows.incrementAndGet());
        assertEquals(2, rows.get());
    }
}
