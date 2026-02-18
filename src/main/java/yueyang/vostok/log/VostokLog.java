package yueyang.vostok.log;

import yueyang.vostok.util.VKAssert;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * High performance async logger for Vostok.
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

    public static void setMaxBackups(int maxBackups) {
        ENGINE.setMaxBackups(maxBackups);
    }

    public static void setConsoleEnabled(boolean enabled) {
        ENGINE.setConsoleEnabled(enabled);
    }

    public static long droppedLogs() {
        return ENGINE.droppedLogs();
    }

    public static void flush() {
        ENGINE.flush();
    }

    public static void shutdown() {
        ENGINE.shutdown();
    }

    private static String resolveCaller() {
        return CALLER_WALKER.walk(stream -> stream
                .map(frame -> frame.getDeclaringClass().getName())
                .filter(name -> !isInternalLogClass(name))
                .findFirst()
                .orElse("unknown"));
    }

    private static boolean isInternalLogClass(String className) {
        return className.startsWith("yueyang.vostok.log.")
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
        private static final DateTimeFormatter ROLL_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        private static final ZoneId ZONE = ZoneId.systemDefault();
        private static final int DEFAULT_QUEUE_SIZE = 1 << 15;

        private final ArrayBlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(DEFAULT_QUEUE_SIZE);
        private final List<LogEvent> batch = new ArrayList<>(512);
        private final AtomicLong dropped = new AtomicLong(0);
        private final Thread worker;
        private volatile boolean running = true;

        private volatile VKLogLevel level = VKLogLevel.INFO;
        private volatile Path outputDir = Path.of("logs");
        private volatile String filePrefix = "vostok";
        private volatile long maxFileSizeBytes = 64L * 1024 * 1024;
        private volatile int maxBackups = 20;
        private volatile boolean consoleEnabled = true;
        private volatile boolean reopenRequested = true;

        private BufferedOutputStream stream;
        private Path activeFile;
        private LocalDate activeDate;
        private long activeSize;
        private int rollSeq;

        private AsyncEngine() {
            worker = new Thread(this::runLoop, "vostok-log-writer");
            worker.setDaemon(true);
            worker.start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "vostok-log-shutdown"));
        }

        private void log(VKLogLevel logLevel, String loggerName, String message, Throwable error) {
            VKLogLevel threshold = level;
            if (!logLevel.enabled(threshold)) {
                return;
            }
            LogEvent event = LogEvent.log(logLevel, loggerName, message, error, System.currentTimeMillis());
            if (!queue.offer(event)) {
                dropped.incrementAndGet();
            }
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
            this.maxFileSizeBytes = mb * 1024 * 1024;
            reopenRequested = true;
        }

        private void setMaxBackups(int maxBackups) {
            VKAssert.isTrue(maxBackups >= 0, "maxBackups must be >= 0");
            this.maxBackups = maxBackups;
        }

        private void setConsoleEnabled(boolean consoleEnabled) {
            this.consoleEnabled = consoleEnabled;
        }

        private long droppedLogs() {
            return dropped.get();
        }

        private void flush() {
            CountDownLatch latch = new CountDownLatch(1);
            if (!queue.offer(LogEvent.flush(latch))) {
                latch.countDown();
            }
            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void shutdown() {
            if (!running) {
                return;
            }
            running = false;
            CountDownLatch latch = new CountDownLatch(1);
            queue.offer(LogEvent.shutdown(latch));
            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void runLoop() {
            while (running || !queue.isEmpty()) {
                try {
                    LogEvent first = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (first == null) {
                        continue;
                    }
                    batch.clear();
                    batch.add(first);
                    queue.drainTo(batch, 511);
                    writeBatch(batch);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[VostokLog] writer error: " + e.getMessage());
                }
            }
            closeStream();
        }

        private void writeBatch(List<LogEvent> events) {
            for (LogEvent event : events) {
                if (event.kind == EventKind.FLUSH) {
                    flushStream();
                    if (event.latch != null) {
                        event.latch.countDown();
                    }
                    continue;
                }
                if (event.kind == EventKind.SHUTDOWN) {
                    flushStream();
                    closeStream();
                    if (event.latch != null) {
                        event.latch.countDown();
                    }
                    continue;
                }
                writeLog(event);
            }
            flushStream();
        }

        private void writeLog(LogEvent event) {
            String text = formatLine(event);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try {
                ensureOpen(event.ts, bytes.length);
                if (stream != null) {
                    stream.write(bytes);
                    activeSize += bytes.length;
                }
            } catch (Exception e) {
                System.err.println("[VostokLog] write file failed: " + e.getMessage());
            }
            if (consoleEnabled) {
                if (event.level == VKLogLevel.ERROR) {
                    System.err.print(text);
                } else {
                    System.out.print(text);
                }
            }
        }

        private void ensureOpen(long ts, int incomingBytes) throws IOException {
            LocalDate date = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE).toLocalDate();
            if (reopenRequested) {
                closeStream();
                reopenRequested = false;
            }
            if (stream == null) {
                openStream(date);
                return;
            }
            if (!date.equals(activeDate) || activeSize + incomingBytes > maxFileSizeBytes) {
                rotate();
                openStream(date);
            }
        }

        private void openStream(LocalDate date) throws IOException {
            Files.createDirectories(outputDir);
            activeFile = outputDir.resolve(filePrefix + ".log");
            stream = new BufferedOutputStream(Files.newOutputStream(activeFile,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), 64 * 1024);
            activeSize = Files.exists(activeFile) ? Files.size(activeFile) : 0L;
            activeDate = date;
        }

        private void rotate() {
            closeStream();
            if (activeFile == null || !Files.exists(activeFile)) {
                return;
            }
            try {
                if (Files.size(activeFile) == 0L) {
                    Files.deleteIfExists(activeFile);
                    return;
                }
                String rolled = filePrefix + "-" + ROLL_FMT.format(LocalDateTime.now()) + "-" + (++rollSeq) + ".log";
                Path target = outputDir.resolve(rolled);
                try {
                    Files.move(activeFile, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception ignore) {
                    Files.move(activeFile, target, StandardCopyOption.REPLACE_EXISTING);
                }
                pruneBackups();
            } catch (Exception e) {
                System.err.println("[VostokLog] rotate failed: " + e.getMessage());
            }
        }

        private void pruneBackups() {
            if (maxBackups < 0) {
                return;
            }
            try (var stream = Files.list(outputDir)) {
                List<Path> files = stream
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.startsWith(filePrefix + "-") && name.endsWith(".log");
                        })
                        .sorted(Comparator.comparingLong(this::lastModified).reversed())
                        .toList();
                for (int i = maxBackups; i < files.size(); i++) {
                    Files.deleteIfExists(files.get(i));
                }
            } catch (Exception ignore) {
            }
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
                }
            } catch (IOException ignore) {
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
                stream = null;
                activeSize = 0L;
                activeDate = null;
            }
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
    }

    private enum EventKind {
        LOG,
        FLUSH,
        SHUTDOWN
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

        private static LogEvent shutdown(CountDownLatch latch) {
            return new LogEvent(EventKind.SHUTDOWN, VKLogLevel.INFO, "shutdown", "", null, System.currentTimeMillis(), latch);
        }
    }
}
