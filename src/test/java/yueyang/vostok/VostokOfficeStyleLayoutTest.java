package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;
import yueyang.vostok.office.ppt.VKPptWriteRequest;
import yueyang.vostok.office.style.VKOfficeImageStyle;
import yueyang.vostok.office.style.VKOfficeLayoutStyle;
import yueyang.vostok.office.style.VKOfficeTextAlign;
import yueyang.vostok.office.style.VKOfficeTextStyle;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeStyleLayoutTest {
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
    void testStyledWrite() {
        init();
        VKOfficeTextStyle textStyle = VKOfficeTextStyle.create()
                .fontSize(18).bold(true).italic(true).colorHex("#336699").align(VKOfficeTextAlign.CENTER);
        VKOfficeImageStyle imageStyle = VKOfficeImageStyle.create().width(120).height(80);
        VKOfficeLayoutStyle layout = VKOfficeLayoutStyle.create().marginLeft(60.0).marginTop(60.0).blockSpacing(20.0);

        Vostok.Office.writeWord("w/a.docx", new VKWordWriteRequest()
                .layoutStyle(layout)
                .addParagraph("styled-word", textStyle)
                .addImageBytes("a.png", onePixelPng(), imageStyle));
        assertTrue(Vostok.Office.readWordText("w/a.docx").contains("styled-word"));

        VKPptWriteRequest ppt = new VKPptWriteRequest().layoutStyle(layout);
        ppt.addSlide().addParagraph("styled-ppt", textStyle).addImageBytes("a.png", onePixelPng(), imageStyle);
        Vostok.Office.writePPT("p/a.pptx", ppt);
        assertTrue(Vostok.Office.readPPTText("p/a.pptx").contains("styled-ppt"));

        VKPdfWriteRequest pdf = new VKPdfWriteRequest().layoutStyle(layout);
        pdf.addPage().addParagraph("styled-pdf", textStyle).addImageBytes("a.png", onePixelPng(), imageStyle);
        Vostok.Office.writePDF("d/a.pdf", pdf);
        assertTrue(Vostok.Office.readPDFText("d/a.pdf").contains("styled-pdf"));
        assertEquals(1, Vostok.Office.countPDFImages("d/a.pdf"));
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
