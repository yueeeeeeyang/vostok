package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.pdf.VKPdfDocument;
import yueyang.vostok.office.pdf.VKPdfImage;
import yueyang.vostok.office.pdf.VKPdfImageLoadMode;
import yueyang.vostok.office.pdf.VKPdfReadOptions;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficePDFReadTest {
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
    void testReadPDFAndCount() {
        init();

        VKPdfWriteRequest request = new VKPdfWriteRequest();
        request.addPage().addParagraph("Hello PDF").addImageBytes("a.png", onePixelPng());
        request.addPage().addParagraph("第二页");

        Vostok.Office.writePDF("pdf/demo.pdf", request);

        String text = Vostok.Office.readPDFText("pdf/demo.pdf");
        assertTrue(text.contains("Hello PDF"));

        assertEquals(11, Vostok.Office.countPDFChars("pdf/demo.pdf"));
        assertEquals(1, Vostok.Office.countPDFImages("pdf/demo.pdf"));
        assertEquals(2, Vostok.Office.countPDFPages("pdf/demo.pdf"));

        VKPdfDocument doc = Vostok.Office.readPDF("pdf/demo.pdf");
        assertEquals(2, doc.pageCount());
        assertEquals(1, doc.imageCount());
        assertEquals(11, doc.charCount());
    }

    @Test
    void testReadPDFImagesMetadataOnly() {
        init();

        VKPdfWriteRequest request = new VKPdfWriteRequest();
        request.addPage().addParagraph("meta").addImageBytes("a.png", onePixelPng()).addImageBytes("b.jpg", onePixelJpeg());
        Vostok.Office.writePDF("pdf/meta.pdf", request);

        VKPdfReadOptions options = VKPdfReadOptions.defaults().imageLoadMode(VKPdfImageLoadMode.METADATA_ONLY);
        List<VKPdfImage> images = Vostok.Office.readPDFImages("pdf/meta.pdf", options);
        assertEquals(2, images.size());
        assertNull(images.get(0).bytes());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }

    private byte[] onePixelJpeg() {
        return Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxAQEBAQEA8PEA8QDw8QEA8QEA8QDxAPFREWFhURFRUYHSggGBolGxUVITEhJSkrLi4uFx8zODMsNygtLisBCgoKDQ0NDw0NDisZFRkrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrKysrK//AABEIAAEAAQMBIgACEQEDEQH/xAAXAAADAQAAAAAAAAAAAAAAAAAAAQMC/8QAFhABAQEAAAAAAAAAAAAAAAAAAAEC/9oADAMBAAIQAxAAAAHqA//EABQQAQAAAAAAAAAAAAAAAAAAADD/2gAIAQEAAQUCq//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8Bp//EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQIBAT8Bp//Z");
    }
}
