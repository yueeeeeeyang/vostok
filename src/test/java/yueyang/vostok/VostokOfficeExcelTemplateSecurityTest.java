package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeExcelTemplateSecurityTest {
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
    void testRejectFakeTemplateByMagic() {
        init();
        Vostok.File.write("tpl/fake.xlsx", "not-a-zip");

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.renderExcelTemplate(
                        "tpl/fake.xlsx",
                        "out/fake.xlsx",
                        Map.of(),
                        VKExcelTemplateOptions.defaults()));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    @Test
    void testRejectXxeTemplate() throws Exception {
        init();
        Path xlsx = tempDir.resolve("tpl/xxe.xlsx");
        Files.createDirectories(xlsx.getParent());
        createXxeWorkbook(xlsx);

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.renderExcelTemplate(
                        "tpl/xxe.xlsx",
                        "out/xxe.xlsx",
                        Map.of(),
                        VKExcelTemplateOptions.defaults()));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    @Test
    void testRejectNonXlsxPath() {
        init();
        Vostok.File.write("tpl/txt-template.txt", "x");
        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.renderExcelTemplate(
                        "tpl/txt-template.txt",
                        "out/out.xlsx",
                        Map.of(),
                        VKExcelTemplateOptions.defaults()));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    private void createXxeWorkbook(Path target) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            put(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                            "</Types>");
            put(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                            "</Relationships>");
            put(zos, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>" +
                            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                            "<sheets><sheet name=\"S1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zos, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                            "</Relationships>");
            put(zos, "xl/worksheets/sheet1.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData/></worksheet>");
        }
    }

    private void put(ZipOutputStream zos, String entry, String xml) throws IOException {
        zos.putNextEntry(new ZipEntry(entry));
        zos.write(xml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
