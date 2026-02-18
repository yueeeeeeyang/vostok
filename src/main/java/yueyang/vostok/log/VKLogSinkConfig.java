package yueyang.vostok.log;

/**
 * Per-logger sink config override.
 */
public class VKLogSinkConfig {
    private String outputDir;
    private String filePrefix;
    private VKLogLevel level;
    private Integer queueCapacity;
    private VKLogQueueFullPolicy queueFullPolicy;
    private Long flushIntervalMs;
    private Integer flushBatchSize;
    private Long shutdownTimeoutMs;
    private VKLogFsyncPolicy fsyncPolicy;
    private VKLogRollInterval rollInterval;
    private Boolean compressRolledFiles;
    private Integer maxBackups;
    private Integer maxBackupDays;
    private Long maxFileSizeBytes;
    private Long maxTotalSizeBytes;
    private Boolean consoleEnabled;
    private Long fileRetryIntervalMs;

    public VKLogSinkConfig copy() {
        VKLogSinkConfig c = new VKLogSinkConfig();
        c.outputDir = outputDir;
        c.filePrefix = filePrefix;
        c.level = level;
        c.queueCapacity = queueCapacity;
        c.queueFullPolicy = queueFullPolicy;
        c.flushIntervalMs = flushIntervalMs;
        c.flushBatchSize = flushBatchSize;
        c.shutdownTimeoutMs = shutdownTimeoutMs;
        c.fsyncPolicy = fsyncPolicy;
        c.rollInterval = rollInterval;
        c.compressRolledFiles = compressRolledFiles;
        c.maxBackups = maxBackups;
        c.maxBackupDays = maxBackupDays;
        c.maxFileSizeBytes = maxFileSizeBytes;
        c.maxTotalSizeBytes = maxTotalSizeBytes;
        c.consoleEnabled = consoleEnabled;
        c.fileRetryIntervalMs = fileRetryIntervalMs;
        return c;
    }

    public String getOutputDir() { return outputDir; }
    public VKLogSinkConfig outputDir(String outputDir) { this.outputDir = outputDir; return this; }
    public String getFilePrefix() { return filePrefix; }
    public VKLogSinkConfig filePrefix(String filePrefix) { this.filePrefix = filePrefix; return this; }
    public VKLogLevel getLevel() { return level; }
    public VKLogSinkConfig level(VKLogLevel level) { this.level = level; return this; }
    public Integer getQueueCapacity() { return queueCapacity; }
    public VKLogSinkConfig queueCapacity(Integer queueCapacity) { this.queueCapacity = queueCapacity; return this; }
    public VKLogQueueFullPolicy getQueueFullPolicy() { return queueFullPolicy; }
    public VKLogSinkConfig queueFullPolicy(VKLogQueueFullPolicy queueFullPolicy) { this.queueFullPolicy = queueFullPolicy; return this; }
    public Long getFlushIntervalMs() { return flushIntervalMs; }
    public VKLogSinkConfig flushIntervalMs(Long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; return this; }
    public Integer getFlushBatchSize() { return flushBatchSize; }
    public VKLogSinkConfig flushBatchSize(Integer flushBatchSize) { this.flushBatchSize = flushBatchSize; return this; }
    public Long getShutdownTimeoutMs() { return shutdownTimeoutMs; }
    public VKLogSinkConfig shutdownTimeoutMs(Long shutdownTimeoutMs) { this.shutdownTimeoutMs = shutdownTimeoutMs; return this; }
    public VKLogFsyncPolicy getFsyncPolicy() { return fsyncPolicy; }
    public VKLogSinkConfig fsyncPolicy(VKLogFsyncPolicy fsyncPolicy) { this.fsyncPolicy = fsyncPolicy; return this; }
    public VKLogRollInterval getRollInterval() { return rollInterval; }
    public VKLogSinkConfig rollInterval(VKLogRollInterval rollInterval) { this.rollInterval = rollInterval; return this; }
    public Boolean getCompressRolledFiles() { return compressRolledFiles; }
    public VKLogSinkConfig compressRolledFiles(Boolean compressRolledFiles) { this.compressRolledFiles = compressRolledFiles; return this; }
    public Integer getMaxBackups() { return maxBackups; }
    public VKLogSinkConfig maxBackups(Integer maxBackups) { this.maxBackups = maxBackups; return this; }
    public Integer getMaxBackupDays() { return maxBackupDays; }
    public VKLogSinkConfig maxBackupDays(Integer maxBackupDays) { this.maxBackupDays = maxBackupDays; return this; }
    public Long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public VKLogSinkConfig maxFileSizeBytes(Long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; return this; }
    public Long getMaxTotalSizeBytes() { return maxTotalSizeBytes; }
    public VKLogSinkConfig maxTotalSizeBytes(Long maxTotalSizeBytes) { this.maxTotalSizeBytes = maxTotalSizeBytes; return this; }
    public Boolean getConsoleEnabled() { return consoleEnabled; }
    public VKLogSinkConfig consoleEnabled(Boolean consoleEnabled) { this.consoleEnabled = consoleEnabled; return this; }
    public Long getFileRetryIntervalMs() { return fileRetryIntervalMs; }
    public VKLogSinkConfig fileRetryIntervalMs(Long fileRetryIntervalMs) { this.fileRetryIntervalMs = fileRetryIntervalMs; return this; }
}
