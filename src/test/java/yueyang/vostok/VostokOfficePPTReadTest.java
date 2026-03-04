package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.ppt.VKPptDocument;
import yueyang.vostok.office.ppt.VKPptImage;
import yueyang.vostok.office.ppt.VKPptImageLoadMode;
import yueyang.vostok.office.ppt.VKPptReadOptions;
import yueyang.vostok.office.ppt.VKPptWriteRequest;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficePPTReadTest {
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
    void testReadPPTAndCount() {
        init();

        VKPptWriteRequest request = new VKPptWriteRequest();
        request.addSlide().addParagraph("第一页 A").addImageBytes("p1.png", onePixelPng());
        request.addSlide().addParagraph("第二页 B");

        Vostok.Office.writePPT("ppt/demo.pptx", request);

        String text = Vostok.Office.readPPTText("ppt/demo.pptx");
        assertTrue(text.contains("第一页"));
        assertTrue(text.contains("第二页"));

        assertEquals(8, Vostok.Office.countPPTChars("ppt/demo.pptx"));
        assertEquals(1, Vostok.Office.countPPTImages("ppt/demo.pptx"));
        assertEquals(2, Vostok.Office.countPPTSlides("ppt/demo.pptx"));

        VKPptDocument doc = Vostok.Office.readPPT("ppt/demo.pptx");
        assertEquals(2, doc.slideCount());
        assertEquals(1, doc.imageCount());
        assertEquals(8, doc.charCount());
    }

    @Test
    void testReadPPTImagesMetadataOnly() {
        init();

        VKPptWriteRequest request = new VKPptWriteRequest();
        request.addSlide().addParagraph("meta").addImageBytes("a.png", onePixelPng()).addImageBytes("b.jpg", onePixelJpeg());
        Vostok.Office.writePPT("ppt/meta.pptx", request);

        VKPptReadOptions options = VKPptReadOptions.defaults().imageLoadMode(VKPptImageLoadMode.METADATA_ONLY);
        List<VKPptImage> images = Vostok.Office.readPPTImages("ppt/meta.pptx", options);
        assertEquals(2, images.size());
        assertNull(images.get(0).bytes());
        assertTrue(images.get(0).mediaPath().startsWith("ppt/media/"));
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }

    private byte[] onePixelJpeg() {
        return Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxAQEBAQEA8PEA8QDw8QEA8QEA8QDxAPFREWFhURFRUYHSggGBolGxUVITEhJSkrLi4uFx8zODMsNygtLisBCgoKDQ0NDw0NDisZFRkrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrK//AABEIAAEAAQMBIgACEQEDEQH/xAAXAAADAQAAAAAAAAAAAAAAAAAAAQMC/8QAFhABAQEAAAAAAAAAAAAAAAAAAAEC/9oADAMBAAIQAxAAAAHqA//EABQQAQAAAAAAAAAAAAAAAAAAADD/2gAIAQEAAQUCq//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bp//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bp//Z");
    }
}
