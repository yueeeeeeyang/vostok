package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.word.VKWordDocument;
import yueyang.vostok.office.word.VKWordImage;
import yueyang.vostok.office.word.VKWordImageLoadMode;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeWordReadTest {
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
    void testReadWordAndCountCharsAndImages() {
        init();

        VKWordWriteRequest request = new VKWordWriteRequest()
                .addParagraph("A B\nC")
                .addParagraph("中 文")
                .addImageBytes("p1.png", onePixelPng());

        Vostok.Office.writeWord("word/demo.docx", request);

        String text = Vostok.Office.readWordText("word/demo.docx");
        assertTrue(text.contains("A B"));
        assertTrue(text.contains("中 文"));

        int chars = Vostok.Office.countWordChars("word/demo.docx");
        assertEquals(5, chars);

        int imageCount = Vostok.Office.countWordImages("word/demo.docx");
        assertEquals(1, imageCount);

        VKWordDocument doc = Vostok.Office.readWord("word/demo.docx");
        assertNotNull(doc);
        assertEquals(5, doc.charCount());
        assertEquals(1, doc.imageCount());
        assertEquals(1, doc.images().size());
    }

    @Test
    void testReadWordImagesMetadataOnly() {
        init();

        VKWordWriteRequest request = new VKWordWriteRequest()
                .addParagraph("meta only")
                .addImageBytes("p1.png", onePixelPng())
                .addImageBytes("p2.jpg", onePixelJpeg());

        Vostok.Office.writeWord("word/meta.docx", request);

        VKWordReadOptions options = VKWordReadOptions.defaults()
                .imageLoadMode(VKWordImageLoadMode.METADATA_ONLY);

        List<VKWordImage> images = Vostok.Office.readWordImages("word/meta.docx", options);
        assertEquals(2, images.size());
        for (VKWordImage image : images) {
            assertNull(image.bytes());
            assertTrue(image.size() > 0);
            assertTrue(image.mediaPath().startsWith("word/media/"));
        }

        VKWordDocument doc = Vostok.Office.readWord("word/meta.docx", options);
        assertEquals(2, doc.imageCount());
        assertNull(doc.images().get(0).bytes());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }

    private byte[] onePixelJpeg() {
        return Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxAQEBAQEA8PEA8QDw8QEA8QEA8QDxAPFREWFhURFRUYHSggGBolGxUVITEhJSkrLi4uFx8zODMsNygtLisBCgoKDQ0NDw0NDisZFRkrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrK//AABEIAAEAAQMBIgACEQEDEQH/xAAXAAADAQAAAAAAAAAAAAAAAAAAAQMC/8QAFhABAQEAAAAAAAAAAAAAAAAAAAEC/9oADAMBAAIQAxAAAAHqA//EABQQAQAAAAAAAAAAAAAAAAAAADD/2gAIAQEAAQUCq//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bp//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bp//Z");
    }
}
