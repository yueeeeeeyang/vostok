package yueyang.vostok;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import yueyang.vostok.file.VKFileConflictStrategy;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.VKFileMigrateMode;
import yueyang.vostok.file.VKFileMigrateOptions;
import yueyang.vostok.file.VKFileMigrateProgressStatus;
import yueyang.vostok.file.VKFileMigrateResult;
import yueyang.vostok.file.VKUnzipOptions;
import yueyang.vostok.file.VKFileWatchEventType;
import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokFileAdvancedTest {
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
    void testHashAndMkdirTypeAndRename() {
        init();
        Vostok.File.mkdirs("a");
        Vostok.File.mkdir("a/b");
        Vostok.File.write("a/b/f.txt", "abc");

        String md5 = Vostok.File.hash("a/b/f.txt", "MD5");
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5);
        assertTrue(Vostok.File.isFile("a/b/f.txt"));
        assertTrue(Vostok.File.isDirectory("a/b"));
        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.hash("a/b/f.txt", "NO_SUCH"));
        assertEquals(VKFileErrorCode.UNSUPPORTED, ex.getErrorCode());

        Vostok.File.rename("a/b/f.txt", "g.txt");
        assertFalse(Vostok.File.exists("a/b/f.txt"));
        assertTrue(Vostok.File.exists("a/b/g.txt"));
    }

    @Test
    void testWalkWithFilter() {
        init();
        Vostok.File.write("w/x.txt", "1");
        Vostok.File.write("w/y.log", "2");
        Vostok.File.write("w/sub/z.txt", "3");

        List<String> txt = Vostok.File.walk("w", true, f -> !f.directory() && f.path().endsWith(".txt"))
                .stream().map(i -> i.path().replace('\\', '/')).sorted().toList();
        assertEquals(List.of("w/sub/z.txt", "w/x.txt"), txt);

        List<String> direct = Vostok.File.walk("w", false).stream()
                .map(i -> i.path().replace('\\', '/')).sorted().toList();
        assertEquals(List.of("w/sub", "w/x.txt", "w/y.log"), direct);
    }

    @Test
    void testDeleteIfExistsAndDeleteRecursively() {
        init();
        Vostok.File.write("d/a.txt", "1");
        Vostok.File.write("d/sub/b.txt", "2");

        assertFalse(Vostok.File.deleteIfExists("d/missing.txt"));
        assertTrue(Vostok.File.deleteIfExists("d/a.txt"));
        assertFalse(Vostok.File.exists("d/a.txt"));

        assertTrue(Vostok.File.deleteRecursively("d"));
        assertFalse(Vostok.File.exists("d"));
        assertFalse(Vostok.File.deleteRecursively("d"));
    }

    @Test
    void testCopyDirAndMoveDirWithConflictStrategy() {
        init();
        Vostok.File.write("src/a.txt", "v1");
        Vostok.File.write("src/sub/b.txt", "v2");

        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.FAIL);
        assertEquals("v1", Vostok.File.read("dst/a.txt"));
        assertEquals("v2", Vostok.File.read("dst/sub/b.txt"));

        assertThrows(VKFileException.class, () -> Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.FAIL));

        Vostok.File.write("src/a.txt", "new");
        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.SKIP);
        assertEquals("v1", Vostok.File.read("dst/a.txt"));

        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.OVERWRITE);
        assertEquals("new", Vostok.File.read("dst/a.txt"));

        Vostok.File.write("stage/x.txt", "x1");
        Vostok.File.write("target/x.txt", "old");
        Vostok.File.moveDir("stage", "target", VKFileConflictStrategy.SKIP);
        assertTrue(Vostok.File.exists("stage/x.txt"));
        assertEquals("old", Vostok.File.read("target/x.txt"));

        Vostok.File.moveDir("stage", "target", VKFileConflictStrategy.OVERWRITE);
        assertFalse(Vostok.File.exists("stage"));
        assertEquals("x1", Vostok.File.read("target/x.txt"));
    }

    @Test
    void testWatch() throws Exception {
        init();
        Vostok.File.mkdirs("watch");

        CountDownLatch latch = new CountDownLatch(1);
        List<VKFileWatchEventType> types = new CopyOnWriteArrayList<>();
        try (var handle = Vostok.File.watch("watch", e -> {
            types.add(e.type());
            latch.countDown();
        })) {
            Vostok.File.write("watch/a.txt", "1");
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        assertFalse(types.isEmpty());
        assertNotEquals(Set.of(VKFileWatchEventType.OVERFLOW), Set.copyOf(types));
    }

    @Test
    void testWatchRecursive() throws Exception {
        init();
        Vostok.File.mkdirs("watch/deep");

        CountDownLatch latch = new CountDownLatch(1);
        List<String> paths = new CopyOnWriteArrayList<>();
        try (var handle = Vostok.File.watch("watch", true, e -> {
            paths.add(e.path().replace('\\', '/'));
            if ("watch/deep/child.txt".equals(e.path().replace('\\', '/'))) {
                latch.countDown();
            }
        })) {
            Vostok.File.write("watch/deep/child.txt", "ok");
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        assertTrue(paths.contains("watch/deep/child.txt"));
    }

    @Test
    void testMoveDirFailWhenTargetExists() {
        init();
        Vostok.File.write("m1/a.txt", "1");
        Vostok.File.write("m2/a.txt", "2");
        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.moveDir("m1", "m2", VKFileConflictStrategy.FAIL));
        assertEquals(VKFileErrorCode.STATE_ERROR, ex.getErrorCode());
    }

    @Test
    void testMkdirFailsWhenParentMissing() {
        init();
        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.mkdir("x/y"));
        assertEquals(VKFileErrorCode.IO_ERROR, ex.getErrorCode());
    }

    @Test
    void testBinaryReadWriteAndRange() {
        init();
        byte[] first = new byte[]{1, 2, 3, 4};
        byte[] second = new byte[]{5, 6};
        Vostok.File.writeBytes("bin/a.dat", first);
        Vostok.File.appendBytes("bin/a.dat", second);
        assertEquals(6, Vostok.File.readBytes("bin/a.dat").length);

        byte[] range = Vostok.File.readRange("bin/a.dat", 2, 3);
        assertArrayEquals(new byte[]{3, 4, 5}, range);

        assertEquals(0, Vostok.File.readRange("bin/a.dat", 100, 10).length);

        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.readRange("bin/a.dat", -1, 1));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    @Test
    void testReadRangeToForLargeRead() {
        init();
        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        Vostok.File.writeBytes("bin/large.dat", payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long copied = Vostok.File.readRangeTo("bin/large.dat", 1024, 128 * 1024, out);
        assertEquals(128 * 1024L, copied);

        byte[] expected = new byte[128 * 1024];
        System.arraycopy(payload, 1024, expected, 0, expected.length);
        assertArrayEquals(expected, out.toByteArray());
    }

    @Test
    void testStreamReadWriteAppend() throws Exception {
        init();
        byte[] payload = new byte[512 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }

        long w = Vostok.File.writeFrom("stream/big.bin", new ByteArrayInputStream(payload));
        assertEquals(payload.length, w);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long r = Vostok.File.readTo("stream/big.bin", out);
        assertEquals(payload.length, r);
        assertArrayEquals(payload, out.toByteArray());

        byte[] tail = new byte[]{9, 8, 7, 6};
        long a = Vostok.File.appendFrom("stream/big.bin", new ByteArrayInputStream(tail));
        assertEquals(tail.length, a);
        byte[] all = Vostok.File.readBytes("stream/big.bin");
        assertEquals(payload.length + tail.length, all.length);
        assertEquals(9, all[payload.length]);
    }

    @Test
    void testWriteFromNoReplace() {
        init();
        Vostok.File.write("stream/a.txt", "a");
        VKFileException ex = assertThrows(VKFileException.class, () ->
                Vostok.File.writeFrom("stream/a.txt", new ByteArrayInputStream(new byte[]{1}), false));
        assertEquals(VKFileErrorCode.IO_ERROR, ex.getErrorCode());
    }

    @Test
    void testZipBombLimitByEntryAndTotal() {
        init();
        Vostok.File.write("z/a.txt", "1234567890");
        Vostok.File.write("z/b.txt", "abcdefghij");
        Vostok.File.zip("z", "z.zip");

        VKFileException byEntries = assertThrows(VKFileException.class, () ->
                Vostok.File.unzip("z.zip", "out1", VKUnzipOptions.builder().maxEntries(1).build()));
        assertEquals(VKFileErrorCode.ZIP_BOMB_RISK, byEntries.getErrorCode());

        VKFileException byTotal = assertThrows(VKFileException.class, () ->
                Vostok.File.unzip("z.zip", "out2", VKUnzipOptions.builder().maxTotalUncompressedBytes(5).build()));
        assertEquals(VKFileErrorCode.ZIP_BOMB_RISK, byTotal.getErrorCode());

        VKFileException byEntrySize = assertThrows(VKFileException.class, () ->
                Vostok.File.unzip("z.zip", "out3", VKUnzipOptions.builder().maxEntryUncompressedBytes(5).build()));
        assertEquals(VKFileErrorCode.ZIP_BOMB_RISK, byEntrySize.getErrorCode());
    }

    @Test
    void testInvalidUnzipOptions() {
        init();
        Vostok.File.write("a.txt", "abc");
        Vostok.File.zip("a.txt", "a.zip");

        VKFileException ex = assertThrows(VKFileException.class, () ->
                Vostok.File.unzip("a.zip", "out", VKUnzipOptions.builder().maxEntries(-2).build()));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    @Test
    void testNotInitialized() {
        VKFileException ex = assertThrows(VKFileException.class, () -> Vostok.File.read("a.txt"));
        assertEquals(VKFileErrorCode.NOT_INITIALIZED, ex.getErrorCode());
    }

    @Test
    void testConfigAndDefaultWatchRecursive() throws Exception {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()).watchRecursiveDefault(true));
        assertTrue(Vostok.File.started());
        assertTrue(Vostok.File.config().isWatchRecursiveDefault());
        Vostok.File.mkdirs("watch/deep");
        CountDownLatch latch = new CountDownLatch(1);
        try (var h = Vostok.File.watch("watch", e -> {
            if ("watch/deep/default.txt".equals(e.path().replace('\\', '/'))) {
                latch.countDown();
            }
        })) {
            Vostok.File.write("watch/deep/default.txt", "ok");
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void testSuggestDatePathAndWriteByDatePath() {
        Vostok.File.init(new VKFileConfig()
                .baseDir(tempDir.toString())
                .datePartitionPattern("yyyy/MM/dd")
                .datePartitionZoneId("UTC"));

        String suggested = Vostok.File.suggestDatePath("upload/a.txt", Instant.parse("2026-02-18T01:02:03Z"));
        assertEquals("2026/02/18/upload/a.txt", suggested);

        String p1 = Vostok.File.writeByDatePath("upload/t1.txt", "hello");
        assertTrue(p1.matches("\\d{4}/\\d{2}/\\d{2}/upload/t1\\.txt"));
        assertEquals("hello", Vostok.File.read(p1));

        String p2 = Vostok.File.writeBytesByDatePath("upload/t2.bin", new byte[]{1, 2, 3});
        assertTrue(p2.matches("\\d{4}/\\d{2}/\\d{2}/upload/t2\\.bin"));
        assertArrayEquals(new byte[]{1, 2, 3}, Vostok.File.readBytes(p2));

        String p3 = Vostok.File.writeFromByDatePath("upload/t3.bin", new ByteArrayInputStream(new byte[]{4, 5}));
        assertTrue(p3.matches("\\d{4}/\\d{2}/\\d{2}/upload/t3\\.bin"));
        assertArrayEquals(new byte[]{4, 5}, Vostok.File.readBytes(p3));
    }

    @Test
    void testNormalWriteUnchangedWhenDatePathApisExist() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        Vostok.File.write("plain/a.txt", "x");
        assertTrue(Vostok.File.exists("plain/a.txt"));
        assertEquals("x", Vostok.File.read("plain/a.txt"));
    }

    @Test
    void testSuggestDatePathInvalidRelativePath() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.File.suggestDatePath("/abs/a.txt"));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    @Test
    void testMigrateBaseDirCopyOnly() throws Exception {
        init();
        Vostok.File.write("mig/a.txt", "A");
        Vostok.File.write("mig/sub/b.txt", "BB");

        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-copy");
        VKFileMigrateResult result = Vostok.File.migrateBaseDir(target.toString(),
                new VKFileMigrateOptions()
                        .mode(VKFileMigrateMode.COPY_ONLY)
                        .conflictStrategy(VKFileConflictStrategy.FAIL)
                        .verifyHash(true));

        assertTrue(result.success());
        assertEquals(2, result.totalFiles());
        assertEquals(2, result.migratedFiles());
        assertEquals(0, result.failedFiles());
        assertEquals("A", Vostok.File.read("mig/a.txt"));
        assertEquals("A", Files.readString(target.resolve("mig/a.txt")));
        assertEquals("BB", Files.readString(target.resolve("mig/sub/b.txt")));
    }

    @Test
    void testMigrateBaseDirMoveWithCleanup() throws Exception {
        init();
        Vostok.File.write("mv/d1/a.txt", "1");
        Vostok.File.write("mv/d2/b.txt", "2");

        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-move");
        VKFileMigrateResult result = Vostok.File.migrateBaseDir(target.toString(),
                new VKFileMigrateOptions()
                        .mode(VKFileMigrateMode.MOVE)
                        .conflictStrategy(VKFileConflictStrategy.OVERWRITE)
                        .deleteEmptyDirsAfterMove(true));

        assertTrue(result.success());
        assertEquals(2, result.migratedFiles());
        assertFalse(Vostok.File.exists("mv/d1/a.txt"));
        assertFalse(Vostok.File.exists("mv/d2/b.txt"));
        assertFalse(Vostok.File.exists("mv/d1"));
        assertFalse(Vostok.File.exists("mv/d2"));
        assertEquals("1", Files.readString(target.resolve("mv/d1/a.txt")));
        assertEquals("2", Files.readString(target.resolve("mv/d2/b.txt")));
    }

    @Test
    void testMigrateBaseDirConflictSkipAndDryRun() throws Exception {
        init();
        Vostok.File.write("conflict/a.txt", "src");

        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-skip");
        Files.createDirectories(target.resolve("conflict"));
        Files.writeString(target.resolve("conflict/a.txt"), "dst");

        VKFileMigrateResult skipResult = Vostok.File.migrateBaseDir(target.toString(),
                new VKFileMigrateOptions()
                        .mode(VKFileMigrateMode.COPY_ONLY)
                        .conflictStrategy(VKFileConflictStrategy.SKIP));
        assertTrue(skipResult.success());
        assertEquals(1, skipResult.totalFiles());
        assertEquals(0, skipResult.migratedFiles());
        assertEquals(1, skipResult.skippedFiles());
        assertEquals("dst", Files.readString(target.resolve("conflict/a.txt")));

        Path dryTarget = tempDir.resolveSibling(tempDir.getFileName() + "-dry");
        VKFileMigrateResult dryRunResult = Vostok.File.migrateBaseDir(dryTarget.toString(),
                new VKFileMigrateOptions()
                        .mode(VKFileMigrateMode.COPY_ONLY)
                        .conflictStrategy(VKFileConflictStrategy.OVERWRITE)
                        .dryRun(true));
        assertTrue(dryRunResult.success());
        assertEquals(1, dryRunResult.migratedFiles());
        assertFalse(Files.exists(dryTarget));
    }

    @Test
    void testMigrateBaseDirExcludeHiddenAndDefaultApi() throws Exception {
        init();
        Vostok.File.write(".hidden.txt", "h");
        Vostok.File.write("normal.txt", "n");

        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-hidden");
        VKFileMigrateResult result = Vostok.File.migrateBaseDir(target.toString(),
                new VKFileMigrateOptions().includeHidden(false));
        assertTrue(result.success());
        assertEquals(1, result.totalFiles());
        assertTrue(Files.exists(target.resolve("normal.txt")));
        assertFalse(Files.exists(target.resolve(".hidden.txt")));

        Path target2 = tempDir.resolveSibling(tempDir.getFileName() + "-default");
        VKFileMigrateResult result2 = Vostok.File.migrateBaseDir(target2.toString());
        assertEquals(2, result2.totalFiles());
        assertEquals(2, result2.migratedFiles());
    }

    @Test
    void testMigrateBaseDirTargetInsideSourceNotAllowed() {
        init();
        Vostok.File.write("a.txt", "1");
        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.File.migrateBaseDir(tempDir.resolve("inside").toString()));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    @Test
    void testMigrateBaseDirResumeByCheckpoint() throws Exception {
        init();
        Vostok.File.write("resume/a.txt", "A");
        Vostok.File.write("resume/b.txt", "B");
        Path sourceB = tempDir.resolve("resume/b.txt");
        boolean changed = sourceB.toFile().setReadable(false, false);
        Assumptions.assumeTrue(changed && !Files.isReadable(sourceB), "Cannot simulate unreadable file on this fs");

        Path checkpoint = tempDir.resolveSibling(tempDir.getFileName() + "-resume.ckpt");
        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-resume-target");
        try {
            VKFileMigrateResult r1 = Vostok.File.migrateBaseDir(target.toString(),
                    new VKFileMigrateOptions()
                            .mode(VKFileMigrateMode.COPY_ONLY)
                            .conflictStrategy(VKFileConflictStrategy.OVERWRITE)
                            .checkpointFile(checkpoint.toString())
                            .maxRetries(0));
            assertEquals(1, r1.failedFiles());
            assertEquals(1, r1.migratedFiles());

            assertTrue(sourceB.toFile().setReadable(true, false));
            VKFileMigrateResult r2 = Vostok.File.migrateBaseDir(target.toString(),
                    new VKFileMigrateOptions()
                            .mode(VKFileMigrateMode.COPY_ONLY)
                            .conflictStrategy(VKFileConflictStrategy.OVERWRITE)
                            .checkpointFile(checkpoint.toString())
                            .maxRetries(0));
            assertTrue(r2.success());
            assertEquals(1, r2.migratedFiles());
            assertTrue(r2.skippedFiles() >= 1);
            assertEquals("A", Files.readString(target.resolve("resume/a.txt")));
            assertEquals("B", Files.readString(target.resolve("resume/b.txt")));
        } finally {
            sourceB.toFile().setReadable(true, false);
        }
    }

    @Test
    void testMigrateBaseDirRetryAndProgress() throws Exception {
        init();
        Vostok.File.write("retry/a.txt", "A");
        Path source = tempDir.resolve("retry/a.txt");
        boolean changed = source.toFile().setReadable(false, false);
        Assumptions.assumeTrue(changed && !Files.isReadable(source), "Cannot simulate unreadable file on this fs");

        List<VKFileMigrateProgressStatus> statuses = new CopyOnWriteArrayList<>();
        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-retry");
        try {
            VKFileMigrateResult result = Vostok.File.migrateBaseDir(target.toString(),
                    new VKFileMigrateOptions()
                            .mode(VKFileMigrateMode.COPY_ONLY)
                            .conflictStrategy(VKFileConflictStrategy.OVERWRITE)
                            .maxRetries(2)
                            .retryIntervalMs(10)
                            .progressListener(p -> {
                                statuses.add(p.status());
                                if (p.status() == VKFileMigrateProgressStatus.RETRYING) {
                                    source.toFile().setReadable(true, false);
                                }
                            }));

            assertTrue(result.success());
            assertTrue(statuses.contains(VKFileMigrateProgressStatus.RETRYING));
            assertTrue(statuses.contains(VKFileMigrateProgressStatus.MIGRATED));
            assertEquals(VKFileMigrateProgressStatus.DONE, statuses.get(statuses.size() - 1));
            assertEquals("A", Files.readString(target.resolve("retry/a.txt")));
        } finally {
            source.toFile().setReadable(true, false);
        }
    }

    @Test
    void testMigrateBaseDirInvalidRetryAndCheckpointOptions() {
        init();
        Vostok.File.write("a.txt", "1");
        VKFileException ex1 = assertThrows(VKFileException.class,
                () -> Vostok.File.migrateBaseDir(tempDir.resolveSibling("x").toString(),
                        new VKFileMigrateOptions().maxRetries(-1)));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex1.getErrorCode());

        VKFileException ex2 = assertThrows(VKFileException.class,
                () -> Vostok.File.migrateBaseDir(tempDir.resolveSibling("y").toString(),
                        new VKFileMigrateOptions().checkpointFile(tempDir.resolve("resume.ckpt").toString())));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex2.getErrorCode());
    }

    @Test
    void testMigrateBaseDirParallelWithSmallQueue() throws Exception {
        init();
        for (int i = 0; i < 80; i++) {
            Vostok.File.write("p/" + i + ".txt", "v" + i);
        }
        Path target = tempDir.resolveSibling(tempDir.getFileName() + "-parallel");
        AtomicInteger migratedEvents = new AtomicInteger();
        VKFileMigrateResult result = Vostok.File.migrateBaseDir(target.toString(),
                new VKFileMigrateOptions()
                        .mode(VKFileMigrateMode.COPY_ONLY)
                        .parallelism(4)
                        .queueCapacity(1)
                        .progressListener(p -> {
                            if (p.status() == VKFileMigrateProgressStatus.MIGRATED) {
                                migratedEvents.incrementAndGet();
                            }
                        }));
        assertTrue(result.success());
        assertEquals(80, result.totalFiles());
        assertEquals(80, result.migratedFiles());
        assertEquals(80, migratedEvents.get());
        assertEquals("v0", Files.readString(target.resolve("p/0.txt")));
        assertEquals("v79", Files.readString(target.resolve("p/79.txt")));
    }

    @Test
    void testMigrateBaseDirInvalidParallelOptions() {
        init();
        Vostok.File.write("a.txt", "1");
        VKFileException ex1 = assertThrows(VKFileException.class,
                () -> Vostok.File.migrateBaseDir(tempDir.resolveSibling("p1").toString(),
                        new VKFileMigrateOptions().parallelism(0)));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex1.getErrorCode());

        VKFileException ex2 = assertThrows(VKFileException.class,
                () -> Vostok.File.migrateBaseDir(tempDir.resolveSibling("p2").toString(),
                        new VKFileMigrateOptions().queueCapacity(0)));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex2.getErrorCode());
    }
}
