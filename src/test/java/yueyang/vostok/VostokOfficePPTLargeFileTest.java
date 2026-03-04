package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.ppt.VKPptReadOptions;
import yueyang.vostok.office.ppt.VKPptWriteOptions;
import yueyang.vostok.office.ppt.VKPptWriteRequest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficePPTLargeFileTest {
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
    void testCountPPTCharsWithLargeText() {
        init();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append('a');
        }
        VKPptWriteRequest request = new VKPptWriteRequest();
        request.addSlide().addParagraph(sb.toString());
        Vostok.Office.writePPT("ppt/large.pptx", request);
        assertEquals(20000, Vostok.Office.countPPTChars("ppt/large.pptx"));
    }

    @Test
    void testCountPPTCharsLimitExceeded() {
        init();

        VKPptWriteRequest request = new VKPptWriteRequest();
        request.addSlide().addParagraph("abcdefghij");
        Vostok.Office.writePPT("ppt/limit.pptx", request);

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.countPPTChars("ppt/limit.pptx", VKPptReadOptions.defaults().maxTextChars(5)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void testWritePPTSlideLimitExceeded() {
        init();

        VKPptWriteRequest request = new VKPptWriteRequest();
        request.addSlide().addParagraph("1");
        request.addSlide().addParagraph("2");

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.writePPT("ppt/s.pptx", request, VKPptWriteOptions.defaults().maxSlides(1)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
