package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.pdf.VKPdfReadOptions;
import yueyang.vostok.office.pdf.VKPdfWriteOptions;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficePDFLargeFileTest {
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
    void testCountPDFCharsWithLargeText() {
        init();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("ab");
        }

        VKPdfWriteRequest request = new VKPdfWriteRequest();
        request.addPage().addParagraph(sb.toString());
        Vostok.Office.writePDF("pdf/large.pdf", request);
        assertEquals(20000, Vostok.Office.countPDFChars("pdf/large.pdf"));
    }

    @Test
    void testCountPDFCharsLimitExceeded() {
        init();

        VKPdfWriteRequest request = new VKPdfWriteRequest();
        request.addPage().addParagraph("abcdefghij");
        Vostok.Office.writePDF("pdf/limit.pdf", request);

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.countPDFChars("pdf/limit.pdf", VKPdfReadOptions.defaults().maxTextChars(5)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void testWritePDFPageLimitExceeded() {
        init();

        VKPdfWriteRequest request = new VKPdfWriteRequest();
        request.addPage().addParagraph("1");
        request.addPage().addParagraph("2");

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.writePDF("pdf/p.pdf", request, VKPdfWriteOptions.defaults().maxPages(1)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
