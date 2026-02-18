package yueyang.vostok.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.Vostok;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VostokLogTest {
    private Path dir;

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("vostok-log-test");
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("test")
                .level(VKLogLevel.INFO)
                .queueCapacity(256)
                .flushIntervalMs(50)
                .flushBatchSize(32)
                .queueFullPolicy(VKLogQueueFullPolicy.DROP)
                .rollInterval(VKLogRollInterval.DAILY));
    }

    @AfterEach
    void tearDown() {
        Vostok.Log.flush();
        Vostok.Log.close();
    }

    @Test
    void testInitAndInitialized() {
        assertTrue(Vostok.Log.initialized());
        Vostok.Log.close();
        assertFalse(Vostok.Log.initialized());
    }

    @Test
    void testInitIsIdempotent() {
        Vostok.Log.init(new VKLogConfig().outputDir("/tmp/should-not-take-effect").filePrefix("other"));
        assertTrue(Vostok.Log.initialized());
        Vostok.Log.info("idempotent-check");
        Vostok.Log.flush();
        assertTrue(Files.exists(dir.resolve("test.log")));
        assertFalse(Files.exists(Path.of("/tmp/should-not-take-effect").resolve("other.log")));
    }

    @Test
    void testReinitAppliesNewConfig() throws Exception {
        Path dir2 = Files.createTempDirectory("vostok-log-test2");
        Vostok.Log.reinit(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir2.toString())
                .filePrefix("reinit")
                .level(VKLogLevel.INFO));
        Vostok.Log.info("reinit-ok");
        Vostok.Log.flush();
        assertTrue(Files.exists(dir2.resolve("reinit.log")));
        String text = Files.readString(dir2.resolve("reinit.log"));
        assertTrue(text.contains("reinit-ok"));
        Vostok.Log.reinit(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("test")
                .level(VKLogLevel.INFO));
    }

    @Test
    void testVostokLogEntryAndCaller() throws Exception {
        Vostok.Log.info("hello {}", "world");
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("test.log"));
        assertTrue(text.contains("hello world"));
        assertTrue(text.contains("yueyang.vostok.log.VostokLogTest"));
    }

    @Test
    void testQueuePolicyDropCountsDropped() throws Exception {
        Vostok.Log.setQueueCapacity(8);
        Vostok.Log.setQueueFullPolicy(VKLogQueueFullPolicy.DROP);
        Vostok.Log.setFsyncPolicy(VKLogFsyncPolicy.EVERY_WRITE);

        fireHighBurst(8, 800);
        Vostok.Log.flush();

        assertTrue(Vostok.Log.droppedLogs() > 0);
    }

    @Test
    void testQueuePolicyBlockNoDrop() throws Exception {
        Vostok.Log.setQueueCapacity(8);
        Vostok.Log.setQueueFullPolicy(VKLogQueueFullPolicy.BLOCK);
        Vostok.Log.setFsyncPolicy(VKLogFsyncPolicy.EVERY_WRITE);

        fireHighBurst(4, 400);
        Vostok.Log.flush();

        assertEquals(0, Vostok.Log.droppedLogs());
    }

    @Test
    void testQueuePolicySyncFallbackNoDropAndHasFallbackWrites() throws Exception {
        Vostok.Log.setQueueCapacity(1);
        Vostok.Log.setQueueFullPolicy(VKLogQueueFullPolicy.SYNC_FALLBACK);
        Vostok.Log.setFsyncPolicy(VKLogFsyncPolicy.EVERY_WRITE);

        fireHighBurst(8, 500);
        Vostok.Log.flush();

        assertEquals(0, Vostok.Log.droppedLogs());
        assertTrue(Vostok.Log.fallbackWrites() > 0);
    }

    @Test
    void testRollCompressAndPrune() throws Exception {
        Vostok.Log.setFilePrefix("roll");
        Vostok.Log.setMaxFileSizeBytes(512);
        Vostok.Log.setCompressRolledFiles(true);
        Vostok.Log.setMaxBackups(2);
        Vostok.Log.setMaxBackupDays(1);
        Vostok.Log.setMaxTotalSizeMb(1);

        Path old = dir.resolve("roll-20000101-000000-1.log");
        Files.writeString(old, "old");
        Files.setLastModifiedTime(old, FileTime.fromMillis(System.currentTimeMillis() - Duration.ofDays(3).toMillis()));

        for (int i = 0; i < 500; i++) {
            Vostok.Log.info("line-{}-{}", i, "x".repeat(50));
        }
        Vostok.Log.flush();

        List<Path> rolled = listRolled("roll");
        assertTrue(Files.notExists(old));
        assertTrue(rolled.size() <= 2);
        assertTrue(rolled.stream().anyMatch(p -> p.getFileName().toString().endsWith(".gz")));
    }

    @Test
    void testFallbackToStderrWhenOutputDirInvalid() throws Exception {
        Path invalid = Files.createTempFile("vostok-log-invalid", ".tmp");
        Vostok.Log.setOutputDir(invalid.toString());
        Vostok.Log.setQueueFullPolicy(VKLogQueueFullPolicy.BLOCK);

        Vostok.Log.error("file-open-fail");
        Vostok.Log.flush();

        assertTrue(Vostok.Log.fileWriteErrors() > 0);
        assertTrue(Vostok.Log.fallbackWrites() > 0);
    }

    private void fireHighBurst(int threads, int perThread) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int worker = t;
            pool.submit(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    for (int i = 0; i < perThread; i++) {
                        Vostok.Log.info("burst-{}-{}", worker, i);
                    }
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private List<Path> listRolled(String prefix) throws Exception {
        List<Path> out = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix + "-") && (name.endsWith(".log") || name.endsWith(".log.gz"));
                    })
                    .forEach(out::add);
        }
        return out;
    }
}
