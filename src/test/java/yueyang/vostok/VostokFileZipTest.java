package yueyang.vostok;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokFileZipTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.File.close();
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
    }

    @Test
    void testZipAndUnzipSingleFile() throws IOException {
        init();
        Vostok.File.write("notes/a.txt", "hello zip");

        Vostok.File.zip("notes/a.txt", "zip/a.zip");
        Vostok.File.unzip("zip/a.zip", "out");

        assertEquals("hello zip", Vostok.File.read("out/a.txt"));
    }

    @Test
    void testZipAndUnzipDirectory() throws IOException {
        init();
        Vostok.File.write("docs/readme.txt", "r1");
        Vostok.File.write("docs/api/v1.txt", "v1");
        Vostok.File.mkdirs("docs/empty");

        Vostok.File.zip("docs", "zip/docs.zip");
        Vostok.File.unzip("zip/docs.zip", "restore");

        assertEquals("r1", Vostok.File.read("restore/docs/readme.txt"));
        assertEquals("v1", Vostok.File.read("restore/docs/api/v1.txt"));
        assertTrue(Files.isDirectory(tempDir.resolve("restore/docs/empty")));
    }

    @Test
    void testUnzipReplacePolicy() throws IOException {
        init();
        Vostok.File.write("data/a.txt", "v1");
        Vostok.File.zip("data/a.txt", "zip/a.zip");
        Vostok.File.unzip("zip/a.zip", "out");

        Vostok.File.write("out/a.txt", "modified");
        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.unzip("zip/a.zip", "out", false));
        assertEquals(VKFileErrorCode.IO_ERROR, ex.getErrorCode());

        Vostok.File.unzip("zip/a.zip", "out", true);
        assertEquals("v1", Vostok.File.read("out/a.txt"));
    }

    @Test
    void testZipSlipProtection() throws IOException {
        init();
        Path zip = tempDir.resolve("zip/malicious.zip");
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("x".getBytes());
            zos.closeEntry();
        }

        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.unzip("zip/malicious.zip", "out"));
        assertEquals(VKFileErrorCode.SECURITY_ERROR, ex.getErrorCode());
        assertTrue(!Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    void testZipAndUnzipLargeFileByStreaming() throws IOException {
        init();
        byte[] data = new byte[5 * 1024 * 1024];
        new Random(42).nextBytes(data);
        Path source = tempDir.resolve("bin/large.bin");
        Files.createDirectories(source.getParent());
        Files.write(source, data);

        Vostok.File.zip("bin/large.bin", "zip/large.zip");
        Vostok.File.unzip("zip/large.zip", "restore");

        byte[] restored = Files.readAllBytes(tempDir.resolve("restore/large.bin"));
        assertArrayEquals(data, restored);
    }
}
