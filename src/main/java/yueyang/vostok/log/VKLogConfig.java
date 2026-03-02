package yueyang.vostok.log;

import yueyang.vostok.util.VKAssert;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Log module configuration.
 */
public class VKLogConfig {
    private VKLogLevel level = VKLogLevel.INFO;
    private String outputDir = "logs";
    private String filePrefix = "vostok";
    private long maxFileSizeBytes = 64L * 1024 * 1024;
    private int maxBackups = 20;
    private int maxBackupDays = 30;
    private long maxTotalSizeBytes = 1024L * 1024 * 1024;
    private boolean consoleEnabled = true;
    private int queueCapacity = 1 << 15;
    private VKLogQueueFullPolicy queueFullPolicy = VKLogQueueFullPolicy.DROP;
    private long flushIntervalMs = 1000;
    private int flushBatchSize = 256;
    private long shutdownTimeoutMs = 5000;
    private VKLogFsyncPolicy fsyncPolicy = VKLogFsyncPolicy.NEVER;
    private VKLogRollInterval rollInterval = VKLogRollInterval.DAILY;
    private boolean compressRolledFiles = false;
    private long fileRetryIntervalMs = 3000;
    private String defaultLoggerName = "app";
    private boolean autoCreateLoggerSink = true;
    private final Set<String> preRegisteredLoggers = new LinkedHashSet<>();
    private final Map<String, VKLogSinkConfig> loggerSinkConfigs = new LinkedHashMap<>();
    /** 自定义格式化器，null 表示使用内置默认格式。 */
    private VKLogFormatter formatter = null;
    /** ERROR 级别事件监听器，null 表示不启用。 */
    private VKLogErrorListener errorListener = null;
    /** 自定义输出 Backend，null 表示使用内置文件 + 控制台路径。 */
    private VKLogBackend backend = null;
    /** 是否对控制台输出启用 ANSI 颜色（开发环境建议开启）。 */
    private boolean consoleColor = false;
    /**
     * 是否在 {@code autoCreateLoggerSink=false} 时对未注册的 logger 抛出异常。
     * <p>
     * 默认 {@code false}：未注册的 logger 路由到默认 sink（单文件模式）。
     * 设为 {@code true} 后，调用未预注册的 logger 名时抛 {@link IllegalArgumentException}，
     * 用于需要严格管控 logger 命名的场景。
     */
    private boolean throwOnUnknownLogger = false;

    /**
     * 返回"不初始化"场景的默认配置。
     * <p>
     * 与 {@code new VKLogConfig()} 的区别：
     * <ul>
     *   <li>{@code compressRolledFiles=true}：滚动文件自动 gzip 压缩</li>
     *   <li>{@code autoCreateLoggerSink=false}：所有日志（含命名 logger）统一写入 {@code vostok.log}，
     *       而非为每个 logger 创建独立文件</li>
     * </ul>
     * 此配置在未调用 {@link yueyang.vostok.log.VostokLog#init} 时由懒初始化路径自动采用。
     */
    public static VKLogConfig defaults() {
        return new VKLogConfig()
                .compressRolledFiles(true)
                .autoCreateLoggerSink(false);
    }

    public VKLogConfig copy() {
        return new VKLogConfig()
                .level(level)
                .outputDir(outputDir)
                .filePrefix(filePrefix)
                .maxFileSizeBytes(maxFileSizeBytes)
                .maxBackups(maxBackups)
                .maxBackupDays(maxBackupDays)
                .maxTotalSizeBytes(maxTotalSizeBytes)
                .consoleEnabled(consoleEnabled)
                .queueCapacity(queueCapacity)
                .queueFullPolicy(queueFullPolicy)
                .flushIntervalMs(flushIntervalMs)
                .flushBatchSize(flushBatchSize)
                .shutdownTimeoutMs(shutdownTimeoutMs)
                .fsyncPolicy(fsyncPolicy)
                .rollInterval(rollInterval)
                .compressRolledFiles(compressRolledFiles)
                .fileRetryIntervalMs(fileRetryIntervalMs)
                .defaultLoggerName(defaultLoggerName)
                .autoCreateLoggerSink(autoCreateLoggerSink)
                .preRegisteredLoggers(preRegisteredLoggers)
                .loggerSinkConfigs(loggerSinkConfigs)
                .formatter(formatter)
                .errorListener(errorListener)
                .consoleColor(consoleColor)
                .throwOnUnknownLogger(throwOnUnknownLogger)
                .backend(backend);
    }

    public VKLogLevel getLevel() {
        return level;
    }

    public VKLogConfig level(VKLogLevel level) {
        VKAssert.notNull(level, "level is null");
        this.level = level;
        return this;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public VKLogConfig outputDir(String outputDir) {
        VKAssert.notBlank(outputDir, "outputDir is blank");
        this.outputDir = outputDir;
        return this;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    public VKLogConfig filePrefix(String filePrefix) {
        VKAssert.notBlank(filePrefix, "filePrefix is blank");
        this.filePrefix = filePrefix;
        return this;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public VKLogConfig maxFileSizeMb(long mb) {
        VKAssert.isTrue(mb > 0, "maxFileSizeMb must be > 0");
        this.maxFileSizeBytes = mb * 1024 * 1024;
        return this;
    }

    public VKLogConfig maxFileSizeBytes(long bytes) {
        VKAssert.isTrue(bytes > 0, "maxFileSizeBytes must be > 0");
        this.maxFileSizeBytes = bytes;
        return this;
    }

    public int getMaxBackups() {
        return maxBackups;
    }

    public VKLogConfig maxBackups(int maxBackups) {
        VKAssert.isTrue(maxBackups >= 0, "maxBackups must be >= 0");
        this.maxBackups = maxBackups;
        return this;
    }

    public int getMaxBackupDays() {
        return maxBackupDays;
    }

    public VKLogConfig maxBackupDays(int maxBackupDays) {
        VKAssert.isTrue(maxBackupDays >= 0, "maxBackupDays must be >= 0");
        this.maxBackupDays = maxBackupDays;
        return this;
    }

    public long getMaxTotalSizeBytes() {
        return maxTotalSizeBytes;
    }

    public VKLogConfig maxTotalSizeMb(long mb) {
        VKAssert.isTrue(mb > 0, "maxTotalSizeMb must be > 0");
        this.maxTotalSizeBytes = mb * 1024 * 1024;
        return this;
    }

    public VKLogConfig maxTotalSizeBytes(long bytes) {
        VKAssert.isTrue(bytes > 0, "maxTotalSizeBytes must be > 0");
        this.maxTotalSizeBytes = bytes;
        return this;
    }

    public boolean isConsoleEnabled() {
        return consoleEnabled;
    }

    public VKLogConfig consoleEnabled(boolean consoleEnabled) {
        this.consoleEnabled = consoleEnabled;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public VKLogConfig queueCapacity(int queueCapacity) {
        VKAssert.isTrue(queueCapacity > 0, "queueCapacity must be > 0");
        this.queueCapacity = queueCapacity;
        return this;
    }

    public VKLogQueueFullPolicy getQueueFullPolicy() {
        return queueFullPolicy;
    }

    public VKLogConfig queueFullPolicy(VKLogQueueFullPolicy queueFullPolicy) {
        VKAssert.notNull(queueFullPolicy, "queueFullPolicy is null");
        this.queueFullPolicy = queueFullPolicy;
        return this;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public VKLogConfig flushIntervalMs(long flushIntervalMs) {
        VKAssert.isTrue(flushIntervalMs > 0, "flushIntervalMs must be > 0");
        this.flushIntervalMs = flushIntervalMs;
        return this;
    }

    public int getFlushBatchSize() {
        return flushBatchSize;
    }

    public VKLogConfig flushBatchSize(int flushBatchSize) {
        VKAssert.isTrue(flushBatchSize > 0, "flushBatchSize must be > 0");
        this.flushBatchSize = flushBatchSize;
        return this;
    }

    public long getShutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }

    public VKLogConfig shutdownTimeoutMs(long shutdownTimeoutMs) {
        VKAssert.isTrue(shutdownTimeoutMs > 0, "shutdownTimeoutMs must be > 0");
        this.shutdownTimeoutMs = shutdownTimeoutMs;
        return this;
    }

    public VKLogFsyncPolicy getFsyncPolicy() {
        return fsyncPolicy;
    }

    public VKLogConfig fsyncPolicy(VKLogFsyncPolicy fsyncPolicy) {
        VKAssert.notNull(fsyncPolicy, "fsyncPolicy is null");
        this.fsyncPolicy = fsyncPolicy;
        return this;
    }

    public VKLogRollInterval getRollInterval() {
        return rollInterval;
    }

    public VKLogConfig rollInterval(VKLogRollInterval rollInterval) {
        VKAssert.notNull(rollInterval, "rollInterval is null");
        this.rollInterval = rollInterval;
        return this;
    }

    public boolean isCompressRolledFiles() {
        return compressRolledFiles;
    }

    public VKLogConfig compressRolledFiles(boolean compressRolledFiles) {
        this.compressRolledFiles = compressRolledFiles;
        return this;
    }

    public long getFileRetryIntervalMs() {
        return fileRetryIntervalMs;
    }

    public VKLogConfig fileRetryIntervalMs(long fileRetryIntervalMs) {
        VKAssert.isTrue(fileRetryIntervalMs > 0, "fileRetryIntervalMs must be > 0");
        this.fileRetryIntervalMs = fileRetryIntervalMs;
        return this;
    }

    public String getDefaultLoggerName() {
        return defaultLoggerName;
    }

    public VKLogConfig defaultLoggerName(String defaultLoggerName) {
        VKAssert.notBlank(defaultLoggerName, "defaultLoggerName is blank");
        this.defaultLoggerName = defaultLoggerName.trim();
        return this;
    }

    public boolean isAutoCreateLoggerSink() {
        return autoCreateLoggerSink;
    }

    public VKLogConfig autoCreateLoggerSink(boolean autoCreateLoggerSink) {
        this.autoCreateLoggerSink = autoCreateLoggerSink;
        return this;
    }

    public Set<String> getPreRegisteredLoggers() {
        return Set.copyOf(preRegisteredLoggers);
    }

    public VKLogConfig preRegisteredLoggers(Set<String> names) {
        this.preRegisteredLoggers.clear();
        if (names == null) {
            return this;
        }
        for (String name : names) {
            addPreRegistered(name);
        }
        return this;
    }

    public VKLogConfig registerLogger(String loggerName) {
        addPreRegistered(loggerName);
        return this;
    }

    public VKLogConfig registerLoggers(String... loggerNames) {
        if (loggerNames == null) {
            return this;
        }
        Arrays.stream(loggerNames).forEach(this::addPreRegistered);
        return this;
    }

    public Map<String, VKLogSinkConfig> getLoggerSinkConfigs() {
        Map<String, VKLogSinkConfig> out = new LinkedHashMap<>();
        for (var e : loggerSinkConfigs.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : e.getValue().copy());
        }
        return out;
    }

    public VKLogConfig loggerSinkConfigs(Map<String, VKLogSinkConfig> configs) {
        this.loggerSinkConfigs.clear();
        if (configs == null) {
            return this;
        }
        for (var e : configs.entrySet()) {
            String name = normalizeLoggerName(e.getKey(), "loggerSinkConfigs key");
            VKLogSinkConfig sink = e.getValue();
            this.loggerSinkConfigs.put(name, sink == null ? null : sink.copy());
            this.preRegisteredLoggers.add(name);
        }
        return this;
    }

    public VKLogConfig registerLogger(String loggerName, VKLogSinkConfig sinkConfig) {
        String name = normalizeLoggerName(loggerName, "loggerName");
        this.preRegisteredLoggers.add(name);
        this.loggerSinkConfigs.put(name, sinkConfig == null ? null : sinkConfig.copy());
        return this;
    }

    public VKLogFormatter getFormatter() {
        return formatter;
    }

    /**
     * 设置自定义格式化器。传入 {@code null} 恢复内置默认格式。
     *
     * @see VKLogFormatter
     */
    public VKLogConfig formatter(VKLogFormatter formatter) {
        this.formatter = formatter;
        return this;
    }

    public VKLogErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * 设置 ERROR 级别日志事件监听器。传入 {@code null} 表示禁用。
     * 监听器在 worker 线程调用，必须非阻塞。
     *
     * @see VKLogErrorListener
     */
    public VKLogConfig errorListener(VKLogErrorListener errorListener) {
        this.errorListener = errorListener;
        return this;
    }

    public boolean isConsoleColor() {
        return consoleColor;
    }

    /**
     * 是否对控制台输出启用 ANSI 颜色。
     * TRACE=灰、DEBUG=青、INFO=绿、WARN=黄、ERROR=红。
     */
    public VKLogConfig consoleColor(boolean consoleColor) {
        this.consoleColor = consoleColor;
        return this;
    }

    public boolean isThrowOnUnknownLogger() {
        return throwOnUnknownLogger;
    }

    /**
     * 设置是否对未注册的 logger 名抛出异常（仅当 {@code autoCreateLoggerSink=false} 时生效）。
     * <p>
     * {@code false}（默认）：未注册 logger 路由到默认 sink（单文件模式）。<br>
     * {@code true}：调用未预注册的 logger 名时抛 {@link IllegalArgumentException}，
     * 用于需要严格管控 logger 命名的场景。
     */
    public VKLogBackend getBackend() {
        return backend;
    }

    /**
     * 设置自定义输出 Backend。传入 {@code null} 恢复内置文件 + 控制台路径。
     * 配置 Backend 后，内置文件写入与控制台输出被跳过；{@link VKLogErrorListener} 仍然触发。
     *
     * @see VKLogBackend
     */
    public VKLogConfig backend(VKLogBackend backend) {
        this.backend = backend;
        return this;
    }

    public VKLogConfig throwOnUnknownLogger(boolean throwOnUnknownLogger) {
        this.throwOnUnknownLogger = throwOnUnknownLogger;
        return this;
    }

    private void addPreRegistered(String loggerName) {
        String name = normalizeLoggerName(loggerName, "loggerName");
        this.preRegisteredLoggers.add(name);
    }

    private String normalizeLoggerName(String loggerName, String label) {
        VKAssert.notBlank(loggerName, label + " is blank");
        return loggerName.trim();
    }
}
