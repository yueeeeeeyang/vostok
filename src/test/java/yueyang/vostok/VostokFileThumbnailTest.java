package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.VKThumbnailMode;
import yueyang.vostok.file.VKThumbnailOptions;
import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokFileThumbnailTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.File.close();
    }

    @Test
    void testThumbnailFit() throws Exception {
        init();
        createImage("img/src.png", 400, 200);

        byte[] bytes = Vostok.File.thumbnail("img/src.png",
                VKThumbnailOptions.builder(100, 100)
                        .mode(VKThumbnailMode.FIT)
                        .build());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(out);
        assertEquals(100, out.getWidth());
        assertEquals(50, out.getHeight());
    }

    @Test
    void testThumbnailFill() throws Exception {
        init();
        createImage("img/src.png", 400, 200);
        byte[] bytes = Vostok.File.thumbnail("img/src.png",
                VKThumbnailOptions.builder(100, 100)
                        .mode(VKThumbnailMode.FILL)
                        .build());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(out);
        assertEquals(100, out.getWidth());
        assertEquals(100, out.getHeight());
    }

    @Test
    void testThumbnailNoUpscale() throws Exception {
        init();
        createImage("img/small.png", 40, 20);
        byte[] bytes = Vostok.File.thumbnail("img/small.png",
                VKThumbnailOptions.builder(120, 120)
                        .upscale(false)
                        .mode(VKThumbnailMode.FIT)
                        .build());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(out);
        assertEquals(40, out.getWidth());
        assertEquals(20, out.getHeight());
    }

    @Test
    void testThumbnailToFile() throws Exception {
        init();
        createImage("img/src.png", 300, 180);
        Vostok.File.thumbnailTo("img/src.png", "thumb/out.jpg",
                VKThumbnailOptions.builder(96, 96)
                        .format("jpg")
                        .quality(0.8f)
                        .mode(VKThumbnailMode.FIT)
                        .build());
        assertTrue(Vostok.File.exists("thumb/out.jpg"));
        BufferedImage out = ImageIO.read(tempDir.resolve("thumb/out.jpg").toFile());
        assertNotNull(out);
    }

    @Test
    void testThumbnailUnsupportedInput() throws Exception {
        init();
        Vostok.File.write("img/not-image.txt", "hello");
        VKFileException ex = assertThrows(VKFileException.class, () ->
                Vostok.File.thumbnail("img/not-image.txt", VKThumbnailOptions.builder(64, 64).build()));
        assertEquals(VKFileErrorCode.UNSUPPORTED_IMAGE_FORMAT, ex.getErrorCode());
    }

    @Test
    void testThumbnailPixelLimit() throws Exception {
        init();
        createImage("img/large.png", 2000, 2000);
        VKFileException ex = assertThrows(VKFileException.class, () ->
                Vostok.File.thumbnail("img/large.png",
                        VKThumbnailOptions.builder(200, 200)
                                .maxInputPixels(1_000_000L)
                                .build()));
        assertEquals(VKFileErrorCode.IMAGE_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void testThumbnailUnsupportedOutputFormat() throws Exception {
        init();
        createImage("img/src.png", 200, 200);
        VKFileException ex = assertThrows(VKFileException.class, () ->
                Vostok.File.thumbnail("img/src.png",
                        VKThumbnailOptions.builder(80, 80)
                                .format("webp")
                                .build()));
        assertEquals(VKFileErrorCode.UNSUPPORTED_IMAGE_FORMAT, ex.getErrorCode());
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
    }

    private void createImage(String path, int w, int h) throws IOException {
        Path p = tempDir.resolve(path);
        Files.createDirectories(p.getParent());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.YELLOW);
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        ImageIO.write(img, "png", p.toFile());
    }
}
