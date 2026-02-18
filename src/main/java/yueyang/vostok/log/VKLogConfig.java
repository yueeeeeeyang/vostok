package yueyang.vostok.log;

import yueyang.vostok.util.VKAssert;

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

    public static VKLogConfig defaults() {
        return new VKLogConfig();
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
                .fileRetryIntervalMs(fileRetryIntervalMs);
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
}
