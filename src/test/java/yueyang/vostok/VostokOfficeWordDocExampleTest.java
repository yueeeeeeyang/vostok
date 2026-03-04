package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.word.VKWordImageLoadMode;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 对齐 docs/office.html 中的 Word 示例。 */
public class VostokOfficeWordDocExampleTest {
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

        VKWordWriteRequest request = new VKWordWriteRequest()
                .addParagraph("订单 A001")
                .addImageBytes("order.png", onePixelPng());

        Vostok.Office.writeWord("word/orders.docx", request);

        assertEquals(1, Vostok.Office.countWordImages("word/orders.docx"));
        assertEquals(6, Vostok.Office.countWordChars("word/orders.docx"));

        VKWordReadOptions metadataOnly = VKWordReadOptions.defaults()
                .imageLoadMode(VKWordImageLoadMode.METADATA_ONLY);
        assertEquals(1, Vostok.Office.readWordImages("word/orders.docx", metadataOnly).size());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
