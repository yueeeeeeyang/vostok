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

public class VostokOfficePPTSecurityTest {
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
    void testRejectFakePPTByMagic() {
        init();
        Vostok.File.write("ppt/fake.pptx", "not-a-zip");

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readPPTText("ppt/fake.pptx"));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    @Test
    void testRejectXxePPT() throws Exception {
        init();

        Path pptx = tempDir.resolve("ppt/malicious.pptx");
        Files.createDirectories(pptx.getParent());
        createXxePpt(pptx);

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readPPTText("ppt/malicious.pptx"));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    private void createXxePpt(Path target) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            put(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                            "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>" +
                            "<Override PartName=\"/ppt/slides/slide1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>" +
                            "</Types>");
            put(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>" +
                            "</Relationships>");
            put(zos, "ppt/presentation.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<p:presentation xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">" +
                            "<p:sldIdLst><p:sldId id=\"256\" r:id=\"rId1\"/></p:sldIdLst>" +
                            "</p:presentation>");
            put(zos, "ppt/_rels/presentation.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide1.xml\"/>" +
                            "</Relationships>");
            put(zos, "ppt/slides/slide1.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                            "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>" +
                            "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">" +
                            "<p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>&xxe;</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>");
        }
    }

    private void put(ZipOutputStream zos, String entry, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(entry));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
