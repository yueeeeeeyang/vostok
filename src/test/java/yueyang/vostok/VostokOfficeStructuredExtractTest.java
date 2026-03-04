package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;
import yueyang.vostok.office.ppt.VKPptWriteRequest;
import yueyang.vostok.office.word.VKWordWriteRequest;
import yueyang.vostok.office.word.structured.VKWordStructuredNodeType;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeStructuredExtractTest {
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
    void testStructuredExtract() {
        init();
        byte[] png = onePixelPng();

        Vostok.Office.writeWord("w/a.docx", new VKWordWriteRequest().addParagraph("w1").addImageBytes("a.png", png));
        var w = Vostok.Office.readWordStructured("w/a.docx");
        assertFalse(w.nodes().isEmpty());
        assertTrue(w.nodes().get(0).children().stream().anyMatch(n -> n.type() == VKWordStructuredNodeType.PARAGRAPH));

        VKPptWriteRequest ppt = new VKPptWriteRequest();
        ppt.addSlide().addParagraph("p1").addImageBytes("a.png", png);
        Vostok.Office.writePPT("p/a.pptx", ppt);
        var p = Vostok.Office.readPPTStructured("p/a.pptx");
        assertFalse(p.nodes().isEmpty());
        assertTrue(p.nodes().stream().anyMatch(n -> !n.children().isEmpty()));

        VKPdfWriteRequest pdf = new VKPdfWriteRequest();
        pdf.addPage().addParagraph("d1").addImageBytes("a.png", png);
        Vostok.Office.writePDF("d/a.pdf", pdf);
        var d = Vostok.Office.readPDFStructured("d/a.pdf");
        assertFalse(d.nodes().isEmpty());
        assertTrue(d.nodes().stream().anyMatch(n -> !n.children().isEmpty()));
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
