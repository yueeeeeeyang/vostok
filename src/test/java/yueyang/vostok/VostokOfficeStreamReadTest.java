package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;
import yueyang.vostok.office.pdf.stream.VKPdfStreamBlock;
import yueyang.vostok.office.ppt.VKPptWriteRequest;
import yueyang.vostok.office.ppt.stream.VKPptStreamBlock;
import yueyang.vostok.office.word.VKWordWriteRequest;
import yueyang.vostok.office.word.stream.VKWordStreamBlock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeStreamReadTest {
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
    void testReadStreams() {
        init();
        byte[] png = onePixelPng();

        Vostok.Office.writeWord("w/a.docx", new VKWordWriteRequest().addParagraph("w1").addImageBytes("a.png", png));
        List<VKWordStreamBlock> wordBlocks = new ArrayList<>();
        Vostok.Office.readWordStream("w/a.docx", wordBlocks::add);
        assertTrue(wordBlocks.stream().anyMatch(b -> b.text() != null && b.text().contains("w1")));
        assertTrue(wordBlocks.stream().anyMatch(b -> b.image() != null));

        VKPptWriteRequest ppt = new VKPptWriteRequest();
        ppt.addSlide().addParagraph("p1").addImageBytes("a.png", png);
        Vostok.Office.writePPT("p/a.pptx", ppt);
        List<VKPptStreamBlock> pptBlocks = new ArrayList<>();
        Vostok.Office.readPPTStream("p/a.pptx", pptBlocks::add);
        assertTrue(pptBlocks.stream().anyMatch(b -> b.text() != null && b.text().contains("p1")));
        assertTrue(pptBlocks.stream().anyMatch(b -> b.image() != null));

        VKPdfWriteRequest pdf = new VKPdfWriteRequest();
        pdf.addPage().addParagraph("d1").addImageBytes("a.png", png);
        Vostok.Office.writePDF("d/a.pdf", pdf);
        List<VKPdfStreamBlock> pdfBlocks = new ArrayList<>();
        Vostok.Office.readPDFStream("d/a.pdf", pdfBlocks::add);
        assertTrue(pdfBlocks.stream().anyMatch(b -> b.text() != null && b.text().contains("d1")));
        assertTrue(pdfBlocks.stream().anyMatch(b -> b.image() != null));
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
