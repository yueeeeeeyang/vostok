package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficePDFWriteTest {
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
    void testWritePDFWithBytesAndFilePath() {
        init();

        Vostok.File.writeBytes("images/a.png", onePixelPng());

        VKPdfWriteRequest request = new VKPdfWriteRequest();
        request.addPage().addParagraph("hello").addImageBytes("x.png", onePixelPng()).addImageFile("images/a.png");

        Vostok.Office.writePDF("pdf/write.pdf", request);
        assertEquals(1, Vostok.Office.countPDFPages("pdf/write.pdf"));
        assertEquals(2, Vostok.Office.countPDFImages("pdf/write.pdf"));
    }

    @Test
    void testWritePDFUnsupportedFormat() {
        init();

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.writePDF("pdf/a.doc", new VKPdfWriteRequest()));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
