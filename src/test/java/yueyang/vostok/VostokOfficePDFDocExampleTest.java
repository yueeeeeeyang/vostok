package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.pdf.VKPdfImageLoadMode;
import yueyang.vostok.office.pdf.VKPdfReadOptions;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 对齐 docs/office.html 中的 PDF 示例。 */
public class VostokOfficePDFDocExampleTest {
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

        VKPdfWriteRequest req = new VKPdfWriteRequest();
        req.addPage().addParagraph("账单 A001").addImageBytes("logo.png", onePixelPng());
        Vostok.Office.writePDF("pdf/bill.pdf", req);

        assertEquals(1, Vostok.Office.countPDFPages("pdf/bill.pdf"));
        assertEquals(1, Vostok.Office.countPDFImages("pdf/bill.pdf"));

        VKPdfReadOptions metadataOnly = VKPdfReadOptions.defaults().imageLoadMode(VKPdfImageLoadMode.METADATA_ONLY);
        assertEquals(1, Vostok.Office.readPDFImages("pdf/bill.pdf", metadataOnly).size());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
