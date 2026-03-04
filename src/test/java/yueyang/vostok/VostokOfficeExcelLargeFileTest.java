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
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeExcelLargeFileTest {
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
    void testReadRowsLargeWorkbookByStream() {
        init();
        VKExcelSheet sheet = new VKExcelSheet("Large");
        for (int i = 1; i <= 5000; i++) {
            sheet.addCell(VKExcelCell.stringCell(i, 1, "name-" + i));
            sheet.addCell(VKExcelCell.numberCell(i, 2, String.valueOf(i)));
        }
        Vostok.Office.writeExcel("excel/large.xlsx", new VKExcelWorkbook().addSheet(sheet));

        AtomicInteger rows = new AtomicInteger();
        Vostok.Office.readExcelRows("excel/large.xlsx", "Large", VKExcelReadOptions.defaults(), row -> rows.incrementAndGet());
        assertEquals(5000, rows.get());
    }

    @Test
    void testReadLimitExceeded() {
        init();
        VKExcelSheet sheet = new VKExcelSheet("Limit");
        for (int i = 1; i <= 120; i++) {
            sheet.addCell(VKExcelCell.stringCell(i, 1, "v" + i));
        }
        Vostok.Office.writeExcel("excel/limit.xlsx", new VKExcelWorkbook().addSheet(sheet));

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readExcel("excel/limit.xlsx",
                        VKExcelReadOptions.defaults().maxRowsPerSheet(100)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
