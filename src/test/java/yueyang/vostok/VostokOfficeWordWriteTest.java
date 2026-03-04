package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.word.VKWordImage;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeWordWriteTest {
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
    void testWriteWordWithBytesAndFilePath() {
        init();

        Vostok.File.writeBytes("images/from-file.png", onePixelPng());

        VKWordWriteRequest request = new VKWordWriteRequest()
                .addParagraph("hello word")
                .addImageBytes("inline.png", onePixelPng())
                .addImageFile("images/from-file.png");

        Vostok.Office.writeWord("word/write.docx", request);

        List<VKWordImage> images = Vostok.Office.readWordImages("word/write.docx");
        assertEquals(2, images.size());
        assertNotNull(images.get(0).bytes());
        assertNotNull(images.get(1).bytes());
        assertEquals(2, Vostok.Office.countWordImages("word/write.docx"));
    }

    @Test
    void testWriteWordUnsupportedFormat() {
        init();

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.writeWord("word/demo.doc", new VKWordWriteRequest().addParagraph("x")));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }

    @Test
    void testWriteWordBlockedByReadOnly() {
        init();
        Vostok.File.setReadOnly(true);

        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.Office.writeWord("word/ro.docx", new VKWordWriteRequest().addParagraph("x")));
        assertEquals(VKFileErrorCode.READ_ONLY_ERROR, ex.getErrorCode());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
