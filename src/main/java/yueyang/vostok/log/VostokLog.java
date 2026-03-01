package yueyang.vostok.log;

import yueyang.vostok.util.VKAssert;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

/**
 * Vostok 高性能异步日志系统门面类。
 * <p>
 * 提供静态 API（直接调用，自动以调用方类名作为 loggerName）和命名 Logger API（
 * {@link #logger}/{@link #getLogger}，日志路由到独立的文件 sink）。
 * <p>
 * 核心架构：
 * <ul>
 *   <li>{@link LogRouter} — 按 loggerName 路由到对应 {@link AsyncEngine}</li>
 *   <li>{@link AsyncEngine} — 每个命名 logger 独立的单 worker 线程异步引擎，
 *       含阻塞队列、文件写入、滚动、压缩、清理</li>
 * </ul>
 * <p>
 * 特性：
 * <ul>
 *   <li>级别过滤（在调用端提前拦截，避免 StackWalker 和格式化开销）</li>
 *   <li>MDC 上下文自动捕获（{@link VKLogMDC}）</li>
 *   <li>可插拔格式化器（{@link VKLogFormatter}）</li>
 *   <li>ERROR 级别告警回调（{@link VKLogErrorListener}）</li>
 *   <li>控制台 ANSI 彩色输出</li>
 *   <li>文件滚动（大小 + 时间周期：HOURLY / DAILY / WEEKLY）</li>
 *   <li>异步 gzip 压缩（不阻塞 worker 线程）</li>
 *   <li>三阶段备份清理（按数量、按天数、按总大小）</li>
 *   <li>队列满策略：DROP / BLOCK / SYNC_FALLBACK</li>
 *   <li>fsync 策略：NEVER / EVERY_FLUSH / EVERY_WRITE</li>
 * </ul>
 */
public class VostokLog {

    private static final Object LOCK = new Object();
    private static volatile LogRouter ROUTER;
    private static volatile VKLogConfig CONFIG = VKLogConfig.defaults();
    private static volatile boolean INITIALIZED;

    /**
     * StackWalker 用于从调用栈中提取调用方类名。
     * 只在静态 API（logAuto 路径）且级别启用时才调用，开销可接受。
     */
    private static final StackWalker CALLER_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final String DEFAULT_LOGGER_KEY = "__default__";

    static {
        // JVM 退出时确保 worker 线程优雅完成剩余队列
        Runtime.getRuntime().addShutdownHook(new Thread(VostokLog::close, "vostok-log-shutdown"));
    }

    protected VostokLog() {
    }

    // -------------------------------------------------------------------------
    // 初始化 / 生命周期
    // -------------------------------------------------------------------------

    public static void init() {
        init(VKLogConfig.defaults());
    }

    public static void init(VKLogConfig config) {
        VKAssert.notNull(config, "VKLogConfig is null");
        synchronized (LOCK) {
            if (INITIALIZED) {
                return;
            }
            CONFIG = config.copy();
            ROUTER = new LogRouter(CONFIG);
            INITIALIZED = true;
        }
    }

    public static void reinit(VKLogConfig config) {
        VKAssert.notNull(config, "VKLogConfig is null");
        LogRouter old;
        synchronized (LOCK) {
            CONFIG = config.copy();
            old = ROUTER;
            ROUTER = new LogRouter(CONFIG);
            INITIALIZED = true;
        }
        if (old != null) {
            old.close();
        }
    }

    public static void close() {
        LogRouter old;
        synchronized (LOCK) {
            old = ROUTER;
            ROUTER = null;
            INITIALIZED = false;
            // 重置 CONFIG，使 close() 后的懒初始化路径重新使用 defaults()，
            // 而非保留上一次 init/set* 留下的残留配置。
            CONFIG = VKLogConfig.defaults();
        }
        if (old != null) {
            old.close();
        }
    }

    public static boolean initialized() {
        return INITIALIZED;
    }

    // -------------------------------------------------------------------------
    // 命名 Logger 获取
    // -------------------------------------------------------------------------

    public static VKLogger logger(String loggerName) {
        return current().logger(loggerName);
    }

    public static VKLogger getLogger(String loggerName) {
        return current().logger(loggerName);
    }

    // -------------------------------------------------------------------------
    // 静态日志 API（调用方类名自动作为 loggerName）
    // -------------------------------------------------------------------------

    public static void trace(String msg) {
        LogRouter r = current();
        if (!VKLogLevel.TRACE.enabled(r.level())) return;
        r.logAuto(VKLogLevel.TRACE, resolveCaller(), msg, null);
    }

    public static void debug(String msg) {
        LogRouter r = current();
        if (!VKLogLevel.DEBUG.enabled(r.level())) return;
        r.logAuto(VKLogLevel.DEBUG, resolveCaller(), msg, null);
    }

    public static void info(String msg) {
        LogRouter r = current();
        if (!VKLogLevel.INFO.enabled(r.level())) return;
        r.logAuto(VKLogLevel.INFO, resolveCaller(), msg, null);
    }

    public static void warn(String msg) {
        LogRouter r = current();
        if (!VKLogLevel.WARN.enabled(r.level())) return;
        r.logAuto(VKLogLevel.WARN, resolveCaller(), msg, null);
    }

    public static void error(String msg) {
        LogRouter r = current();
        if (!VKLogLevel.ERROR.enabled(r.level())) return;
        r.logAuto(VKLogLevel.ERROR, resolveCaller(), msg, null);
    }

    public static void error(String msg, Throwable t) {
        LogRouter r = current();
        if (!VKLogLevel.ERROR.enabled(r.level())) return;
        r.logAuto(VKLogLevel.ERROR, resolveCaller(), msg, t);
    }

    /**
     * TRACE 模板日志。
     * 提前级别检查：若 TRACE 未启用，不执行 StackWalker 和字符串格式化。
     */
    public static void trace(String template, Object... args) {
        LogRouter r = current();
        if (!VKLogLevel.TRACE.enabled(r.level())) return;
        r.logAuto(VKLogLevel.TRACE, resolveCaller(), format(template, args), null);
    }

    public static void debug(String template, Object... args) {
        LogRouter r = current();
        if (!VKLogLevel.DEBUG.enabled(r.level())) return;
        r.logAuto(VKLogLevel.DEBUG, resolveCaller(), format(template, args), null);
    }

    public static void info(String template, Object... args) {
        LogRouter r = current();
        if (!VKLogLevel.INFO.enabled(r.level())) return;
        r.logAuto(VKLogLevel.INFO, resolveCaller(), format(template, args), null);
    }

    public static void warn(String template, Object... args) {
        LogRouter r = current();
        if (!VKLogLevel.WARN.enabled(r.level())) return;
        r.logAuto(VKLogLevel.WARN, resolveCaller(), format(template, args), null);
    }

    public static void error(String template, Object... args) {
        LogRouter r = current();
        if (!VKLogLevel.ERROR.enabled(r.level())) return;
        r.logAuto(VKLogLevel.ERROR, resolveCaller(), format(template, args), null);
    }

    // -------------------------------------------------------------------------
    // 级别查询（对全局默认 sink 级别）
    // -------------------------------------------------------------------------

    public static boolean isTraceEnabled() {
        return VKLogLevel.TRACE.enabled(current().level());
    }

    public static boolean isDebugEnabled() {
        return VKLogLevel.DEBUG.enabled(current().level());
    }

    public static boolean isInfoEnabled() {
        return VKLogLevel.INFO.enabled(current().level());
    }

    public static boolean isWarnEnabled() {
        return VKLogLevel.WARN.enabled(current().level());
    }

    public static boolean isErrorEnabled() {
        return VKLogLevel.ERROR.enabled(current().level());
    }

    // -------------------------------------------------------------------------
    // 运行时配置更新
    // -------------------------------------------------------------------------

    public static void setLevel(VKLogLevel level) {
        updateConfig(cfg -> cfg.level(level));
    }

    /**
     * 运行时动态修改指定命名 Logger 的日志级别，不影响其他 Logger。
     * 若该 Logger 的 sink 尚未创建，会按当前配置创建后再设置级别。
     */
    public static void setLevel(String loggerName, VKLogLevel level) {
        VKAssert.notNull(level, "level is null");
        LogRouter r = ROUTER;
        if (r != null) {
            r.setLoggerLevel(loggerName, level);
        }
    }

    public static VKLogLevel level() {
        LogRouter r = ROUTER;
        return r == null ? CONFIG.getLevel() : r.level();
    }

    public static void setOutputDir(String outputDir) {
        updateConfig(cfg -> cfg.outputDir(outputDir));
    }

    public static void setFilePrefix(String filePrefix) {
        updateConfig(cfg -> cfg.filePrefix(filePrefix));
    }

    public static void setMaxFileSizeMb(long mb) {
        updateConfig(cfg -> cfg.maxFileSizeMb(mb));
    }

    public static void setMaxFileSizeBytes(long bytes) {
        updateConfig(cfg -> cfg.maxFileSizeBytes(bytes));
    }

    public static void setMaxBackups(int maxBackups) {
        updateConfig(cfg -> cfg.maxBackups(maxBackups));
    }

    public static void setMaxBackupDays(int maxBackupDays) {
        updateConfig(cfg -> cfg.maxBackupDays(maxBackupDays));
    }

    public static void setMaxTotalSizeMb(long mb) {
        updateConfig(cfg -> cfg.maxTotalSizeMb(mb));
    }

    public static void setConsoleEnabled(boolean enabled) {
        updateConfig(cfg -> cfg.consoleEnabled(enabled));
    }

    public static void setQueueFullPolicy(VKLogQueueFullPolicy policy) {
        updateConfig(cfg -> cfg.queueFullPolicy(policy));
    }

    public static void setQueueCapacity(int capacity) {
        updateConfig(cfg -> cfg.queueCapacity(capacity));
    }

    public static void setFlushIntervalMs(long flushIntervalMs) {
        updateConfig(cfg -> cfg.flushIntervalMs(flushIntervalMs));
    }

    public static void setFlushBatchSize(int flushBatchSize) {
        updateConfig(cfg -> cfg.flushBatchSize(flushBatchSize));
    }

    public static void setShutdownTimeoutMs(long shutdownTimeoutMs) {
        updateConfig(cfg -> cfg.shutdownTimeoutMs(shutdownTimeoutMs));
    }

    public static void setFsyncPolicy(VKLogFsyncPolicy fsyncPolicy) {
        updateConfig(cfg -> cfg.fsyncPolicy(fsyncPolicy));
    }

    public static void setRollInterval(VKLogRollInterval interval) {
        updateConfig(cfg -> cfg.rollInterval(interval));
    }

    public static void setCompressRolledFiles(boolean compress) {
        updateConfig(cfg -> cfg.compressRolledFiles(compress));
    }

    public static void setFileRetryIntervalMs(long retryIntervalMs) {
        updateConfig(cfg -> cfg.fileRetryIntervalMs(retryIntervalMs));
    }

    /**
     * 设置全局自定义格式化器，传入 {@code null} 恢复默认格式。
     */
    public static void setFormatter(VKLogFormatter formatter) {
        updateConfig(cfg -> cfg.formatter(formatter));
    }

    /**
     * 设置 ERROR 级别日志监听器，传入 {@code null} 表示禁用。
     */
    public static void setErrorListener(VKLogErrorListener listener) {
        updateConfig(cfg -> cfg.errorListener(listener));
    }

    /**
     * 是否对控制台输出启用 ANSI 彩色（仅控制台，不影响文件输出）。
     */
    public static void setConsoleColor(boolean color) {
        updateConfig(cfg -> cfg.consoleColor(color));
    }

    /**
     * 设置是否对未注册的 logger 名抛出异常（仅当 {@code autoCreateLoggerSink=false} 时生效）。
     * <p>
     * {@code false}（默认）：未注册 logger 路由到默认 sink（单文件模式）。<br>
     * {@code true}：调用未预注册的 logger 名时抛 {@link IllegalArgumentException}。
     *
     * @see VKLogConfig#throwOnUnknownLogger(boolean)
     */
    public static void setThrowOnUnknownLogger(boolean throwOnUnknown) {
        updateConfig(cfg -> cfg.throwOnUnknownLogger(throwOnUnknown));
    }

    // -------------------------------------------------------------------------
    // 监控指标
    // -------------------------------------------------------------------------

    public static long droppedLogs() {
        LogRouter r = ROUTER;
        return r == null ? 0L : r.droppedLogs();
    }

    public static long fallbackWrites() {
        LogRouter r = ROUTER;
        return r == null ? 0L : r.fallbackWrites();
    }

    public static long fileWriteErrors() {
        LogRouter r = ROUTER;
        return r == null ? 0L : r.fileWriteErrors();
    }

    /**
     * 当前排队等待异步压缩的文件数量。
     * 可用于测试或监控，等待此值归零表示所有压缩任务已完成。
     */
    public static int pendingCompressions() {
        return AsyncEngine.COMPRESS_PENDING.get();
    }

    // -------------------------------------------------------------------------
    // 操作
    // -------------------------------------------------------------------------

    public static void flush() {
        LogRouter r = ROUTER;
        if (r != null) {
            r.flush();
        }
    }

    public static void shutdown() {
        close();
    }

    public static void resetDefaults() {
        reinit(VKLogConfig.defaults());
    }

    static void resetForTests() {
        reinit(VKLogConfig.defaults());
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /** 懒初始化：首次使用时若未 init 则自动用默认配置创建 Router。 */
    private static LogRouter current() {
        LogRouter r = ROUTER;
        if (r != null) {
            return r;
        }
        synchronized (LOCK) {
            if (ROUTER == null) {
                ROUTER = new LogRouter(CONFIG);
                INITIALIZED = true;
            }
            return ROUTER;
        }
    }

    /**
     * 原子方式更新配置并推送到所有现有 sink。
     * 队列容量等少数字段在 sink 内部二次处理。
     */
    private static void updateConfig(Consumer<VKLogConfig> updater) {
        synchronized (LOCK) {
            VKLogConfig next = CONFIG.copy();
            updater.accept(next);
            CONFIG = next;
            if (ROUTER != null) {
                ROUTER.applyConfig(next);
            }
        }
    }

    /**
     * 从调用栈中提取第一个非内部类的调用方类名。
     * 仅在对应级别已启用时调用，避免不必要的 StackWalker 开销。
     */
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

    /**
     * SLF4J 风格 {@code {}} 占位符模板格式化。
     * 多余参数追加在末尾（用 {@code [value]} 包裹），不足时占位符保留原样。
     */
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
        // 追加未被占位符消耗的多余参数
        while (argIdx < args.length) {
            Object arg = args[argIdx++];
            sb.append(" [").append(arg).append("]");
        }
        return sb.toString();
    }

    /**
     * 校验并规范化 loggerName：非空、trim、不含路径分隔符。
     */
    private static String validateLoggerName(String loggerName) {
        VKAssert.notBlank(loggerName, "loggerName is blank");
        String name = loggerName.trim();
        VKAssert.isTrue(!name.contains("/") && !name.contains("\\"), "loggerName must not contain path separator");
        VKAssert.isTrue(!".".equals(name) && !"..".equals(name), "loggerName is invalid");
        return name;
    }

    // =========================================================================
    // NamedLogger：命名 Logger 实现
    // =========================================================================

    /**
     * 命名 Logger 实现。
     * name 在构造时已经过 {@link #validateLoggerName} 校验规范化，后续调用无需重复校验。
     */
    private static final class NamedLogger implements VKLogger {
        private final String name;
        private final LogRouter router;

        private NamedLogger(String name, LogRouter router) {
            this.name = name;
            this.router = router;
        }

        @Override
        public String name() {
            return name;
        }

        // ---- 级别查询 ----

        @Override
        public boolean isTraceEnabled() {
            return router.isLevelEnabled(name, VKLogLevel.TRACE);
        }

        @Override
        public boolean isDebugEnabled() {
            return router.isLevelEnabled(name, VKLogLevel.DEBUG);
        }

        @Override
        public boolean isInfoEnabled() {
            return router.isLevelEnabled(name, VKLogLevel.INFO);
        }

        @Override
        public boolean isWarnEnabled() {
            return router.isLevelEnabled(name, VKLogLevel.WARN);
        }

        @Override
        public boolean isErrorEnabled() {
            return router.isLevelEnabled(name, VKLogLevel.ERROR);
        }

        // ---- 无模板日志 ----

        @Override
        public void trace(String msg) {
            router.logByLogger(name, VKLogLevel.TRACE, msg, null);
        }

        @Override
        public void debug(String msg) {
            router.logByLogger(name, VKLogLevel.DEBUG, msg, null);
        }

        @Override
        public void info(String msg) {
            router.logByLogger(name, VKLogLevel.INFO, msg, null);
        }

        @Override
        public void warn(String msg) {
            router.logByLogger(name, VKLogLevel.WARN, msg, null);
        }

        @Override
        public void error(String msg) {
            router.logByLogger(name, VKLogLevel.ERROR, msg, null);
        }

        @Override
        public void error(String msg, Throwable t) {
            router.logByLogger(name, VKLogLevel.ERROR, msg, t);
        }

        // ---- 模板日志（在调用端格式化，仅级别启用时执行） ----

        @Override
        public void trace(String template, Object... args) {
            if (router.isLevelEnabled(name, VKLogLevel.TRACE))
                router.logByLogger(name, VKLogLevel.TRACE, format(template, args), null);
        }

        @Override
        public void debug(String template, Object... args) {
            if (router.isLevelEnabled(name, VKLogLevel.DEBUG))
                router.logByLogger(name, VKLogLevel.DEBUG, format(template, args), null);
        }

        @Override
        public void info(String template, Object... args) {
            if (router.isLevelEnabled(name, VKLogLevel.INFO))
                router.logByLogger(name, VKLogLevel.INFO, format(template, args), null);
        }

        @Override
        public void warn(String template, Object... args) {
            if (router.isLevelEnabled(name, VKLogLevel.WARN))
                router.logByLogger(name, VKLogLevel.WARN, format(template, args), null);
        }

        @Override
        public void error(String template, Object... args) {
            if (router.isLevelEnabled(name, VKLogLevel.ERROR))
                router.logByLogger(name, VKLogLevel.ERROR, format(template, args), null);
        }
    }

    // =========================================================================
    // LogRouter：按 loggerName 路由到对应 AsyncEngine
    // =========================================================================

    /**
     * 路由层。每个命名 logger 对应一个 {@link AsyncEngine}（独立队列 + 独立文件）。
     * 默认 sink（key = {@link VostokLog#DEFAULT_LOGGER_KEY}）接收静态 API 的日志。
     */
    private static final class LogRouter {
        private final ConcurrentHashMap<String, AsyncEngine> sinks = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, VKLogger> loggerCache = new ConcurrentHashMap<>();
        private volatile VKLogConfig config;

        private LogRouter(VKLogConfig config) {
            this.config = config.copy();
            sinks.put(DEFAULT_LOGGER_KEY, new AsyncEngine(defaultSinkConfig(this.config)));
            preRegisterFromConfig(this.config);
        }

        /** 静态 API 路径：loggerName 为调用方类名，路由到默认 sink（类名不做 sink 匹配）。 */
        private void logAuto(VKLogLevel level, String loggerName, String message, Throwable error) {
            AsyncEngine sink = sinks.getOrDefault(loggerName, sinks.get(DEFAULT_LOGGER_KEY));
            sink.log(level, loggerName, message, error);
        }

        /**
         * 命名 Logger 路径：name 已在 {@link NamedLogger} 构造时验证，无需重复校验。
         */
        private void logByLogger(String normalizedName, VKLogLevel level, String message, Throwable error) {
            AsyncEngine sink = sinkForLogger(normalizedName);
            sink.log(level, normalizedName, message, error);
        }

        private VKLogger logger(String loggerName) {
            String normalized = validateLoggerName(loggerName);
            sinkForLogger(normalized);
            return loggerCache.computeIfAbsent(normalized, n -> new NamedLogger(n, this));
        }

        /**
         * 查询指定 logger 对应 sink 的当前日志级别。
         * 若 sink 未创建则回退到默认 sink 的级别。
         */
        private boolean isLevelEnabled(String normalizedLoggerName, VKLogLevel level) {
            AsyncEngine sink = sinks.get(normalizedLoggerName);
            if (sink == null) sink = sinks.get(DEFAULT_LOGGER_KEY);
            return sink != null && level.enabled(sink.level);
        }

        /**
         * 动态修改指定 logger 的日志级别。
         * 若 sink 尚未创建，按当前配置创建后再设置级别。
         */
        private void setLoggerLevel(String loggerName, VKLogLevel level) {
            VKAssert.notNull(level, "level is null");
            String normalized = validateLoggerName(loggerName);
            AsyncEngine sink = sinkForLogger(normalized);
            sink.setLevel(level);
        }

        private synchronized void applyConfig(VKLogConfig next) {
            this.config = next.copy();
            for (var entry : sinks.entrySet()) {
                String key = entry.getKey();
                entry.getValue().applyConfig(sinkConfigFor(key, this.config));
            }
            preRegisterFromConfig(this.config);
        }

        private VKLogLevel level() {
            return config.getLevel();
        }

        private long droppedLogs() {
            long total = 0;
            for (AsyncEngine e : sinks.values()) total += e.droppedLogs();
            return total;
        }

        private long fallbackWrites() {
            long total = 0;
            for (AsyncEngine e : sinks.values()) total += e.fallbackWrites();
            return total;
        }

        private long fileWriteErrors() {
            long total = 0;
            for (AsyncEngine e : sinks.values()) total += e.fileWriteErrors();
            return total;
        }

        private void flush() {
            for (AsyncEngine e : sinks.values()) e.flush();
        }

        private void close() {
            for (AsyncEngine e : sinks.values()) e.shutdown();
            sinks.clear();
            loggerCache.clear();
        }

        /**
         * 获取或创建指定 loggerName 对应的 AsyncEngine。
         * <p>
         * 路由规则：
         * <ol>
         *   <li>已存在对应 sink → 直接返回</li>
         *   <li>{@code autoCreateLoggerSink=true} → 惰性创建专属 sink（独立文件）</li>
         *   <li>{@code autoCreateLoggerSink=false, throwOnUnknownLogger=true} → 抛出异常（严格注册模式）</li>
         *   <li>{@code autoCreateLoggerSink=false, throwOnUnknownLogger=false}（默认）→ 路由到默认 sink（单文件模式）</li>
         * </ol>
         */
        private AsyncEngine sinkForLogger(String loggerName) {
            AsyncEngine found = sinks.get(loggerName);
            if (found != null) {
                return found;
            }
            if (!config.isAutoCreateLoggerSink()) {
                if (config.isThrowOnUnknownLogger()) {
                    throw new IllegalArgumentException("Logger sink not registered: " + loggerName);
                }
                // 单文件模式：未注册的 logger 写入默认 sink（vostok.log），日志行仍保留 loggerName 标识
                return sinks.get(DEFAULT_LOGGER_KEY);
            }
            return sinks.computeIfAbsent(loggerName, name -> new AsyncEngine(sinkConfigFor(name, config)));
        }

        private static VKLogConfig defaultSinkConfig(VKLogConfig config) {
            return config.copy();
        }

        /**
         * 为指定 loggerName 构造 sink 配置：从全局配置出发，将 filePrefix 设为 loggerName，
         * 再叠加 {@link VKLogSinkConfig} 中的 per-logger 覆盖项。
         */
        private static VKLogConfig sinkConfigFor(String loggerName, VKLogConfig config) {
            if (DEFAULT_LOGGER_KEY.equals(loggerName)) {
                return defaultSinkConfig(config);
            }
            VKLogConfig sink = config.copy().filePrefix(loggerName);
            VKLogSinkConfig override = config.getLoggerSinkConfigs().get(loggerName);
            if (override != null) {
                applyOverride(sink, loggerName, override);
            }
            return sink;
        }

        /** 将 {@link VKLogSinkConfig} 中非 null 字段覆盖到 sink 配置上。 */
        private static void applyOverride(VKLogConfig sink, String loggerName, VKLogSinkConfig override) {
            if (override.getOutputDir() != null) sink.outputDir(override.getOutputDir());
            if (override.getFilePrefix() != null) sink.filePrefix(override.getFilePrefix());
            if (override.getLevel() != null) sink.level(override.getLevel());
            if (override.getQueueCapacity() != null) sink.queueCapacity(override.getQueueCapacity());
            if (override.getQueueFullPolicy() != null) sink.queueFullPolicy(override.getQueueFullPolicy());
            if (override.getFlushIntervalMs() != null) sink.flushIntervalMs(override.getFlushIntervalMs());
            if (override.getFlushBatchSize() != null) sink.flushBatchSize(override.getFlushBatchSize());
            if (override.getShutdownTimeoutMs() != null) sink.shutdownTimeoutMs(override.getShutdownTimeoutMs());
            if (override.getFsyncPolicy() != null) sink.fsyncPolicy(override.getFsyncPolicy());
            if (override.getRollInterval() != null) sink.rollInterval(override.getRollInterval());
            if (override.getCompressRolledFiles() != null) sink.compressRolledFiles(override.getCompressRolledFiles());
            if (override.getMaxBackups() != null) sink.maxBackups(override.getMaxBackups());
            if (override.getMaxBackupDays() != null) sink.maxBackupDays(override.getMaxBackupDays());
            if (override.getMaxFileSizeBytes() != null) sink.maxFileSizeBytes(override.getMaxFileSizeBytes());
            if (override.getMaxTotalSizeBytes() != null) sink.maxTotalSizeBytes(override.getMaxTotalSizeBytes());
            if (override.getConsoleEnabled() != null) sink.consoleEnabled(override.getConsoleEnabled());
            if (override.getFileRetryIntervalMs() != null) sink.fileRetryIntervalMs(override.getFileRetryIntervalMs());
            if (override.getFormatter() != null) sink.formatter(override.getFormatter());
            if (override.getErrorListener() != null) sink.errorListener(override.getErrorListener());
            if (override.getConsoleColor() != null) sink.consoleColor(override.getConsoleColor());
            // filePrefix 保底：若 override 未指定 filePrefix，用 loggerName
            if (sink.getFilePrefix() == null || sink.getFilePrefix().isBlank()) {
                sink.filePrefix(loggerName);
            }
        }

        private void preRegisterFromConfig(VKLogConfig config) {
            Set<String> names = config.getPreRegisteredLoggers();
            for (String loggerName : names) {
                String normalized = validateLoggerName(loggerName);
                sinks.computeIfAbsent(normalized, name -> new AsyncEngine(sinkConfigFor(name, config)));
            }
            Map<String, VKLogSinkConfig> overrides = config.getLoggerSinkConfigs();
            for (String loggerName : overrides.keySet()) {
                String normalized = validateLoggerName(loggerName);
                sinks.computeIfAbsent(normalized, name -> new AsyncEngine(sinkConfigFor(name, config)));
            }
        }
    }

    // =========================================================================
    // AsyncEngine：单 worker 线程异步日志引擎
    // =========================================================================

    /**
     * 每个命名 logger 对应一个 AsyncEngine 实例。
     * <p>
     * 关键设计：
     * <ul>
     *   <li>所有写文件操作均在单一 worker 线程执行，无需对文件句柄加锁</li>
     *   <li>调用线程通过 {@link ArrayBlockingQueue} 提交事件，与 worker 解耦</li>
     *   <li>队列满时按 {@link VKLogQueueFullPolicy} 执行 DROP / BLOCK / SYNC_FALLBACK</li>
     *   <li>文件滚动（按大小 + 按时间周期）、备份压缩（异步）、备份清理在 worker 内完成</li>
     * </ul>
     */
    private static final class AsyncEngine {

        // ---- 时间格式化常量 ----
        /** 完整时间戳格式（秒级，每秒缓存复用，毫秒位手动拼接）。 */
        private static final DateTimeFormatter TS_SEC_FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final DateTimeFormatter ROLL_TS_FMT =
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        private static final DateTimeFormatter KEY_HOUR_FMT =
                DateTimeFormatter.ofPattern("yyyyMMddHH");
        private static final DateTimeFormatter KEY_DAY_FMT =
                DateTimeFormatter.ofPattern("yyyyMMdd");
        /**
         * ISO 周年 + 周次（如 "2024W03"），跨年边界安全。
         * 使用 'Y'（周年）而非 'y'（历年），防止年末跨年滚动 key 混乱。
         */
        private static final DateTimeFormatter KEY_WEEK_FMT =
                DateTimeFormatter.ofPattern("YYYY'W'ww");
        private static final ZoneId ZONE = ZoneId.systemDefault();

        // ---- ANSI 颜色码 ----
        private static final String ANSI_RESET  = "\u001B[0m";
        private static final String ANSI_GRAY   = "\u001B[90m"; // TRACE
        private static final String ANSI_CYAN   = "\u001B[36m"; // DEBUG
        private static final String ANSI_GREEN  = "\u001B[32m"; // INFO
        private static final String ANSI_YELLOW = "\u001B[33m"; // WARN
        private static final String ANSI_RED    = "\u001B[31m"; // ERROR

        // ---- 异步压缩线程池（全局共享，串行处理，避免并发 IO 冲击磁盘） ----
        /**
         * 单线程执行器，所有 AsyncEngine 实例共享，确保同一时刻只有一个文件在被 gzip 压缩。
         * daemon=true，不阻塞 JVM 正常退出（进行中的压缩任务会被放弃，原 .log 文件仍保留）。
         */
        private static final ExecutorService COMPRESS_EXEC = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vostok-log-compress");
            t.setDaemon(true);
            return t;
        });
        /** 当前等待或正在执行的压缩任务数量，供外部等待或监控。 */
        static final AtomicInteger COMPRESS_PENDING = new AtomicInteger(0);

        // ---- 运行时统计 ----
        private final AtomicLong dropped       = new AtomicLong(0);
        private final AtomicLong fallbackWrites = new AtomicLong(0);
        private final AtomicLong fileWriteErrors = new AtomicLong(0);
        /** SYNC_FALLBACK 路径的写锁，防止多线程并发追加同一文件。 */
        private final Object directWriteLock = new Object();

        // ---- 队列（volatile，setQueueCapacity 时原子替换） ----
        private volatile ArrayBlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(1 << 15);
        private volatile int queueCapacity = 1 << 15;
        private volatile VKLogQueueFullPolicy queueFullPolicy = VKLogQueueFullPolicy.DROP;

        // ---- 日志配置（volatile，applyConfig 热更新） ----
        private volatile VKLogLevel level          = VKLogLevel.INFO;
        private volatile Path outputDir            = Path.of("logs");
        private volatile String filePrefix         = "vostok";
        private volatile long maxFileSizeBytes     = 64L * 1024 * 1024;
        private volatile int maxBackups            = 20;
        private volatile int maxBackupDays         = 30;
        private volatile long maxTotalSizeBytes    = 1024L * 1024 * 1024;
        private volatile boolean consoleEnabled    = true;
        private volatile long flushIntervalMs      = 1000;
        private volatile int flushBatchSize        = 256;
        private volatile long shutdownTimeoutMs    = 5000;
        private volatile VKLogFsyncPolicy fsyncPolicy   = VKLogFsyncPolicy.NEVER;
        private volatile VKLogRollInterval rollInterval = VKLogRollInterval.DAILY;
        private volatile boolean compressRolledFiles    = false;
        private volatile long fileRetryIntervalMs       = 3000;
        private volatile VKLogFormatter formatter       = null;
        private volatile VKLogErrorListener errorListener = null;
        private volatile boolean consoleColor           = false;

        // ---- 文件写入状态（仅 worker 线程访问，无需 volatile） ----
        /** 文件打开/关闭的信号，由 applyConfig 触发后 worker 在下一次循环处理。 */
        private volatile boolean reopenRequested = true;
        /** 文件出错后的重试时间点（epoch ms），0 表示可立即重试。 */
        private volatile long nextFileRetryAt;

        // ---- 生命周期标志 ----
        private volatile boolean accepting     = true; // false 后新日志走 SYNC_FALLBACK
        private volatile boolean stopRequested = false; // worker 排空队列后退出

        private final Thread worker;

        // ---- 文件句柄（仅 worker 线程访问） ----
        private BufferedOutputStream stream;
        private FileOutputStream out;
        private FileChannel channel;
        private Path activeFile;
        private String activeRollKey;
        private long activeSize;
        private int rollSeq;

        // ---- 时间戳缓存（仅 worker 线程访问，避免每条日志都调用 DateTimeFormatter） ----
        private long cachedSecond = -1;
        private String cachedSecondStr = "";

        private AsyncEngine(VKLogConfig config) {
            applyConfig(config);
            worker = new Thread(this::runLoop, "vostok-log-writer-" + filePrefix);
            worker.setDaemon(true);
            worker.start();
        }

        // ---- 配置应用 ----

        private synchronized void applyConfig(VKLogConfig config) {
            setLevel(config.getLevel());
            setOutputDir(config.getOutputDir());
            setFilePrefix(config.getFilePrefix());
            setMaxFileSizeBytes(config.getMaxFileSizeBytes());
            setMaxBackups(config.getMaxBackups());
            setMaxBackupDays(config.getMaxBackupDays());
            setMaxTotalSizeBytes(config.getMaxTotalSizeBytes());
            setConsoleEnabled(config.isConsoleEnabled());
            setQueueFullPolicy(config.getQueueFullPolicy());
            setQueueCapacity(config.getQueueCapacity());
            setFlushIntervalMs(config.getFlushIntervalMs());
            setFlushBatchSize(config.getFlushBatchSize());
            setShutdownTimeoutMs(config.getShutdownTimeoutMs());
            setFsyncPolicy(config.getFsyncPolicy());
            setRollInterval(config.getRollInterval());
            setCompressRolledFiles(config.isCompressRolledFiles());
            setFileRetryIntervalMs(config.getFileRetryIntervalMs());
            setFormatter(config.getFormatter());
            setErrorListener(config.getErrorListener());
            setConsoleColor(config.isConsoleColor());
        }

        // ---- 日志提交 ----

        /**
         * 调用线程提交日志事件。
         * <ol>
         *   <li>若 accepting=false（关闭中），走同步 fallback 写文件。</li>
         *   <li>提前过滤级别，避免无效事件进入队列。</li>
         *   <li>按 queueFullPolicy 处理队列满的情况。</li>
         * </ol>
         */
        private void log(VKLogLevel logLevel, String loggerName, String message, Throwable error) {
            if (!accepting) {
                writeDirectFallback(LogEvent.log(logLevel, loggerName, message, error, System.currentTimeMillis()));
                return;
            }
            if (!logLevel.enabled(level)) {
                return;
            }
            LogEvent event = LogEvent.log(logLevel, loggerName, message, error, System.currentTimeMillis());
            switch (queueFullPolicy) {
                case BLOCK         -> enqueueBlocking(event);
                case SYNC_FALLBACK -> { if (!tryEnqueue(event)) writeDirectFallback(event); }
                default            -> enqueueDrop(event);  // DROP
            }
        }

        private boolean enqueueDrop(LogEvent event) {
            boolean ok = tryEnqueue(event);
            if (!ok) dropped.incrementAndGet();
            return ok;
        }

        private boolean tryEnqueue(LogEvent event) {
            return queue.offer(event);
        }

        /**
         * BLOCK 策略：每 200ms 重试一次，直到成功或引擎关闭。
         * 引擎关闭后放弃并计入 dropped。
         */
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

        // ---- 配置 setters（部分由 worker 线程无锁读取，需 volatile 写） ----

        void setLevel(VKLogLevel level) {
            VKAssert.notNull(level, "level is null");
            this.level = level;
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

        private void setMaxTotalSizeBytes(long bytes) {
            VKAssert.isTrue(bytes > 0, "maxTotalSizeBytes must be > 0");
            this.maxTotalSizeBytes = bytes;
        }

        private void setConsoleEnabled(boolean consoleEnabled) {
            this.consoleEnabled = consoleEnabled;
        }

        private void setQueueFullPolicy(VKLogQueueFullPolicy policy) {
            VKAssert.notNull(policy, "queueFullPolicy is null");
            this.queueFullPolicy = policy;
        }

        /**
         * 扩容/缩容队列。
         * <p>
         * <b>swap-first 策略</b>：先原子替换 queue 引用（后续并发写入新队列），
         * 再将旧队列中的残留事件迁移到新队列，避免迁移期间并发 offer 的事件丢失。
         */
        private synchronized void setQueueCapacity(int capacity) {
            VKAssert.isTrue(capacity > 0, "queueCapacity must be > 0");
            if (capacity == queueCapacity) {
                return;
            }
            // 先 swap：后续并发 offer 直接进新队列
            ArrayBlockingQueue<LogEvent> old = queue;
            queue = new ArrayBlockingQueue<>(capacity);
            queueCapacity = capacity;
            // 再迁移旧队列中的残留事件
            LogEvent e;
            while ((e = old.poll()) != null) {
                if (!queue.offer(e)) {
                    dropped.incrementAndGet();
                }
            }
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

        private void setFormatter(VKLogFormatter formatter) {
            this.formatter = formatter;
        }

        private void setErrorListener(VKLogErrorListener listener) {
            this.errorListener = listener;
        }

        private void setConsoleColor(boolean color) {
            this.consoleColor = color;
        }

        // ---- 统计指标 ----

        private long droppedLogs()     { return dropped.get(); }
        private long fallbackWrites()  { return fallbackWrites.get(); }
        private long fileWriteErrors() { return fileWriteErrors.get(); }

        // ---- 控制操作 ----

        private void flush() {
            if (stopRequested) return;
            CountDownLatch latch = new CountDownLatch(1);
            enqueueControl(LogEvent.flush(latch));
            awaitLatch(latch, 3_000);
        }

        private void shutdown() {
            if (stopRequested) return;
            // accepting=false 先关门：后续新提交走 fallback，不再入队
            accepting = false;
            stopRequested = true;
            worker.interrupt(); // 唤醒可能阻塞在 poll 的 worker
            try {
                worker.join(shutdownTimeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 提交控制事件（如 FLUSH），带重试直到成功或引擎关闭。
         * 控制事件优先级高，不受 queueFullPolicy 限制。
         */
        private void enqueueControl(LogEvent event) {
            while (!stopRequested) {
                try {
                    if (queue.offer(event, 200, TimeUnit.MILLISECONDS)) return;
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

        // ---- Worker 主循环 ----

        /**
         * Worker 线程主循环。
         * <p>
         * 使用 {@code drainTo} 批量消费，减少每条消息的入队/出队开销。
         * 批量处理后统一判断是否需要 flush，兼顾吞吐和延迟。
         */
        private void runLoop() {
            long lastFlushAt = System.currentTimeMillis();
            int dirtyCount = 0;
            List<LogEvent> batch = new ArrayList<>(flushBatchSize);

            while (!(stopRequested && queue.isEmpty())) {
                try {
                    batch.clear();
                    // 尽量批量拉取，减少循环次数
                    int n = queue.drainTo(batch, flushBatchSize);
                    if (n == 0) {
                        // 队列暂空，等待最多 200ms（避免忙轮询）
                        LogEvent single = queue.poll(200, TimeUnit.MILLISECONDS);
                        if (single != null) {
                            batch.add(single);
                        }
                    }

                    long now = System.currentTimeMillis();
                    for (LogEvent event : batch) {
                        if (event.kind == EventKind.FLUSH) {
                            flushStream();
                            if (event.latch != null) event.latch.countDown();
                            dirtyCount = 0;
                            lastFlushAt = now;
                        } else {
                            writeLog(event);
                            dirtyCount++;
                        }
                    }

                    // 满足批量大小或超时则 flush
                    if (dirtyCount > 0 && (dirtyCount >= flushBatchSize || now - lastFlushAt >= flushIntervalMs)) {
                        flushStream();
                        dirtyCount = 0;
                        lastFlushAt = now;
                    }

                } catch (InterruptedException ignore) {
                    // shutdown/reconfig 唤醒，继续循环检查 stopRequested
                } catch (Exception e) {
                    System.err.println("[VostokLog] writer error: " + e.getMessage());
                }
            }
            // 循环退出后确保剩余数据落盘
            flushStream();
            closeStream();
        }

        // ---- 写入逻辑 ----

        /**
         * 写入一条日志事件：格式化 → 写文件 → （可选）写控制台 → 触发 ERROR 监听器。
         */
        private void writeLog(LogEvent event) {
            String text = formatLine(event);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            boolean fileOk = writeToFile(bytes, event.ts);

            if (!fileOk) {
                fallbackWrites.incrementAndGet();
                writeToStderr(text);
            } else if (consoleEnabled) {
                writeToConsole(event.level, text);
            }

            // ERROR 监听器在写入完成后调用（无论写文件成功与否）
            if (event.level == VKLogLevel.ERROR) {
                VKLogErrorListener listener = errorListener;
                if (listener != null) {
                    try {
                        listener.onError(event.loggerName, event.msg, event.error, event.ts);
                    } catch (Exception ignored) {
                        // 监听器异常不能影响 worker 循环
                    }
                }
            }
        }

        /**
         * 写入字节到当前日志文件。
         * 若文件未打开（或出错冷却期中），返回 false 触发 fallback。
         */
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

        /**
         * 同步直写文件（SYNC_FALLBACK 或关闭期间调用）。
         * 使用 {@link #directWriteLock} 防止并发写乱序。
         * 如果文件写入也失败，退化到 stderr。
         */
        private void writeDirectFallback(LogEvent event) {
            if (!event.level.enabled(level)) {
                return;
            }
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

            // ERROR 监听器
            if (event.level == VKLogLevel.ERROR) {
                VKLogErrorListener listener = errorListener;
                if (listener != null) {
                    try {
                        listener.onError(event.loggerName, event.msg, event.error, event.ts);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // ---- 文件滚动 ----

        /**
         * 确保日志文件已打开且不需要滚动。
         * 若 reopenRequested（配置变更触发）先关闭再重新打开。
         */
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

        /** 任一条件满足则触发滚动：超出大小限制，或时间周期跨越（key 变化）。 */
        private boolean shouldRotate(long ts, int incomingBytes) {
            if (activeSize + incomingBytes > maxFileSizeBytes) {
                return true;
            }
            return !rollKey(ts).equals(activeRollKey);
        }

        /**
         * 计算当前时间戳对应的滚动 key。
         * NONE → 固定值（不按时间滚动）；HOURLY / DAILY / WEEKLY 按时间格式化。
         * 相邻两次写入 key 不变则不触发时间滚动。
         */
        private String rollKey(long ts) {
            if (rollInterval == VKLogRollInterval.NONE) {
                return "NO_ROLL";
            }
            LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE);
            return switch (rollInterval) {
                case HOURLY -> KEY_HOUR_FMT.format(dt);
                case DAILY  -> KEY_DAY_FMT.format(dt);
                case WEEKLY -> KEY_WEEK_FMT.format(dt);
                default     -> "NO_ROLL";
            };
        }

        /**
         * 打开（或追加）日志文件。
         * {@code activeSize} 从磁盘读取当前文件大小，以正确判断下次是否需要滚动。
         */
        private void openStream(long ts) throws IOException {
            Files.createDirectories(outputDir);
            activeFile = outputDir.resolve(filePrefix + ".log");
            out = new FileOutputStream(activeFile.toFile(), true);
            channel = out.getChannel();
            stream = new BufferedOutputStream(out, 64 * 1024);
            // FileOutputStream(append=true) 已创建文件，直接读取当前大小
            activeSize = Files.size(activeFile);
            activeRollKey = rollKey(ts);
            nextFileRetryAt = 0;
        }

        /**
         * 将当前文件 rotate 到带时间戳的备份文件名，然后异步压缩（可选），最后修剪旧备份。
         */
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
                Path rolled = outputDir.resolve(
                        filePrefix + "-" + ROLL_TS_FMT.format(
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZONE))
                                + "-" + (++rollSeq) + ".log");
                try {
                    Files.move(activeFile, rolled, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception ignore) {
                    Files.move(activeFile, rolled, StandardCopyOption.REPLACE_EXISTING);
                }

                if (compressRolledFiles) {
                    // 异步压缩：不阻塞 worker 线程，压缩失败仅打印错误，原 .log 文件仍保留
                    final Path toCompress = rolled;
                    COMPRESS_PENDING.incrementAndGet();
                    COMPRESS_EXEC.submit(() -> {
                        try {
                            compressFile(toCompress);
                        } catch (Exception e) {
                            System.err.println("[VostokLog] compression failed: " + e.getMessage());
                        } finally {
                            COMPRESS_PENDING.decrementAndGet();
                        }
                    });
                }
                pruneBackups();
            } catch (Exception e) {
                markFileError(e);
            }
        }

        /** 将 source 文件 gzip 压缩为 source.gz，成功后删除原文件。 */
        private void compressFile(Path source) throws IOException {
            Path gz = source.resolveSibling(source.getFileName().toString() + ".gz");
            try (InputStream in = Files.newInputStream(source);
                 OutputStream fout = Files.newOutputStream(gz,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 GZIPOutputStream gzOut = new GZIPOutputStream(fout)) {
                in.transferTo(gzOut);
            }
            Files.deleteIfExists(source);
        }

        /**
         * 修剪旧备份文件，满足三个约束：最多 maxBackups 个、不超过 maxBackupDays 天、总大小不超过 maxTotalSizeBytes。
         * <p>
         * <b>单次目录扫描</b>：三个阶段复用同一份有序文件列表，避免多次 IO，
         * 且各阶段检查 {@code Files.exists} 跳过已被上一阶段删除的文件，
         * 防止 {@code Files.size()} 对不存在文件抛出 IOException 并触发 {@code markFileError}。
         */
        private void pruneBackups() {
            try {
                // 一次性获取文件列表（按最后修改时间降序 = 最新在前）
                List<Path> files = listRolledFiles();

                // 阶段 1：按数量限制删除最旧的
                if (maxBackups >= 0 && files.size() > maxBackups) {
                    for (int i = maxBackups; i < files.size(); i++) {
                        Files.deleteIfExists(files.get(i));
                    }
                }

                // 阶段 2：按天数限制删除过期文件（跳过已被阶段1删除的）
                if (maxBackupDays > 0) {
                    long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxBackupDays);
                    for (Path p : files) {
                        if (!Files.exists(p)) continue;
                        if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                            Files.deleteIfExists(p);
                        }
                    }
                }

                // 阶段 3：按总大小限制删除（跳过已删除文件，防止 Files.size 抛 NSFE）
                if (maxTotalSizeBytes > 0) {
                    long total = 0;
                    for (Path p : files) {
                        if (!Files.exists(p)) continue;
                        long size;
                        try {
                            size = Files.size(p);
                        } catch (IOException ignore) {
                            // 极端并发场景：文件在 exists 检查后被外部删除，安全跳过
                            continue;
                        }
                        if (total + size <= maxTotalSizeBytes) {
                            total += size;
                        } else {
                            Files.deleteIfExists(p);
                        }
                    }
                }
            } catch (Exception e) {
                markFileError(e);
            }
        }

        /** 列出 outputDir 中属于当前 logger 的所有备份文件，按最后修改时间降序排列（最新在前）。 */
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

        // ---- flush / fsync ----

        private void flushStream() {
            try {
                if (stream != null) {
                    stream.flush();
                    if (fsyncPolicy == VKLogFsyncPolicy.EVERY_FLUSH
                            || fsyncPolicy == VKLogFsyncPolicy.EVERY_WRITE) {
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

        /** 关闭文件句柄，清零相关状态。确保资源释放顺序：stream → channel → out。 */
        private void closeStream() {
            try {
                if (stream != null) {
                    stream.flush();
                    stream.close();
                }
            } catch (IOException ignore) {
            } finally {
                try { if (channel != null) channel.close(); } catch (IOException ignore) { }
                try { if (out != null) out.close(); } catch (IOException ignore) { }
                stream = null;
                channel = null;
                out = null;
                activeSize = 0L;
                activeRollKey = null;
            }
        }

        /**
         * 记录文件错误，进入冷却期（{@code fileRetryIntervalMs}），期间跳过文件写入。
         * 关闭当前文件句柄，下次写入触发重新打开尝试。
         */
        private void markFileError(Exception e) {
            fileWriteErrors.incrementAndGet();
            nextFileRetryAt = System.currentTimeMillis() + fileRetryIntervalMs;
            closeStream();
            System.err.println("[VostokLog] file write failed: " + e.getMessage());
        }

        // ---- 格式化 ----

        /**
         * 格式化日志行。
         * 若配置了自定义 {@link VKLogFormatter}，委托给它处理；否则使用内置默认格式。
         */
        private String formatLine(LogEvent event) {
            VKLogFormatter fmt = formatter;
            if (fmt != null) {
                return fmt.format(event.level, event.loggerName, event.msg, event.error, event.ts, event.mdc);
            }
            return defaultFormat(event);
        }

        /**
         * 内置默认格式：{@code yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [loggerName] {k=v,...} message\n}。
         * MDC 不为空时在消息前插入 {@code {key=value, ...}}。
         */
        private String defaultFormat(LogEvent event) {
            String ts = formatTimestamp(event.ts);
            StringBuilder sb = new StringBuilder(256);
            sb.append(ts)
              .append(" [").append(event.level).append("] ")
              .append("[").append(event.loggerName).append("] ");
            if (!event.mdc.isEmpty()) {
                sb.append("{");
                boolean first = true;
                for (var entry : event.mdc.entrySet()) {
                    if (!first) sb.append(", ");
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                    first = false;
                }
                sb.append("} ");
            }
            sb.append(event.msg == null ? "" : event.msg);
            if (event.error != null) {
                sb.append('\n').append(stackTrace(event.error));
            }
            sb.append('\n');
            return sb.toString();
        }

        /**
         * 带秒级缓存的时间戳格式化。
         * <p>
         * {@link DateTimeFormatter#format} 调用较重，在高频日志下大多数事件属于同一秒。
         * 缓存秒级字符串，只在秒变化时重新格式化，毫秒位手动拼接（避免格式化开销）。
         * 此方法仅在单线程 worker 中调用，缓存字段无并发安全问题。
         */
        private String formatTimestamp(long ts) {
            long sec = ts / 1000;
            if (sec != cachedSecond) {
                cachedSecond = sec;
                cachedSecondStr = TS_SEC_FMT.format(
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(sec), ZONE));
            }
            long ms = ts % 1000;
            // 手动补零，避免 String.format 的开销
            if (ms < 10)  return cachedSecondStr + ".00" + ms;
            if (ms < 100) return cachedSecondStr + ".0"  + ms;
            return cachedSecondStr + "." + ms;
        }

        /**
         * 将异常堆栈格式化为字符串（含 cause chain 和 suppressed exceptions）。
         * 使用 JDK 标准 {@link Throwable#printStackTrace(PrintWriter)} 以正确处理所有嵌套结构。
         */
        private String stackTrace(Throwable t) {
            StringWriter sw = new StringWriter(512);
            t.printStackTrace(new PrintWriter(sw));
            String s = sw.toString();
            // 去掉末尾多余换行（formatLine 会自己追加 \n）
            return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        }

        // ---- 控制台输出 ----

        /**
         * 输出到控制台。
         * ERROR 写 stderr，其余写 stdout。
         * 若 consoleColor=true，用 ANSI 颜色码包裹（仅控制台，不影响文件内容）。
         */
        private void writeToConsole(VKLogLevel level, String text) {
            if (consoleColor) {
                String color = switch (level) {
                    case TRACE -> ANSI_GRAY;
                    case DEBUG -> ANSI_CYAN;
                    case INFO  -> ANSI_GREEN;
                    case WARN  -> ANSI_YELLOW;
                    case ERROR -> ANSI_RED;
                };
                if (level == VKLogLevel.ERROR) {
                    System.err.print(color + text + ANSI_RESET);
                } else {
                    System.out.print(color + text + ANSI_RESET);
                }
            } else {
                if (level == VKLogLevel.ERROR) {
                    System.err.print(text);
                } else {
                    System.out.print(text);
                }
            }
        }

        private void writeToStderr(String text) {
            System.err.print(text);
        }
    }

    // =========================================================================
    // 事件类型与 LogEvent
    // =========================================================================

    private enum EventKind {
        LOG,
        FLUSH
    }

    /**
     * 日志事件（不可变）。
     * MDC 在调用线程的 log() 入口处快照，与事件绑定，保证 worker 线程看到的上下文与调用时一致。
     */
    private static final class LogEvent {
        private final EventKind kind;
        private final VKLogLevel level;
        private final String loggerName;
        private final String msg;
        private final Throwable error;
        private final long ts;
        private final CountDownLatch latch;
        /** 调用线程 MDC 快照，不可修改（构造时已是独立副本）。 */
        private final Map<String, String> mdc;

        private LogEvent(EventKind kind, VKLogLevel level, String loggerName,
                         String msg, Throwable error, long ts,
                         CountDownLatch latch, Map<String, String> mdc) {
            this.kind       = kind;
            this.level      = level;
            this.loggerName = loggerName;
            this.msg        = msg;
            this.error      = error;
            this.ts         = ts;
            this.latch      = latch;
            this.mdc        = mdc;
        }

        /** 创建日志事件，同时在调用线程上捕获 MDC 快照。 */
        private static LogEvent log(VKLogLevel level, String loggerName,
                                     String msg, Throwable error, long ts) {
            return new LogEvent(EventKind.LOG, level, loggerName, msg, error, ts,
                    null, VKLogMDC.snapshot());
        }

        private static LogEvent flush(CountDownLatch latch) {
            return new LogEvent(EventKind.FLUSH, VKLogLevel.INFO, "flush", "", null,
                    System.currentTimeMillis(), latch, Map.of());
        }
    }
}
