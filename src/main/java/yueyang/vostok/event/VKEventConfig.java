package yueyang.vostok.event;

public class VKEventConfig {
    private boolean enabled = true;
    private int asyncCoreThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private int asyncMaxThreads = Math.max(asyncCoreThreads, Runtime.getRuntime().availableProcessors() * 2);
    private int asyncQueueCapacity = 4096;
    private long asyncKeepAliveMs = 60_000;
    private VKEventRejectionPolicy rejectionPolicy = VKEventRejectionPolicy.CALLER_RUNS;
    private VKEventListenerErrorStrategy listenerErrorStrategy = VKEventListenerErrorStrategy.CONTINUE;
    private long shutdownWaitMs = 3000;

    public VKEventConfig copy() {
        return new VKEventConfig()
                .enabled(enabled)
                .asyncCoreThreads(asyncCoreThreads)
                .asyncMaxThreads(asyncMaxThreads)
                .asyncQueueCapacity(asyncQueueCapacity)
                .asyncKeepAliveMs(asyncKeepAliveMs)
                .rejectionPolicy(rejectionPolicy)
                .listenerErrorStrategy(listenerErrorStrategy)
                .shutdownWaitMs(shutdownWaitMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public VKEventConfig enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public int getAsyncCoreThreads() {
        return asyncCoreThreads;
    }

    public VKEventConfig asyncCoreThreads(int asyncCoreThreads) {
        this.asyncCoreThreads = Math.max(1, asyncCoreThreads);
        if (this.asyncMaxThreads < this.asyncCoreThreads) {
            this.asyncMaxThreads = this.asyncCoreThreads;
        }
        return this;
    }

    public int getAsyncMaxThreads() {
        return asyncMaxThreads;
    }

    public VKEventConfig asyncMaxThreads(int asyncMaxThreads) {
        this.asyncMaxThreads = Math.max(1, asyncMaxThreads);
        if (this.asyncMaxThreads < this.asyncCoreThreads) {
            this.asyncCoreThreads = this.asyncMaxThreads;
        }
        return this;
    }

    public int getAsyncQueueCapacity() {
        return asyncQueueCapacity;
    }

    public VKEventConfig asyncQueueCapacity(int asyncQueueCapacity) {
        this.asyncQueueCapacity = Math.max(1, asyncQueueCapacity);
        return this;
    }

    public long getAsyncKeepAliveMs() {
        return asyncKeepAliveMs;
    }

    public VKEventConfig asyncKeepAliveMs(long asyncKeepAliveMs) {
        this.asyncKeepAliveMs = Math.max(0, asyncKeepAliveMs);
        return this;
    }

    public VKEventRejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public VKEventConfig rejectionPolicy(VKEventRejectionPolicy rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy == null ? VKEventRejectionPolicy.CALLER_RUNS : rejectionPolicy;
        return this;
    }

    public VKEventListenerErrorStrategy getListenerErrorStrategy() {
        return listenerErrorStrategy;
    }

    public VKEventConfig listenerErrorStrategy(VKEventListenerErrorStrategy listenerErrorStrategy) {
        this.listenerErrorStrategy = listenerErrorStrategy == null
                ? VKEventListenerErrorStrategy.CONTINUE
                : listenerErrorStrategy;
        return this;
    }

    public long getShutdownWaitMs() {
        return shutdownWaitMs;
    }

    public VKEventConfig shutdownWaitMs(long shutdownWaitMs) {
        this.shutdownWaitMs = Math.max(0, shutdownWaitMs);
        return this;
    }
}
