package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.convert.VKOfficeConvertOptions;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeConvertTest {
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
    void testDocxToPdfConvert() {
        init();
        Vostok.Office.writeWord("in/a.docx", new VKWordWriteRequest().addParagraph("hello convert"));
        Vostok.Office.convertToPDF("in/a.docx", "out/a.pdf");
        assertTrue(Vostok.Office.readPDFText("out/a.pdf").contains("hello convert"));
    }

    @Test
    void testExcelCsvConvert() {
        init();
        VKExcelWorkbook wb = new VKExcelWorkbook().addSheet(new VKExcelSheet("Orders")
                .addCell(VKExcelCell.stringCell(1, 1, "id"))
                .addCell(VKExcelCell.stringCell(1, 2, "name"))
                .addCell(VKExcelCell.stringCell(2, 1, "1"))
                .addCell(VKExcelCell.stringCell(2, 2, "tom")));
        Vostok.Office.writeExcel("in/a.xlsx", wb);
        Vostok.Office.convertExcelToCSV("in/a.xlsx", "out/a.csv", VKOfficeConvertOptions.defaults().csvSheetName("Orders"));
        String csv = Vostok.File.read("out/a.csv");
        assertTrue(csv.contains("id,name"));
        assertTrue(csv.contains("1,tom"));

        Vostok.Office.convertCSVToExcel("out/a.csv", "out/back.xlsx", VKOfficeConvertOptions.defaults().csvSheetName("Back"));
        VKExcelWorkbook back = Vostok.Office.readExcel("out/back.xlsx");
        assertEquals("id", back.getSheet("Back").getCell(1, 1).value());
    }

    @Test
    void testUnsupportedConvert() {
        init();
        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.convertToPDF("in/a.txt", "out/a.pdf"));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }
}
