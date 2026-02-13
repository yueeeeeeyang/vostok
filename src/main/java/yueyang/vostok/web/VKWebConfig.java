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
}
