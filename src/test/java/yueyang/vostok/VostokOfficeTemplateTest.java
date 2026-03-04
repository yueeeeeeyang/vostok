package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.template.VKOfficeTemplateData;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeTemplateTest {
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
    void testRenderWordTemplate() {
        init();
        Vostok.Office.writeWord("tpl/word.docx", new VKWordWriteRequest()
                .addParagraph("Hello {{name}}\n{{#items as item}}- {{item.name}} x {{item.qty}} = {{item.amount}}\n{{/items}}{{?vip}}VIP{{/vip}}"));
        VKOfficeTemplateData data = VKOfficeTemplateData.create()
                .put("name", "Tom")
                .put("items", List.of(
                        Map.of("name", "A", "qty", 2, "amount", "8.00"),
                        Map.of("name", "B", "qty", 1, "amount", "3.00")))
                .put("vip", true);
        Vostok.Office.renderWordTemplate("tpl/word.docx", "out/word.docx", data);
        String text = Vostok.Office.readWordText("out/word.docx");
        assertTrue(text.contains("Hello Tom"));
        assertTrue(text.contains("- A x 2 = 8.00"));
        assertTrue(text.contains("- B x 1 = 3.00"));
        assertTrue(text.contains("VIP"));
    }

    @Test
    void testRenderPPTAndPDFTemplate() {
        init();
        var pptReq = new yueyang.vostok.office.ppt.VKPptWriteRequest();
        pptReq.addSlide().addParagraph("PPT {{name}} {{?show}}OK{{/show}}");
        Vostok.Office.writePPT("tpl/t.pptx", pptReq);
        Vostok.Office.renderPPTTemplate("tpl/t.pptx", "out/t.pptx", Map.of("name", "N1", "show", true), null);
        assertTrue(Vostok.Office.readPPTText("out/t.pptx").contains("PPT N1 OK"));

        var pdfReq = new yueyang.vostok.office.pdf.VKPdfWriteRequest();
        pdfReq.addPage().addParagraph("PDF {{name}}");
        Vostok.Office.writePDF("tpl/t.pdf", pdfReq);
        Vostok.Office.renderPDFTemplate("tpl/t.pdf", "out/t.pdf", Map.of("name", "N2"), null);
        assertTrue(Vostok.Office.readPDFText("out/t.pdf").contains("PDF N2"));
    }
}
