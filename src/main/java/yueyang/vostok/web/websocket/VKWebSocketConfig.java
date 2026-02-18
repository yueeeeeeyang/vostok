package yueyang.vostok.web.websocket;

public final class VKWebSocketConfig {
    private int maxFramePayloadBytes = 1024 * 1024;
    private int maxMessageBytes = 4 * 1024 * 1024;
    private int maxPendingFrames = 1024;
    private int maxPendingBytes = 8 * 1024 * 1024;
    private int pingIntervalMs = 30_000;
    private int pongTimeoutMs = 10_000;
    private int idleTimeoutMs = 120_000;

    public int getMaxFramePayloadBytes() {
        return maxFramePayloadBytes;
    }

    public VKWebSocketConfig maxFramePayloadBytes(int maxFramePayloadBytes) {
        this.maxFramePayloadBytes = Math.max(1024, maxFramePayloadBytes);
        return this;
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    public VKWebSocketConfig maxMessageBytes(int maxMessageBytes) {
        this.maxMessageBytes = Math.max(1024, maxMessageBytes);
        return this;
    }

    public int getMaxPendingFrames() {
        return maxPendingFrames;
    }

    public VKWebSocketConfig maxPendingFrames(int maxPendingFrames) {
        this.maxPendingFrames = Math.max(16, maxPendingFrames);
        return this;
    }

    public int getMaxPendingBytes() {
        return maxPendingBytes;
    }

    public VKWebSocketConfig maxPendingBytes(int maxPendingBytes) {
        this.maxPendingBytes = Math.max(1024, maxPendingBytes);
        return this;
    }

    public int getPingIntervalMs() {
        return pingIntervalMs;
    }

    public VKWebSocketConfig pingIntervalMs(int pingIntervalMs) {
        this.pingIntervalMs = Math.max(1000, pingIntervalMs);
        return this;
    }

    public int getPongTimeoutMs() {
        return pongTimeoutMs;
    }

    public VKWebSocketConfig pongTimeoutMs(int pongTimeoutMs) {
        this.pongTimeoutMs = Math.max(1000, pongTimeoutMs);
        return this;
    }

    public int getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public VKWebSocketConfig idleTimeoutMs(int idleTimeoutMs) {
        this.idleTimeoutMs = Math.max(1000, idleTimeoutMs);
        return this;
    }
}
