package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteOptions;
import yueyang.vostok.office.word.VKWordWriteRequest;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficeWordLargeFileTest {
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
    void testCountCharsWithLargeText() {
        init();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            sb.append("ab");
        }

        Vostok.Office.writeWord("word/large.docx", new VKWordWriteRequest().addParagraph(sb.toString()));
        int chars = Vostok.Office.countWordChars("word/large.docx");
        assertEquals(20_000, chars);
    }

    @Test
    void testCountCharsLimitExceeded() {
        init();

        Vostok.Office.writeWord("word/limit.docx", new VKWordWriteRequest().addParagraph("abcdefghij"));

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.countWordChars("word/limit.docx", VKWordReadOptions.defaults().maxTextChars(5)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void testWriteImageBytesLimitExceeded() {
        init();

        VKWordWriteRequest request = new VKWordWriteRequest()
                .addParagraph("img")
                .addImageBytes("p.png", onePixelPng());

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.writeWord("word/img-limit.docx", request,
                        VKWordWriteOptions.defaults().maxSingleImageBytes(10)));
        assertEquals(VKOfficeErrorCode.LIMIT_EXCEEDED, ex.getErrorCode());
    }

    private byte[] onePixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7mR4kAAAAASUVORK5CYII=");
    }
}
