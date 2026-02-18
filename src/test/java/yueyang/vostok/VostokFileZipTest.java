package yueyang.vostok;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
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

    @Test
    void testZipAndUnzipSingleFile() throws IOException {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.write("notes/a.txt", "hello zip");

        Vostok.File.zip("notes/a.txt", "zip/a.zip");
        Vostok.File.unzip("zip/a.zip", "out");

        assertEquals("hello zip", Vostok.File.read("out/a.txt"));
    }

    @Test
    void testZipAndUnzipDirectory() throws IOException {
        Vostok.File.initLocal(tempDir.toString());
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
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.write("data/a.txt", "v1");
        Vostok.File.zip("data/a.txt", "zip/a.zip");
        Vostok.File.unzip("zip/a.zip", "out");

        Vostok.File.write("out/a.txt", "modified");
        assertThrows(UncheckedIOException.class, () -> Vostok.File.unzip("zip/a.zip", "out", false));

        Vostok.File.unzip("zip/a.zip", "out", true);
        assertEquals("v1", Vostok.File.read("out/a.txt"));
    }

    @Test
    void testZipSlipProtection() throws IOException {
        Vostok.File.initLocal(tempDir.toString());
        Path zip = tempDir.resolve("zip/malicious.zip");
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("x".getBytes());
            zos.closeEntry();
        }

        assertThrows(IllegalArgumentException.class, () -> Vostok.File.unzip("zip/malicious.zip", "out"));
        assertTrue(!Files.exists(tempDir.resolve("evil.txt")));
    }

    @Test
    void testZipAndUnzipLargeFileByStreaming() throws IOException {
        Vostok.File.initLocal(tempDir.toString());
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
