package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelCellType;
import yueyang.vostok.office.excel.VKExcelReadOptions;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeExcelTest {
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
    void testWriteReadWorkbookRoundTrip() {
        init();

        VKExcelWorkbook workbook = new VKExcelWorkbook()
                .addSheet(new VKExcelSheet("SheetA")
                        .addCell(VKExcelCell.stringCell(1, 1, "name"))
                        .addCell(VKExcelCell.numberCell(1, 2, "100"))
                        .addCell(VKExcelCell.booleanCell(1, 3, true))
                        .addCell(VKExcelCell.formulaCell(2, 1, "SUM(B1:B1)", "100"))
                        .setCell(3, 2, VKExcelCellType.BLANK, null))
                .addSheet(new VKExcelSheet("SheetB")
                        .addCell(VKExcelCell.stringCell(1, 1, "hello")));

        Vostok.Office.writeExcel("excel/demo.xlsx", workbook);
        VKExcelWorkbook read = Vostok.Office.readExcel("excel/demo.xlsx");

        assertEquals(2, read.sheets().size());

        VKExcelSheet s1 = read.getSheet("SheetA");
        assertNotNull(s1);
        assertEquals("name", s1.getCell(1, 1).value());
        assertEquals("100", s1.getCell(1, 2).value());
        assertEquals("1", s1.getCell(1, 3).value());
        assertEquals("SUM(B1:B1)", s1.getCell(2, 1).formula());
        assertEquals("100", s1.getCell(2, 1).value());

        VKExcelSheet s2 = read.getSheet("SheetB");
        assertNotNull(s2);
        assertEquals("hello", s2.getCell(1, 1).value());
    }

    @Test
    void testReadExcelRowsStream() {
        init();

        VKExcelSheet sheet = new VKExcelSheet("Rows");
        for (int i = 1; i <= 200; i++) {
            sheet.addCell(VKExcelCell.stringCell(i, 1, "r" + i));
            sheet.addCell(VKExcelCell.numberCell(i, 2, String.valueOf(i)));
        }
        Vostok.Office.writeExcel("excel/rows.xlsx", new VKExcelWorkbook().addSheet(sheet));

        AtomicInteger count = new AtomicInteger();
        AtomicInteger lastRow = new AtomicInteger();
        Vostok.Office.readExcelRows("excel/rows.xlsx", "Rows", VKExcelReadOptions.defaults(), row -> {
            count.incrementAndGet();
            lastRow.set(row.rowIndex());
            assertTrue(row.cells().containsKey(1));
        });

        assertEquals(200, count.get());
        assertEquals(200, lastRow.get());
    }

    @Test
    void testWriteExcelBlockedByReadOnly() {
        init();
        Vostok.File.setReadOnly(true);
        VKExcelWorkbook workbook = new VKExcelWorkbook().addSheet(new VKExcelSheet("S")
                .addCell(VKExcelCell.stringCell(1, 1, "x")));

        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.Office.writeExcel("excel/ro.xlsx", workbook));
        assertEquals(VKFileErrorCode.READ_ONLY_ERROR, ex.getErrorCode());
    }
}
