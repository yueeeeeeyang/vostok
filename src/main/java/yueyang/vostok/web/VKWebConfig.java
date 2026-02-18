package yueyang.vostok.web;

/**
 * Web server configuration.
 */
public final class VKWebConfig {
    private int port = 8080;
    private int ioThreads = 1;
    private int workerThreads = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
    private int backlog = 1024;
    private int readBufferSize = 16 * 1024;
    private int maxHeaderBytes = 32 * 1024;
    private int maxBodyBytes = 4 * 1024 * 1024;
    private int keepAliveTimeoutMs = 30_000;
    private int maxConnections = 10_000;
    private int readTimeoutMs = 15_000;
    private int workerQueueSize = 10_000;
    private boolean accessLogEnabled = true;
    private int accessLogQueueSize = 8_192;
    private boolean rateLimitLogEnabled = true;
    private boolean multipartEnabled = true;
    private String multipartTempDir = System.getProperty("java.io.tmpdir") + "/vostok-upload";
    private int multipartInMemoryThresholdBytes = 64 * 1024;
    private int multipartMaxParts = 128;
    private long multipartMaxFileSizeBytes = 16L * 1024 * 1024;
    private long multipartMaxTotalBytes = 32L * 1024 * 1024;
    private int rateLimitCleanupIntervalMs = 60_000;
    private boolean websocketEnabled = true;
    private int websocketMaxFramePayloadBytes = 1024 * 1024;
    private int websocketMaxMessageBytes = 4 * 1024 * 1024;
    private int websocketMaxPendingFrames = 1024;
    private int websocketMaxPendingBytes = 8 * 1024 * 1024;
    private int websocketPingIntervalMs = 30_000;
    private int websocketPongTimeoutMs = 10_000;
    private int websocketIdleTimeoutMs = 120_000;

    public int getPort() {
        return port;
    }

    public VKWebConfig port(int port) {
        this.port = port;
        return this;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public VKWebConfig ioThreads(int ioThreads) {
        this.ioThreads = Math.max(1, ioThreads);
        return this;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public VKWebConfig workerThreads(int workerThreads) {
        this.workerThreads = Math.max(1, workerThreads);
        return this;
    }

    public int getBacklog() {
        return backlog;
    }

    public VKWebConfig backlog(int backlog) {
        this.backlog = Math.max(1, backlog);
        return this;
    }

    public int getReadBufferSize() {
        return readBufferSize;
    }

    public VKWebConfig readBufferSize(int readBufferSize) {
        this.readBufferSize = Math.max(1024, readBufferSize);
        return this;
    }

    public int getMaxHeaderBytes() {
        return maxHeaderBytes;
    }

    public VKWebConfig maxHeaderBytes(int maxHeaderBytes) {
        this.maxHeaderBytes = Math.max(4096, maxHeaderBytes);
        return this;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public VKWebConfig maxBodyBytes(int maxBodyBytes) {
        this.maxBodyBytes = Math.max(1024, maxBodyBytes);
        return this;
    }

    public int getKeepAliveTimeoutMs() {
        return keepAliveTimeoutMs;
    }

    public VKWebConfig keepAliveTimeoutMs(int keepAliveTimeoutMs) {
        this.keepAliveTimeoutMs = Math.max(1000, keepAliveTimeoutMs);
        return this;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public VKWebConfig maxConnections(int maxConnections) {
        this.maxConnections = Math.max(1, maxConnections);
        return this;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKWebConfig readTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = Math.max(1000, readTimeoutMs);
        return this;
    }

    public int getWorkerQueueSize() {
        return workerQueueSize;
    }

    public VKWebConfig workerQueueSize(int workerQueueSize) {
        this.workerQueueSize = Math.max(1, workerQueueSize);
        return this;
    }

    public boolean isAccessLogEnabled() {
        return accessLogEnabled;
    }

    public VKWebConfig accessLogEnabled(boolean accessLogEnabled) {
        this.accessLogEnabled = accessLogEnabled;
        return this;
    }

    public int getAccessLogQueueSize() {
        return accessLogQueueSize;
    }

    public VKWebConfig accessLogQueueSize(int accessLogQueueSize) {
        this.accessLogQueueSize = Math.max(256, accessLogQueueSize);
        return this;
    }

    public boolean isRateLimitLogEnabled() {
        return rateLimitLogEnabled;
    }

    public VKWebConfig rateLimitLogEnabled(boolean rateLimitLogEnabled) {
        this.rateLimitLogEnabled = rateLimitLogEnabled;
        return this;
    }

    public boolean isMultipartEnabled() {
        return multipartEnabled;
    }

    public VKWebConfig multipartEnabled(boolean multipartEnabled) {
        this.multipartEnabled = multipartEnabled;
        return this;
    }

    public String getMultipartTempDir() {
        return multipartTempDir;
    }

    public VKWebConfig multipartTempDir(String multipartTempDir) {
        if (multipartTempDir != null && !multipartTempDir.isEmpty()) {
            this.multipartTempDir = multipartTempDir;
        }
        return this;
    }

    public int getMultipartInMemoryThresholdBytes() {
        return multipartInMemoryThresholdBytes;
    }

    public VKWebConfig multipartInMemoryThresholdBytes(int multipartInMemoryThresholdBytes) {
        this.multipartInMemoryThresholdBytes = Math.max(1024, multipartInMemoryThresholdBytes);
        return this;
    }

    public int getMultipartMaxParts() {
        return multipartMaxParts;
    }

    public VKWebConfig multipartMaxParts(int multipartMaxParts) {
        this.multipartMaxParts = Math.max(1, multipartMaxParts);
        return this;
    }

    public long getMultipartMaxFileSizeBytes() {
        return multipartMaxFileSizeBytes;
    }

    public VKWebConfig multipartMaxFileSizeBytes(long multipartMaxFileSizeBytes) {
        this.multipartMaxFileSizeBytes = Math.max(1024L, multipartMaxFileSizeBytes);
        return this;
    }

    public long getMultipartMaxTotalBytes() {
        return multipartMaxTotalBytes;
    }

    public VKWebConfig multipartMaxTotalBytes(long multipartMaxTotalBytes) {
        this.multipartMaxTotalBytes = Math.max(1024L, multipartMaxTotalBytes);
        return this;
    }

    public int getRateLimitCleanupIntervalMs() {
        return rateLimitCleanupIntervalMs;
    }

    public VKWebConfig rateLimitCleanupIntervalMs(int rateLimitCleanupIntervalMs) {
        this.rateLimitCleanupIntervalMs = Math.max(1000, rateLimitCleanupIntervalMs);
        return this;
    }

    public boolean isWebsocketEnabled() {
        return websocketEnabled;
    }

    public VKWebConfig websocketEnabled(boolean websocketEnabled) {
        this.websocketEnabled = websocketEnabled;
        return this;
    }

    public int getWebsocketMaxFramePayloadBytes() {
        return websocketMaxFramePayloadBytes;
    }

    public VKWebConfig websocketMaxFramePayloadBytes(int websocketMaxFramePayloadBytes) {
        this.websocketMaxFramePayloadBytes = Math.max(1024, websocketMaxFramePayloadBytes);
        return this;
    }

    public int getWebsocketMaxMessageBytes() {
        return websocketMaxMessageBytes;
    }

    public VKWebConfig websocketMaxMessageBytes(int websocketMaxMessageBytes) {
        this.websocketMaxMessageBytes = Math.max(1024, websocketMaxMessageBytes);
        return this;
    }

    public int getWebsocketMaxPendingFrames() {
        return websocketMaxPendingFrames;
    }

    public VKWebConfig websocketMaxPendingFrames(int websocketMaxPendingFrames) {
        this.websocketMaxPendingFrames = Math.max(16, websocketMaxPendingFrames);
        return this;
    }

    public int getWebsocketMaxPendingBytes() {
        return websocketMaxPendingBytes;
    }

    public VKWebConfig websocketMaxPendingBytes(int websocketMaxPendingBytes) {
        this.websocketMaxPendingBytes = Math.max(1024, websocketMaxPendingBytes);
        return this;
    }

    public int getWebsocketPingIntervalMs() {
        return websocketPingIntervalMs;
    }

    public VKWebConfig websocketPingIntervalMs(int websocketPingIntervalMs) {
        this.websocketPingIntervalMs = Math.max(1000, websocketPingIntervalMs);
        return this;
    }

    public int getWebsocketPongTimeoutMs() {
        return websocketPongTimeoutMs;
    }

    public VKWebConfig websocketPongTimeoutMs(int websocketPongTimeoutMs) {
        this.websocketPongTimeoutMs = Math.max(1000, websocketPongTimeoutMs);
        return this;
    }

    public int getWebsocketIdleTimeoutMs() {
        return websocketIdleTimeoutMs;
    }

    public VKWebConfig websocketIdleTimeoutMs(int websocketIdleTimeoutMs) {
        this.websocketIdleTimeoutMs = Math.max(1000, websocketIdleTimeoutMs);
        return this;
    }
}
