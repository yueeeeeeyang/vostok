package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.ppt.VKPptWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficePPTWriteTest {
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
    void testWritePPTWithBytesAndFilePath() {
        init();

        Vostok.File.writeBytes("images/a.png", onePixelPng());

        VKPptWriteRequest request = new VKPptWriteRequest();
        request.addSlide().addParagraph("hello").addImageBytes("in.png", onePixelPng()).addImageFile("images/a.png");

        Vostok.Office.writePPT("ppt/write.pptx", request);
        assertEquals(1, Vostok.Office.countPPTSlides("ppt/write.pptx"));
        assertEquals(2, Vostok.Office.countPPTImages("ppt/write.pptx"));
    }

    @Test
    void testWritePPTUnsupportedFormat() {
        init();
        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.writePPT("ppt/a.ppt", new VKPptWriteRequest()));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
