package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.ppt.VKPptImageLoadMode;
import yueyang.vostok.office.ppt.VKPptReadOptions;
import yueyang.vostok.office.ppt.VKPptWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 对齐 docs/office.html 中的 PPT 示例。 */
public class VostokOfficePPTDocExampleTest {
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

        VKPptWriteRequest req = new VKPptWriteRequest();
        req.addSlide().addParagraph("季度总结 Q1").addImageBytes("chart.png", onePixelPng());
        Vostok.Office.writePPT("ppt/summary.pptx", req);

        assertEquals(1, Vostok.Office.countPPTSlides("ppt/summary.pptx"));
        assertEquals(1, Vostok.Office.countPPTImages("ppt/summary.pptx"));

        VKPptReadOptions metadataOnly = VKPptReadOptions.defaults().imageLoadMode(VKPptImageLoadMode.METADATA_ONLY);
        assertEquals(1, Vostok.Office.readPPTImages("ppt/summary.pptx", metadataOnly).size());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
