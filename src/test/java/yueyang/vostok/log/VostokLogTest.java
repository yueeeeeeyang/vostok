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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void testLoggerApiRoutesToDedicatedFile() throws Exception {
        Vostok.Log.logger("data").info("sql-run");
        Vostok.Log.info("default-run");
        Vostok.Log.flush();

        String dataText = Files.readString(dir.resolve("data.log"));
        String defaultText = Files.readString(dir.resolve("test.log"));
        assertTrue(dataText.contains("sql-run"));
        assertTrue(defaultText.contains("default-run"));
    }

    @Test
    void testGetLoggerReturnsReusableInstance() throws Exception {
        VKLogger l1 = Vostok.Log.getLogger("data");
        VKLogger l2 = Vostok.Log.getLogger("data");
        assertSame(l1, l2);

        l1.info("reuse-1");
        l2.info("reuse-2");
        Vostok.Log.flush();

        String dataText = Files.readString(dir.resolve("data.log"));
        assertTrue(dataText.contains("reuse-1"));
        assertTrue(dataText.contains("reuse-2"));
    }

    @Test
    void testPreRegisteredLoggersWorkWithStrictMode() throws Exception {
        // autoCreateLoggerSink=false + throwOnUnknownLogger=true：严格模式，未注册 logger 抛异常
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("test")
                .autoCreateLoggerSink(false)
                .throwOnUnknownLogger(true)
                .registerLoggers("data", "web"));

        Vostok.Log.logger("data").info("data-log");
        Vostok.Log.getLogger("web").info("web-log");
        Vostok.Log.flush();

        assertTrue(Files.readString(dir.resolve("data.log")).contains("data-log"));
        assertTrue(Files.readString(dir.resolve("web.log")).contains("web-log"));
        // 严格模式：未注册 logger 抛异常
        assertThrows(IllegalArgumentException.class, () -> Vostok.Log.logger("unknown"));
    }

    @Test
    void testPerLoggerSinkOverride() throws Exception {
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("test")
                .registerLogger("access", new VKLogSinkConfig()
                        .filePrefix("access-special")
                        .maxFileSizeBytes(1024L * 1024)));

        Vostok.Log.getLogger("access").info("access-hit");
        Vostok.Log.flush();

        Path p = dir.resolve("access-special.log");
        assertTrue(Files.exists(p));
        assertTrue(Files.readString(p).contains("access-hit"));
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

    // =========================================================================
    // MDC 测试
    // =========================================================================

    @Test
    void testMdcAppearsInOutput() throws Exception {
        VKLogMDC.put("requestId", "req-001");
        VKLogMDC.put("userId", "u-42");
        Vostok.Log.info("mdc-msg");
        Vostok.Log.flush();
        VKLogMDC.clear();

        String text = Files.readString(dir.resolve("test.log"));
        assertTrue(text.contains("requestId=req-001"), "MDC key requestId should appear in output");
        assertTrue(text.contains("userId=u-42"), "MDC key userId should appear in output");
        assertTrue(text.contains("mdc-msg"), "message should appear in output");
    }

    @Test
    void testMdcPutGetRemoveClear() {
        VKLogMDC.put("k1", "v1");
        VKLogMDC.put("k2", "v2");
        assertEquals("v1", VKLogMDC.get("k1"));
        assertEquals("v2", VKLogMDC.get("k2"));

        VKLogMDC.remove("k1");
        assertNull(VKLogMDC.get("k1"));
        assertEquals("v2", VKLogMDC.get("k2"));

        VKLogMDC.clear();
        assertNull(VKLogMDC.get("k2"));
        assertTrue(VKLogMDC.getAll().isEmpty());
    }

    @Test
    void testMdcGetAllReturnsSnapshot() {
        VKLogMDC.put("a", "1");
        VKLogMDC.put("b", "2");
        Map<String, String> snap = VKLogMDC.getAll();
        assertEquals(2, snap.size());
        assertEquals("1", snap.get("a"));
        assertEquals("2", snap.get("b"));

        // 快照不反映后续变更
        VKLogMDC.put("c", "3");
        assertEquals(2, snap.size());
        VKLogMDC.clear();
    }

    @Test
    void testMdcPutAllBatchSet() {
        VKLogMDC.putAll(Map.of("x", "10", "y", "20"));
        assertEquals("10", VKLogMDC.get("x"));
        assertEquals("20", VKLogMDC.get("y"));
        VKLogMDC.clear();
    }

    @Test
    void testMdcIsThreadLocal() throws Exception {
        // 主线程设置 MDC，子线程应看不到
        VKLogMDC.put("mainKey", "mainVal");
        String[] childVal = {null};
        Thread t = new Thread(() -> childVal[0] = VKLogMDC.get("mainKey"));
        t.start();
        t.join(2000);
        assertNull(childVal[0], "MDC should be thread-local, child thread should not see main's context");
        VKLogMDC.clear();
    }

    @Test
    void testMdcClearPreventsLeakage() throws Exception {
        VKLogMDC.put("leak", "should-not-appear");
        VKLogMDC.clear();
        Vostok.Log.info("after-clear");
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("test.log"));
        assertFalse(text.contains("should-not-appear"), "Cleared MDC must not appear in subsequent log");
        assertTrue(text.contains("after-clear"));
    }

    @Test
    void testMdcPutNullKeyIgnored() {
        VKLogMDC.put(null, "value");
        assertTrue(VKLogMDC.getAll().isEmpty(), "null key should be ignored");
    }

    @Test
    void testMdcPutAllNullOrEmptyIgnored() {
        VKLogMDC.putAll(null);
        VKLogMDC.putAll(Map.of());
        assertTrue(VKLogMDC.getAll().isEmpty());
    }

    @Test
    void testMdcCrossThreadPropagation() throws Exception {
        // 模拟 Web 框架跨线程传播 MDC 快照
        VKLogMDC.put("traceId", "trace-xyz");
        Map<String, String> snapshot = VKLogMDC.getAll();
        VKLogMDC.clear(); // 主线程清除

        List<String> childOutput = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            VKLogMDC.putAll(snapshot);
            try {
                childOutput.add(VKLogMDC.get("traceId"));
            } finally {
                VKLogMDC.clear();
                done.countDown();
            }
        });
        t.start();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals("trace-xyz", childOutput.get(0), "Cross-thread MDC propagation via putAll should work");
    }

    // =========================================================================
    // 级别查询方法测试
    // =========================================================================

    @Test
    void testStaticLevelQueryMatchesConfig() {
        // setUp 中 level=INFO
        assertFalse(Vostok.Log.isTraceEnabled());
        assertFalse(Vostok.Log.isDebugEnabled());
        assertTrue(Vostok.Log.isInfoEnabled());
        assertTrue(Vostok.Log.isWarnEnabled());
        assertTrue(Vostok.Log.isErrorEnabled());

        Vostok.Log.setLevel(VKLogLevel.WARN);
        assertFalse(Vostok.Log.isInfoEnabled());
        assertTrue(Vostok.Log.isWarnEnabled());

        Vostok.Log.setLevel(VKLogLevel.TRACE);
        assertTrue(Vostok.Log.isTraceEnabled());
        assertTrue(Vostok.Log.isDebugEnabled());
    }

    @Test
    void testNamedLoggerLevelQuery() {
        // 默认继承全局 INFO 级别
        VKLogger logger = Vostok.Log.logger("lvltest");
        assertFalse(logger.isTraceEnabled());
        assertFalse(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
        assertTrue(logger.isWarnEnabled());
        assertTrue(logger.isErrorEnabled());
    }

    @Test
    void testLevelQueryAfterSetLevelForSpecificLogger() {
        VKLogger logger = Vostok.Log.logger("payment");
        // 全局 INFO，命名 logger 先继承 INFO
        assertFalse(logger.isDebugEnabled());

        // 单独设为 DEBUG
        Vostok.Log.setLevel("payment", VKLogLevel.DEBUG);
        assertTrue(logger.isDebugEnabled(), "After setLevel to DEBUG, isDebugEnabled should be true");

        // 全局仍为 INFO（另一个 logger 不受影响）
        VKLogger other = Vostok.Log.logger("other-svc");
        assertFalse(other.isDebugEnabled());
    }

    // =========================================================================
    // 运行时单 Logger 级别设置测试
    // =========================================================================

    @Test
    void testSetLevelPerLoggerFiltersIndependently() throws Exception {
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .level(VKLogLevel.INFO)
                .flushIntervalMs(50));

        VKLogger payLogger = Vostok.Log.logger("pay");
        VKLogger webLogger = Vostok.Log.logger("web");

        Vostok.Log.setLevel("pay", VKLogLevel.DEBUG);

        payLogger.debug("pay-debug-visible");
        // 写一条 INFO 消息确保 web.log 文件被创建，DEBUG 消息应被过滤
        webLogger.info("web-info-visible");
        webLogger.debug("web-debug-hidden");
        Vostok.Log.flush();

        String payText = Files.readString(dir.resolve("pay.log"));
        assertTrue(payText.contains("pay-debug-visible"),
                "pay logger should output DEBUG after its level is set to DEBUG");

        String webText = Files.readString(dir.resolve("web.log"));
        assertTrue(webText.contains("web-info-visible"),
                "web logger should output INFO messages");
        assertFalse(webText.contains("web-debug-hidden"),
                "web logger should filter DEBUG since its level is still INFO");
    }

    @Test
    void testSetLevelPerLoggerCreatesLazylyIfNeeded() {
        // logger sink 还未创建时 setLevel 应能正常工作（内部惰性创建）
        Vostok.Log.setLevel("lazy-logger", VKLogLevel.DEBUG);
        VKLogger logger = Vostok.Log.logger("lazy-logger");
        assertTrue(logger.isDebugEnabled(), "Level set before logger creation should take effect");
    }

    // =========================================================================
    // 自定义格式化器测试
    // =========================================================================

    @Test
    void testCustomFormatterApplied() throws Exception {
        Vostok.Log.setFormatter((level, loggerName, msg, t, ts, mdc) ->
                "CUSTOM|" + level + "|" + msg + "\n");

        Vostok.Log.info("fmt-msg");
        Vostok.Log.flush();
        Vostok.Log.setFormatter(null);

        String text = Files.readString(dir.resolve("test.log"));
        assertTrue(text.contains("CUSTOM|INFO|fmt-msg"), "Custom formatter output should appear in log file");
    }

    @Test
    void testCustomFormatterReceivesMdcAndThrowable() throws Exception {
        List<Map<String, String>> capturedMdc = new CopyOnWriteArrayList<>();
        List<Throwable> capturedErrors = new CopyOnWriteArrayList<>();

        Vostok.Log.setFormatter((level, loggerName, msg, t, ts, mdc) -> {
            capturedMdc.add(mdc);
            if (t != null) capturedErrors.add(t);
            return msg + "\n";
        });

        VKLogMDC.put("fmtKey", "fmtVal");
        RuntimeException ex = new RuntimeException("test-ex");
        Vostok.Log.error("error-with-ex", ex);
        Vostok.Log.flush();
        VKLogMDC.clear();
        Vostok.Log.setFormatter(null);

        assertFalse(capturedMdc.isEmpty(), "Formatter should receive MDC snapshot");
        assertEquals("fmtVal", capturedMdc.get(0).get("fmtKey"), "MDC value should be passed to formatter");
        assertFalse(capturedErrors.isEmpty(), "Formatter should receive throwable");
        assertSame(ex, capturedErrors.get(0));
    }

    @Test
    void testCustomFormatterNullRestoresDefault() throws Exception {
        Vostok.Log.setFormatter((level, loggerName, msg, t, ts, mdc) -> "CUSTOM\n");
        Vostok.Log.setFormatter(null);
        Vostok.Log.info("default-format-check");
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("test.log"));
        // 默认格式含有级别标记和 logger 名
        assertTrue(text.contains("[INFO]"), "Default format should be restored after setting null formatter");
        assertTrue(text.contains("default-format-check"));
    }

    @Test
    void testPerLoggerSinkCustomFormatter() throws Exception {
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("test")
                .registerLogger("access", new VKLogSinkConfig()
                        .formatter((level, loggerName, msg, t, ts, mdc) -> "JSON|" + msg + "\n")));

        Vostok.Log.logger("access").info("access-hit");
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("access.log"));
        assertTrue(text.contains("JSON|access-hit"), "Per-logger formatter should override global formatter");
    }

    // =========================================================================
    // ERROR 监听器测试
    // =========================================================================

    @Test
    void testErrorListenerCalledOnError() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        List<String> capturedMessages = new CopyOnWriteArrayList<>();

        Vostok.Log.setErrorListener((loggerName, message, error, timestamp) -> {
            callCount.incrementAndGet();
            capturedMessages.add(message);
        });

        Vostok.Log.error("err-one");
        Vostok.Log.error("err-two");
        Vostok.Log.flush();
        Vostok.Log.setErrorListener(null);

        assertEquals(2, callCount.get(), "Error listener should be called once per ERROR log");
        assertTrue(capturedMessages.contains("err-one"));
        assertTrue(capturedMessages.contains("err-two"));
    }

    @Test
    void testErrorListenerNotCalledOnNonError() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        Vostok.Log.setErrorListener((loggerName, message, error, timestamp) -> callCount.incrementAndGet());

        Vostok.Log.trace("t");
        Vostok.Log.debug("d");
        Vostok.Log.info("i");
        Vostok.Log.warn("w");
        Vostok.Log.flush();
        Vostok.Log.setErrorListener(null);

        assertEquals(0, callCount.get(), "Error listener should NOT be called for non-ERROR levels");
    }

    @Test
    void testErrorListenerReceivesThrowable() throws Exception {
        List<Throwable> capturedErrors = new CopyOnWriteArrayList<>();
        Vostok.Log.setErrorListener((loggerName, message, error, timestamp) -> {
            if (error != null) capturedErrors.add(error);
        });

        RuntimeException ex = new RuntimeException("listener-ex");
        Vostok.Log.error("with-throwable", ex);
        Vostok.Log.flush();
        Vostok.Log.setErrorListener(null);

        assertEquals(1, capturedErrors.size());
        assertSame(ex, capturedErrors.get(0));
    }

    @Test
    void testErrorListenerExceptionDoesNotBreakLogging() throws Exception {
        // 监听器本身抛出异常，不应影响后续日志写入
        Vostok.Log.setErrorListener((loggerName, message, error, timestamp) -> {
            throw new RuntimeException("listener-crash");
        });

        Vostok.Log.error("before-crash");
        Vostok.Log.error("after-crash");
        Vostok.Log.flush();
        Vostok.Log.setErrorListener(null);

        String text = Files.readString(dir.resolve("test.log"));
        assertTrue(text.contains("before-crash"), "Log before listener crash should be written");
        assertTrue(text.contains("after-crash"), "Log after listener crash should still be written");
    }

    @Test
    void testErrorListenerNullDisablesListener() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        Vostok.Log.setErrorListener((loggerName, message, error, timestamp) -> callCount.incrementAndGet());
        Vostok.Log.setErrorListener(null);

        Vostok.Log.error("no-listener");
        Vostok.Log.flush();

        assertEquals(0, callCount.get(), "Null listener should disable callbacks");
    }

    // =========================================================================
    // WEEKLY 滚动配置测试
    // =========================================================================

    @Test
    void testWeeklyRollIntervalConfigured() throws Exception {
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("weekly")
                .rollInterval(VKLogRollInterval.WEEKLY)
                .level(VKLogLevel.INFO));

        Vostok.Log.info("weekly-log");
        Vostok.Log.flush();

        assertTrue(Files.exists(dir.resolve("weekly.log")), "Log file should be created with WEEKLY roll interval");
        String text = Files.readString(dir.resolve("weekly.log"));
        assertTrue(text.contains("weekly-log"));
    }

    // =========================================================================
    // Bug 1 回归测试：pruneBackups 多阶段删除不触发虚假 markFileError
    // =========================================================================

    @Test
    void testPruneBackupsMultiStageNoFalseFileError() throws Exception {
        // 准备：设置较小的 maxBackups 和 maxTotalSize，写足够多日志触发多次 rotate + prune
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("prune")
                .maxFileSizeBytes(300)   // 小文件，频繁 rotate
                .maxBackups(2)           // 只保留2个备份，阶段1会删除多余
                .maxBackupDays(30)
                .maxTotalSizeBytes(2048) // 阶段3也会激活
                .level(VKLogLevel.INFO)
                .flushIntervalMs(50));

        // 写多条日志，触发多次 rotate
        for (int i = 0; i < 100; i++) {
            Vostok.Log.info("prune-test-line-{}", i);
        }
        Vostok.Log.flush();

        // Bug 1 的现象是 fileWriteErrors > 0，修复后应为 0
        assertEquals(0, Vostok.Log.fileWriteErrors(),
                "pruneBackups should not trigger markFileError when deleting old backups");

        // 验证备份数量约束生效
        List<Path> backups = listRolled("prune");
        assertTrue(backups.size() <= 2, "Backups should be pruned to maxBackups=2, got: " + backups.size());
    }

    // =========================================================================
    // Bug 2 回归测试：并发 setQueueCapacity 不静默丢失事件
    // =========================================================================

    @Test
    void testConcurrentSetQueueCapacityNoSilentDrop() throws Exception {
        // 用较大容量队列，扩容过程中并发写入，验证不会静默丢失
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("resize")
                .queueCapacity(64)
                .queueFullPolicy(VKLogQueueFullPolicy.BLOCK)
                .level(VKLogLevel.INFO)
                .flushIntervalMs(10));

        int THREADS = 4;
        int PER_THREAD = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS + 1);
        AtomicLong written = new AtomicLong(0);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS + 1);

        // 写日志线程
        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    for (int i = 0; i < PER_THREAD; i++) {
                        Vostok.Log.info("resize-{}-{}", tid, i);
                        written.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        // 并发扩缩容线程
        pool.submit(() -> {
            try {
                start.await(2, TimeUnit.SECONDS);
                int[] sizes = {32, 128, 64, 256, 64};
                for (int s : sizes) {
                    Vostok.Log.setQueueCapacity(s);
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        Vostok.Log.flush();

        // 文件写入错误应为 0（不是磁盘问题）
        assertEquals(0, Vostok.Log.fileWriteErrors(),
                "Queue resize should not cause file write errors");
    }

    // =========================================================================
    // 并发压测：多 Logger 高并发写入
    // =========================================================================

    @Test
    void testConcurrentMultiLoggerStress() throws Exception {
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("stress")
                .queueCapacity(1024)
                .queueFullPolicy(VKLogQueueFullPolicy.DROP)
                .level(VKLogLevel.INFO)
                .flushIntervalMs(50));

        String[] loggerNames = {"svc-a", "svc-b", "svc-c"};
        int THREADS = 6;
        int PER_THREAD = 500;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int t = 0; t < THREADS; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    VKLogger logger = Vostok.Log.logger(loggerNames[tid % loggerNames.length]);
                    for (int i = 0; i < PER_THREAD; i++) {
                        logger.info("stress-{}-{}", tid, i);
                    }
                } catch (InterruptedException e) {
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

        Vostok.Log.flush();

        // 验证三个 logger 都写入了文件
        for (String name : loggerNames) {
            Path p = dir.resolve(name + ".log");
            assertTrue(Files.exists(p), "Logger " + name + " should have written a log file");
            assertTrue(Files.size(p) > 0, "Logger " + name + " log file should not be empty");
        }
        assertEquals(0, Vostok.Log.fileWriteErrors(), "No file errors expected during concurrent stress");
    }

    // =========================================================================
    // 边界条件测试
    // =========================================================================

    @Test
    void testLoggerNameBlankThrows() {
        assertThrows(Exception.class, () -> Vostok.Log.logger("  "), "Blank logger name should throw");
    }

    @Test
    void testLoggerNameWithPathSeparatorThrows() {
        assertThrows(Exception.class, () -> Vostok.Log.logger("a/b"), "Logger name with '/' should throw");
        assertThrows(Exception.class, () -> Vostok.Log.logger("a\\b"), "Logger name with '\\' should throw");
    }

    @Test
    void testLogNullThrowableIsOk() throws Exception {
        Vostok.Log.error("no-throwable", (Throwable) null);
        Vostok.Log.flush();
        String text = Files.readString(dir.resolve("test.log"));
        assertTrue(text.contains("no-throwable"));
    }

    @Test
    void testLevelBelowThresholdIsFiltered() throws Exception {
        // 全局 INFO，DEBUG 不应写入文件
        Vostok.Log.debug("should-be-filtered");
        Vostok.Log.info("should-be-written");
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("test.log"));
        assertFalse(text.contains("should-be-filtered"), "DEBUG message should be filtered when level=INFO");
        assertTrue(text.contains("should-be-written"));
    }

    @Test
    void testSetLevelZeroFiltersAll() throws Exception {
        Vostok.Log.setLevel(VKLogLevel.ERROR);
        Vostok.Log.info("info-filtered");
        Vostok.Log.warn("warn-filtered");
        Vostok.Log.error("error-passes");
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("test.log"));
        assertFalse(text.contains("info-filtered"));
        assertFalse(text.contains("warn-filtered"));
        assertTrue(text.contains("error-passes"));
    }

    @Test
    void testFlushBeforeAnyLogDoesNotThrow() {
        // 无事件时 flush 不应抛异常
        Vostok.Log.close();
        Vostok.Log.flush(); // should be no-op
    }

    @Test
    void testErrorListenerCalledOnNamedLoggerError() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        List<String> capturedLoggers = new CopyOnWriteArrayList<>();

        Vostok.Log.setErrorListener((loggerName, message, error, timestamp) -> {
            callCount.incrementAndGet();
            capturedLoggers.add(loggerName);
        });

        VKLogger logger = Vostok.Log.logger("named-err-logger");
        logger.error("named-error");
        Vostok.Log.flush();
        Vostok.Log.setErrorListener(null);

        assertEquals(1, callCount.get(), "Error listener should be called for named logger ERROR");
        assertTrue(capturedLoggers.contains("named-err-logger"),
                "Logger name should be passed to error listener");
    }

    @Test
    void testVKLoggerNameReturnsCorrectName() {
        VKLogger logger = Vostok.Log.logger("my-service");
        assertEquals("my-service", logger.name());
    }

    // =========================================================================
    // 默认配置（不初始化）行为测试
    // =========================================================================

    @Test
    void testDefaultConfigHasCompressAndSingleFileMode() {
        // VKLogConfig.defaults() 的语义：compress=true，单文件模式
        VKLogConfig def = VKLogConfig.defaults();
        assertTrue(def.isCompressRolledFiles(),
                "defaults() should enable compression");
        assertFalse(def.isAutoCreateLoggerSink(),
                "defaults() should disable per-logger sink creation (single-file mode)");
        assertFalse(def.isThrowOnUnknownLogger(),
                "defaults() should route unknown loggers to default sink, not throw");
    }

    @Test
    void testNoInitAllLoggersRouteToSingleFile() throws Exception {
        // 真正的"不初始化"场景：close 后不调 init/reinit，只通过 setter 覆盖 outputDir。
        // setter 内部触发 current() → 懒初始化走 VKLogConfig.defaults()（单文件模式）。
        Vostok.Log.close();
        // 不调 init() / reinit()，仅重定向到可验证的临时目录
        Vostok.Log.setOutputDir(dir.toString());
        Vostok.Log.setConsoleEnabled(false);

        // 静态 API 日志
        Vostok.Log.info("static-log");
        // 命名 logger：单文件模式下路由到默认 sink（vostok.log），不创建独立文件
        Vostok.Log.logger("payment").info("payment-log");
        Vostok.Log.logger("order").warn("order-log");
        Vostok.Log.flush();

        // 只有 vostok.log，不存在 payment.log / order.log
        assertTrue(Files.exists(dir.resolve("vostok.log")),
                "Default log file vostok.log should be created");
        assertFalse(Files.exists(dir.resolve("payment.log")),
                "In single-file mode, payment.log should not be created");
        assertFalse(Files.exists(dir.resolve("order.log")),
                "In single-file mode, order.log should not be created");

        // 所有内容写入 vostok.log，且日志行仍包含正确的 logger 名标识
        String text = Files.readString(dir.resolve("vostok.log"));
        assertTrue(text.contains("static-log"),    "Static API log should appear in vostok.log");
        assertTrue(text.contains("payment-log"),   "Named logger log should appear in vostok.log");
        assertTrue(text.contains("order-log"),     "Named logger log should appear in vostok.log");
        assertTrue(text.contains("[payment]"),     "Logger name [payment] should appear in log line");
        assertTrue(text.contains("[order]"),       "Logger name [order] should appear in log line");
    }

    @Test
    void testNoInitLevelFilterAppliedToAllLoggers() throws Exception {
        // 不初始化场景：通过 setter 调整级别，验证单文件模式下级别过滤对所有命名 logger 生效
        Vostok.Log.close();
        Vostok.Log.setOutputDir(dir.toString());
        Vostok.Log.setConsoleEnabled(false);
        Vostok.Log.setLevel(VKLogLevel.WARN);  // 全局阈值 WARN

        Vostok.Log.logger("svc").info("should-be-filtered");  // INFO < WARN → 过滤
        Vostok.Log.logger("svc").warn("should-pass");          // WARN >= WARN → 通过
        Vostok.Log.flush();

        String text = Files.readString(dir.resolve("vostok.log"));
        assertFalse(text.contains("should-be-filtered"),
                "INFO log should be filtered by WARN threshold in single-file mode");
        assertTrue(text.contains("should-pass"),
                "WARN log should pass through in single-file mode");
    }

    @Test
    void testExplicitInitWithAutoCreateSinkCreatesPerLoggerFiles() throws Exception {
        // 显式 init + autoCreateLoggerSink=true（new VKLogConfig() 的字段默认值）→ 每个 logger 独立文件
        // 这是 setUp() 中 new VKLogConfig() 的默认行为，验证与 defaults() 的区别
        Vostok.Log.logger("svc-a").info("a-log");
        Vostok.Log.info("default-log");
        Vostok.Log.flush();

        assertTrue(Files.exists(dir.resolve("svc-a.log")),
                "Explicit init with autoCreateLoggerSink=true should create per-logger files");
        assertTrue(Files.exists(dir.resolve("test.log")),
                "Default sink file should exist");
        assertTrue(Files.readString(dir.resolve("svc-a.log")).contains("a-log"));
        assertTrue(Files.readString(dir.resolve("test.log")).contains("default-log"));
    }

    @Test
    void testCustomFormatterViaPerLoggerSinkWithMdc() throws Exception {
        List<String> capturedLoggerNames = new CopyOnWriteArrayList<>();
        Vostok.Log.close();
        Vostok.Log.init(new VKLogConfig()
                .consoleEnabled(false)
                .outputDir(dir.toString())
                .filePrefix("test")
                .registerLogger("fmt-logger", new VKLogSinkConfig()
                        .formatter((level, loggerName, msg, t, ts, mdc) -> {
                            capturedLoggerNames.add(loggerName);
                            return "FMT:" + msg + "\n";
                        })));

        VKLogMDC.put("rId", "r-99");
        Vostok.Log.logger("fmt-logger").info("per-sink-fmt");
        Vostok.Log.flush();
        VKLogMDC.clear();

        assertFalse(capturedLoggerNames.isEmpty());
        assertEquals("fmt-logger", capturedLoggerNames.get(0));
        String text = Files.readString(dir.resolve("fmt-logger.log"));
        assertTrue(text.contains("FMT:per-sink-fmt"));
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

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
