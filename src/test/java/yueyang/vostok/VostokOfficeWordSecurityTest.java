package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeWordSecurityTest {
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
    void testRejectFakeDocxByMagic() {
        init();
        Vostok.File.write("word/fake.docx", "not-a-zip");

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readWordText("word/fake.docx"));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    @Test
    void testRejectXxeDocx() throws Exception {
        init();

        Path docx = tempDir.resolve("word/malicious.docx");
        Files.createDirectories(docx.getParent());
        createXxeWord(docx);

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readWordText("word/malicious.docx"));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    @Test
    void testRejectDocFormat() {
        init();
        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readWordText("word/legacy.doc"));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    private void createXxeWord(Path target) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            put(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                            "</Types>");
            put(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                            "</Relationships>");
            put(zos, "word/document.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>" +
                            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                            "<w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body></w:document>");
        }
    }

    private void put(ZipOutputStream zos, String entry, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(entry));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
