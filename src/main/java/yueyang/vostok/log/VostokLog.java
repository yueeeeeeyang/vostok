package yueyang.vostok.log;

import yueyang.vostok.util.VKAssert;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPOutputStream;

/**
 * High-performance async logger for Vostok.
 */
public class VostokLog {
    private static final AsyncEngine ENGINE = new AsyncEngine();
    private static final StackWalker CALLER_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    protected VostokLog() {
    }

    public static void trace(String msg) {
        ENGINE.log(VKLogLevel.TRACE, resolveCaller(), msg, null);
    }

    public static void debug(String msg) {
        ENGINE.log(VKLogLevel.DEBUG, resolveCaller(), msg, null);
    }

    public static void info(String msg) {
        ENGINE.log(VKLogLevel.INFO, resolveCaller(), msg, null);
    }

    public static void warn(String msg) {
        ENGINE.log(VKLogLevel.WARN, resolveCaller(), msg, null);
    }

    public static void error(String msg) {
        ENGINE.log(VKLogLevel.ERROR, resolveCaller(), msg, null);
    }

    public static void error(String msg, Throwable t) {
        ENGINE.log(VKLogLevel.ERROR, resolveCaller(), msg, t);
    }

    public static void trace(String template, Object... args) {
        ENGINE.log(VKLogLevel.TRACE, resolveCaller(), format(template, args), null);
    }

    public static void debug(String template, Object... args) {
        ENGINE.log(VKLogLevel.DEBUG, resolveCaller(), format(template, args), null);
    }

    public static void info(String template, Object... args) {
        ENGINE.log(VKLogLevel.INFO, resolveCaller(), format(template, args), null);
    }

    public static void warn(String template, Object... args) {
        ENGINE.log(VKLogLevel.WARN, resolveCaller(), format(template, args), null);
    }

    public static void error(String template, Object... args) {
        ENGINE.log(VKLogLevel.ERROR, resolveCaller(), format(template, args), null);
    }

    public static void setLevel(VKLogLevel level) {
        ENGINE.setLevel(level);
    }

    public static VKLogLevel level() {
        return ENGINE.level();
    }

    public static void setOutputDir(String outputDir) {
        ENGINE.setOutputDir(outputDir);
    }

    public static void setFilePrefix(String filePrefix) {
        ENGINE.setFilePrefix(filePrefix);
    }

    public static void setMaxFileSizeMb(long mb) {
        ENGINE.setMaxFileSizeMb(mb);
    }

    public static void setMaxFileSizeBytes(long bytes) {
        ENGINE.setMaxFileSizeBytes(bytes);
    }

    public static void setMaxBackups(int maxBackups) {
        ENGINE.setMaxBackups(maxBackups);
    }

    public static void setMaxBackupDays(int maxBackupDays) {
        ENGINE.setMaxBackupDays(maxBackupDays);
    }

    public static void setMaxTotalSizeMb(long mb) {
        ENGINE.setMaxTotalSizeMb(mb);
    }

    public static void setConsoleEnabled(boolean enabled) {
        ENGINE.setConsoleEnabled(enabled);
    }

    public static void setQueueFullPolicy(VKLogQueueFullPolicy policy) {
        ENGINE.setQueueFullPolicy(policy);
    }

    public static void setQueueCapacity(int capacity) {
        ENGINE.setQueueCapacity(capacity);
    }

    public static void setFlushIntervalMs(long flushIntervalMs) {
        ENGINE.setFlushIntervalMs(flushIntervalMs);
    }

    public static void setFlushBatchSize(int flushBatchSize) {
        ENGINE.setFlushBatchSize(flushBatchSize);
    }

    public static void setShutdownTimeoutMs(long shutdownTimeoutMs) {
        ENGINE.setShutdownTimeoutMs(shutdownTimeoutMs);
    }

    public static void setFsyncPolicy(VKLogFsyncPolicy fsyncPolicy) {
        ENGINE.setFsyncPolicy(fsyncPolicy);
    }

    public static void setRollInterval(VKLogRollInterval interval) {
        ENGINE.setRollInterval(interval);
    }

    public static void setCompressRolledFiles(boolean compress) {
        ENGINE.setCompressRolledFiles(compress);
    }

    public static void setFileRetryIntervalMs(long retryIntervalMs) {
        ENGINE.setFileRetryIntervalMs(retryIntervalMs);
    }

    public static long droppedLogs() {
        return ENGINE.droppedLogs();
    }

    public static long fallbackWrites() {
        return ENGINE.fallbackWrites();
    }

    public static long fileWriteErrors() {
        return ENGINE.fileWriteErrors();
    }

    public static void flush() {
        ENGINE.flush();
    }

    public static void shutdown() {
        ENGINE.shutdown();
    }

    public static void resetDefaults() {
        ENGINE.resetDefaults();
    }

    static void resetForTests() {
        ENGINE.resetDefaults();
    }

    private static String resolveCaller() {
        return CALLER_WALKER.walk(stream -> stream
                .map(frame -> frame.getDeclaringClass().getName())
                .filter(name -> !isInternalLogClass(name))
                .findFirst()
                .orElse("unknown"));
    }

    private static boolean isInternalLogClass(String className) {
        return "yueyang.vostok.log.VostokLog".equals(className)
                || className.startsWith("yueyang.vostok.log.VostokLog$")
                || className.startsWith("yueyang.vostok.Vostok$Log")
                || className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("org.junit.")
                || className.startsWith("sun.reflect.")
                || "java.lang.Thread".equals(className);
    }

    private static String format(String template, Object... args) {
        if (template == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + args.length * 8);
        int argIdx = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '}' && argIdx < args.length) {
                Object arg = args[argIdx++];
                sb.append(arg == null ? "null" : arg);
                i++;
                continue;
            }
            sb.append(c);
        }
        while (argIdx < args.length) {
            Object arg = args[argIdx++];
            sb.append(" [").append(arg).append("]");
        }
        return sb.toString();
    }

    private static final class AsyncEngine {
        private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private static final DateTimeFormatter ROLL_TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        private static final DateTimeFormatter KEY_HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
        private static final DateTimeFormatter KEY_DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
        private static final ZoneId ZONE = ZoneId.systemDefault();

        private final AtomicLong dropped = new AtomicLong(0);
        private final AtomicLong fallbackWrites = new AtomicLong(0);
        private final AtomicLong fileWriteErrors = new AtomicLong(0);
        private final Object directWriteLock = new Object();

        private volatile ArrayBlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(1 << 15);
        private volatile int queueCapacity = 1 << 15;
        private volatile VKLogQueueFullPolicy queueFullPolicy = VKLogQueueFullPolicy.DROP;

        private volatile VKLogLevel level = VKLogLevel.INFO;
        private volatile Path outputDir = Path.of("logs");
        private volatile String filePrefix = "vostok";
        private volatile long maxFileSizeBytes = 64L * 1024 * 1024;
        private volatile int maxBackups = 20;
        private volatile int maxBackupDays = 30;
        private volatile long maxTotalSizeBytes = 1024L * 1024 * 1024;
        private volatile boolean consoleEnabled = true;

        private volatile long flushIntervalMs = 1000;
        private volatile int flushBatchSize = 256;
        private volatile long shutdownTimeoutMs = 5000;
        private volatile VKLogFsyncPolicy fsyncPolicy = VKLogFsyncPolicy.NEVER;
        private volatile VKLogRollInterval rollInterval = VKLogRollInterval.DAILY;
        private volatile boolean compressRolledFiles = false;
        private volatile long fileRetryIntervalMs = 3000;

        private volatile boolean reopenRequested = true;
        private volatile long nextFileRetryAt;

        private volatile boolean accepting = true;
        private volatile boolean stopRequested;

        private final Thread worker;

        private BufferedOutputStream stream;
        private FileOutputStream out;
        private FileChannel channel;
        private Path activeFile;
        private String activeRollKey;
        private long activeSize;
        private int rollSeq;

        private AsyncEngine() {
            worker = new Thread(this::runLoop, "vostok-log-writer");
            worker.setDaemon(true);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "vostok-log-shutdown"));
        }

        private void log(VKLogLevel logLevel, String loggerName, String message, Throwable error) {
            if (!accepting) {
                writeDirectFallback(LogEvent.log(logLevel, loggerName, message, error, System.currentTimeMillis()));
                return;
            }
            VKLogLevel threshold = level;
            if (!logLevel.enabled(threshold)) {
                return;
            }
            LogEvent event = LogEvent.log(logLevel, loggerName, message, error, System.currentTimeMillis());
            switch (queueFullPolicy) {
                case BLOCK:
                    enqueueBlocking(event);
                    break;
                case SYNC_FALLBACK:
                    if (!tryEnqueue(event)) {
                        writeDirectFallback(event);
                    }
                    break;
                case DROP:
                default:
                    enqueueDrop(event);
                    break;
            }
        }

        private boolean enqueueDrop(LogEvent event) {
            boolean ok = tryEnqueue(event);
            if (!ok) {
                dropped.incrementAndGet();
            }
            return ok;
        }

        private boolean tryEnqueue(LogEvent event) {
            ArrayBlockingQueue<LogEvent> q = queue;
            return q.offer(event);
        }

        private void enqueueBlocking(LogEvent event) {
            while (accepting && !stopRequested) {
                try {
                    ArrayBlockingQueue<LogEvent> q = queue;
                    if (q.offer(event, 200, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            dropped.incrementAndGet();
        }

        private void setLevel(VKLogLevel level) {
            VKAssert.notNull(level, "level is null");
            this.level = level;
        }

        private VKLogLevel level() {
            return level;
        }

        private void setOutputDir(String outputDir) {
            VKAssert.notBlank(outputDir, "outputDir is blank");
            this.outputDir = Path.of(outputDir);
            reopenRequested = true;
        }

        private void setFilePrefix(String filePrefix) {
            VKAssert.notBlank(filePrefix, "filePrefix is blank");
            this.filePrefix = filePrefix;
            reopenRequested = true;
        }

        private void setMaxFileSizeMb(long mb) {
            VKAssert.isTrue(mb > 0, "maxFileSizeMb must be > 0");
            setMaxFileSizeBytes(mb * 1024 * 1024);
        }

        private void setMaxFileSizeBytes(long bytes) {
            VKAssert.isTrue(bytes > 0, "maxFileSizeBytes must be > 0");
            this.maxFileSizeBytes = bytes;
            reopenRequested = true;
        }

        private void setMaxBackups(int maxBackups) {
            VKAssert.isTrue(maxBackups >= 0, "maxBackups must be >= 0");
            this.maxBackups = maxBackups;
        }

        private void setMaxBackupDays(int maxBackupDays) {
            VKAssert.isTrue(maxBackupDays >= 0, "maxBackupDays must be >= 0");
            this.maxBackupDays = maxBackupDays;
        }

        private void setMaxTotalSizeMb(long mb) {
            VKAssert.isTrue(mb > 0, "maxTotalSizeMb must be > 0");
            this.maxTotalSizeBytes = mb * 1024 * 1024;
        }

        private void setConsoleEnabled(boolean consoleEnabled) {
            this.consoleEnabled = consoleEnabled;
        }

        private void setQueueFullPolicy(VKLogQueueFullPolicy policy) {
            VKAssert.notNull(policy, "queueFullPolicy is null");
            this.queueFullPolicy = policy;
        }

        private synchronized void setQueueCapacity(int capacity) {
            VKAssert.isTrue(capacity > 0, "queueCapacity must be > 0");
            if (capacity == queueCapacity) {
                return;
            }
            ArrayBlockingQueue<LogEvent> next = new ArrayBlockingQueue<>(capacity);
            LogEvent e;
            while ((e = queue.poll()) != null) {
                if (!next.offer(e)) {
                    dropped.incrementAndGet();
                }
            }
            queue = next;
            queueCapacity = capacity;
        }

        private void setFlushIntervalMs(long flushIntervalMs) {
            VKAssert.isTrue(flushIntervalMs > 0, "flushIntervalMs must be > 0");
            this.flushIntervalMs = flushIntervalMs;
        }

        private void setFlushBatchSize(int flushBatchSize) {
            VKAssert.isTrue(flushBatchSize > 0, "flushBatchSize must be > 0");
            this.flushBatchSize = flushBatchSize;
        }

        private void setShutdownTimeoutMs(long shutdownTimeoutMs) {
            VKAssert.isTrue(shutdownTimeoutMs > 0, "shutdownTimeoutMs must be > 0");
            this.shutdownTimeoutMs = shutdownTimeoutMs;
        }

        private void setFsyncPolicy(VKLogFsyncPolicy fsyncPolicy) {
            VKAssert.notNull(fsyncPolicy, "fsyncPolicy is null");
            this.fsyncPolicy = fsyncPolicy;
        }

        private void setRollInterval(VKLogRollInterval interval) {
            VKAssert.notNull(interval, "rollInterval is null");
            this.rollInterval = interval;
            reopenRequested = true;
        }

        private void setCompressRolledFiles(boolean compress) {
            this.compressRolledFiles = compress;
        }

        private void setFileRetryIntervalMs(long retryIntervalMs) {
            VKAssert.isTrue(retryIntervalMs > 0, "fileRetryIntervalMs must be > 0");
            this.fileRetryIntervalMs = retryIntervalMs;
        }

        private long droppedLogs() {
            return dropped.get();
        }

        private long fallbackWrites() {
            return fallbackWrites.get();
        }

        private long fileWriteErrors() {
            return fileWriteErrors.get();
        }

        private void flush() {
            if (stopRequested) {
                return;
            }
            CountDownLatch latch = new CountDownLatch(1);
            enqueueControl(LogEvent.flush(latch));
            awaitLatch(latch, 3_000);
        }

        private void shutdown() {
            if (stopRequested) {
                return;
            }
            accepting = false;
            stopRequested = true;
            worker.interrupt();
            try {
                worker.join(shutdownTimeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void resetDefaults() {
            level = VKLogLevel.INFO;
            outputDir = Path.of("logs");
            filePrefix = "vostok";
            maxFileSizeBytes = 64L * 1024 * 1024;
            maxBackups = 20;
            maxBackupDays = 30;
            maxTotalSizeBytes = 1024L * 1024 * 1024;
            consoleEnabled = true;
            queueFullPolicy = VKLogQueueFullPolicy.DROP;
            flushIntervalMs = 1000;
            flushBatchSize = 256;
            fsyncPolicy = VKLogFsyncPolicy.NEVER;
            rollInterval = VKLogRollInterval.DAILY;
            compressRolledFiles = false;
            fileRetryIntervalMs = 3000;
            reopenRequested = true;
            nextFileRetryAt = 0;
            dropped.set(0);
            fallbackWrites.set(0);
            fileWriteErrors.set(0);

            while (queue.poll() != null) {
                // clear queue
            }
            flush();
        }

        private void enqueueControl(LogEvent event) {
            while (!stopRequested) {
                try {
                    ArrayBlockingQueue<LogEvent> q = queue;
                    if (q.offer(event, 200, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void awaitLatch(CountDownLatch latch, long timeoutMs) {
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void runLoop() {
            long lastFlushAt = System.currentTimeMillis();
            int dirtyCount = 0;
            while (true) {
                if (stopRequested && queue.isEmpty()) {
                    break;
                }
                try {
                    LogEvent event = queue.poll(200, TimeUnit.MILLISECONDS);
                    long now = System.currentTimeMillis();
                    if (event == null) {
                        if (dirtyCount > 0 && now - lastFlushAt >= flushIntervalMs) {
                            flushStream();
                            dirtyCount = 0;
                            lastFlushAt = now;
                        }
                        continue;
                    }

                    if (event.kind == EventKind.FLUSH) {
                        flushStream();
                        if (event.latch != null) {
                            event.latch.countDown();
                        }
                        lastFlushAt = now;
                        dirtyCount = 0;
                        continue;
                    }

                    writeLog(event);
                    dirtyCount++;
                    if (dirtyCount >= flushBatchSize || now - lastFlushAt >= flushIntervalMs) {
                        flushStream();
                        dirtyCount = 0;
                        lastFlushAt = now;
                    }
                } catch (InterruptedException ignore) {
                    // wakeup for shutdown/reconfig
                } catch (Exception e) {
                    System.err.println("[VostokLog] writer error: " + e.getMessage());
                }
            }
            flushStream();
            closeStream();
        }

        private void writeLog(LogEvent event) {
            String text = formatLine(event);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            boolean fileOk = writeToFile(bytes, event.ts);

            if (!fileOk) {
                fallbackWrites.incrementAndGet();
                writeToStderr(text);
                return;
            }

            if (consoleEnabled) {
                writeToConsole(event.level, text);
            }
        }

        private boolean writeToFile(byte[] bytes, long ts) {
            long now = System.currentTimeMillis();
            if (stream == null && now < nextFileRetryAt) {
                return false;
            }

            try {
                ensureOpen(ts, bytes.length);
                if (stream == null) {
                    return false;
                }
                stream.write(bytes);
                activeSize += bytes.length;
                if (fsyncPolicy == VKLogFsyncPolicy.EVERY_WRITE) {
                    forceChannel();
                }
                return true;
            } catch (Exception e) {
                markFileError(e);
                return false;
            }
        }

        private void writeDirectFallback(LogEvent event) {
            String text = formatLine(event);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            boolean ok = false;

            synchronized (directWriteLock) {
                try {
                    Files.createDirectories(outputDir);
                    Path p = outputDir.resolve(filePrefix + ".log");
                    Files.write(p, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    ok = true;
                } catch (Exception e) {
                    fileWriteErrors.incrementAndGet();
                }
            }

            if (!ok) {
                writeToStderr(text);
            } else if (consoleEnabled) {
                writeToConsole(event.level, text);
            }
            fallbackWrites.incrementAndGet();
        }

        private void ensureOpen(long ts, int incomingBytes) throws IOException {
            if (reopenRequested) {
                closeStream();
                reopenRequested = false;
            }
            if (stream == null) {
                openStream(ts);
                return;
            }
            if (shouldRotate(ts, incomingBytes)) {
                rotate(ts);
                openStream(ts);
            }
        }

        private boolean shouldRotate(long ts, int incomingBytes) {
            if (activeSize + incomingBytes > maxFileSizeBytes) {
                return true;
            }
            String key = rollKey(ts);
            return !key.equals(activeRollKey);
        }

        private String rollKey(long ts) {
            if (rollInterval == VKLogRollInterval.NONE) {
                return "NO_ROLL";
            }
            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
            if (rollInterval == VKLogRollInterval.HOURLY) {
                return KEY_HOUR_FMT.format(dt);
            }
            return KEY_DAY_FMT.format(dt);
        }

        private void openStream(long ts) throws IOException {
            Files.createDirectories(outputDir);
            activeFile = outputDir.resolve(filePrefix + ".log");
            out = new FileOutputStream(activeFile.toFile(), true);
            channel = out.getChannel();
            stream = new BufferedOutputStream(out, 64 * 1024);
            activeSize = Files.exists(activeFile) ? Files.size(activeFile) : 0L;
            activeRollKey = rollKey(ts);
            nextFileRetryAt = 0;
        }

        private void rotate(long ts) {
            closeStream();
            if (activeFile == null || !Files.exists(activeFile)) {
                return;
            }
            try {
                if (Files.size(activeFile) == 0L) {
                    Files.deleteIfExists(activeFile);
                    return;
                }
                Path rolled = outputDir.resolve(filePrefix + "-" + ROLL_TS_FMT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE)) + "-" + (++rollSeq) + ".log");
                try {
                    Files.move(activeFile, rolled, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception ignore) {
                    Files.move(activeFile, rolled, StandardCopyOption.REPLACE_EXISTING);
                }
                if (compressRolledFiles) {
                    rolled = compressFile(rolled);
                }
                pruneBackups();
            } catch (Exception e) {
                markFileError(e);
            }
        }

        private Path compressFile(Path source) throws IOException {
            Path gz = source.resolveSibling(source.getFileName().toString() + ".gz");
            try (InputStream in = Files.newInputStream(source);
                 OutputStream fout = Files.newOutputStream(gz, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 GZIPOutputStream out = new GZIPOutputStream(fout)) {
                in.transferTo(out);
            }
            Files.deleteIfExists(source);
            return gz;
        }

        private void pruneBackups() {
            try {
                List<Path> files = listRolledFiles();
                if (maxBackups >= 0 && files.size() > maxBackups) {
                    for (int i = maxBackups; i < files.size(); i++) {
                        Files.deleteIfExists(files.get(i));
                    }
                }

                if (maxBackupDays > 0) {
                    long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxBackupDays);
                    for (Path p : listRolledFiles()) {
                        if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                            Files.deleteIfExists(p);
                        }
                    }
                }

                if (maxTotalSizeBytes > 0) {
                    long total = 0;
                    for (Path p : listRolledFiles()) {
                        long size = Files.size(p);
                        if (total + size <= maxTotalSizeBytes) {
                            total += size;
                            continue;
                        }
                        Files.deleteIfExists(p);
                    }
                }
            } catch (Exception e) {
                markFileError(e);
            }
        }

        private List<Path> listRolledFiles() throws IOException {
            try (var stream = Files.list(outputDir)) {
                return stream
                        .filter(this::isRolledFile)
                        .sorted(Comparator.comparingLong(this::lastModified).reversed())
                        .toList();
            }
        }

        private boolean isRolledFile(Path p) {
            String name = p.getFileName().toString();
            return name.startsWith(filePrefix + "-")
                    && (name.endsWith(".log") || name.endsWith(".log.gz"));
        }

        private long lastModified(Path p) {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }

        private void flushStream() {
            try {
                if (stream != null) {
                    stream.flush();
                    if (fsyncPolicy == VKLogFsyncPolicy.EVERY_FLUSH || fsyncPolicy == VKLogFsyncPolicy.EVERY_WRITE) {
                        forceChannel();
                    }
                }
            } catch (Exception e) {
                markFileError(e);
            }
        }

        private void forceChannel() throws IOException {
            if (channel != null) {
                channel.force(false);
            }
        }

        private void closeStream() {
            try {
                if (stream != null) {
                    stream.flush();
                    stream.close();
                }
            } catch (IOException ignore) {
            } finally {
                try {
                    if (channel != null) {
                        channel.close();
                    }
                } catch (IOException ignore) {
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException ignore) {
                }
                stream = null;
                channel = null;
                out = null;
                activeSize = 0L;
                activeRollKey = null;
            }
        }

        private void markFileError(Exception e) {
            fileWriteErrors.incrementAndGet();
            nextFileRetryAt = System.currentTimeMillis() + fileRetryIntervalMs;
            closeStream();
            System.err.println("[VostokLog] file write failed: " + e.getMessage());
        }

        private String formatLine(LogEvent event) {
            String ts = TS_FMT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.ts), ZONE));
            StringBuilder sb = new StringBuilder(256);
            sb.append(ts).append(" [").append(event.level).append("] ");
            sb.append("[").append(event.loggerName).append("] ");
            sb.append(event.msg == null ? "" : event.msg);
            if (event.error != null) {
                sb.append('\n').append(stackTrace(event.error));
            }
            sb.append('\n');
            return sb.toString();
        }

        private String stackTrace(Throwable t) {
            StringBuilder sb = new StringBuilder(512);
            sb.append(t);
            for (StackTraceElement e : t.getStackTrace()) {
                sb.append("\n\tat ").append(e);
            }
            Throwable cause = t.getCause();
            if (cause != null && cause != t) {
                sb.append("\nCaused by: ").append(stackTrace(cause));
            }
            return sb.toString();
        }

        private void writeToConsole(VKLogLevel level, String text) {
            if (level == VKLogLevel.ERROR) {
                System.err.print(text);
                return;
            }
            System.out.print(text);
        }

        private void writeToStderr(String text) {
            System.err.print(text);
        }
    }

    private enum EventKind {
        LOG,
        FLUSH
    }

    private static final class LogEvent {
        private final EventKind kind;
        private final VKLogLevel level;
        private final String loggerName;
        private final String msg;
        private final Throwable error;
        private final long ts;
        private final CountDownLatch latch;

        private LogEvent(EventKind kind, VKLogLevel level, String loggerName, String msg, Throwable error, long ts, CountDownLatch latch) {
            this.kind = kind;
            this.level = level;
            this.loggerName = loggerName;
            this.msg = msg;
            this.error = error;
            this.ts = ts;
            this.latch = latch;
        }

        private static LogEvent log(VKLogLevel level, String loggerName, String msg, Throwable error, long ts) {
            return new LogEvent(EventKind.LOG, level, loggerName, msg, error, ts, null);
        }

        private static LogEvent flush(CountDownLatch latch) {
            return new LogEvent(EventKind.FLUSH, VKLogLevel.INFO, "flush", "", null, System.currentTimeMillis(), latch);
        }
    }
}
