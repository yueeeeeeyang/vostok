package yueyang.vostok.event;

/**
 * 事件发布结果，记录本次 publish/publishAsync 操作的统计数据。
 *
 * <p>costNs 使用 System.nanoTime() 精度，适合微秒级性能分析。
 * asyncFailed 仅在 publishAsync() 中有意义（等待所有异步 future 完成后统计）；
 * 同步 publish() 调用时 asyncFailed 始终为 0（不等待异步任务结果）。
 */
public final class VKEventPublishResult {
    private final int matchedListeners;
    private final int syncExecuted;
    private final int syncFailed;
    private final int asyncSubmitted;
    private final int asyncRejected;
    /** 异步监听器执行失败数（publishAsync 专用，publish() 固定为 0） */
    private final int asyncFailed;
    /** 发布耗时，单位纳秒（nanoTime 精度，非 currentTimeMillis） */
    private final long costNs;

    public VKEventPublishResult(int matchedListeners, int syncExecuted, int syncFailed,
                                int asyncSubmitted, int asyncRejected, int asyncFailed, long costNs) {
        this.matchedListeners = matchedListeners;
        this.syncExecuted = syncExecuted;
        this.syncFailed = syncFailed;
        this.asyncSubmitted = asyncSubmitted;
        this.asyncRejected = asyncRejected;
        this.asyncFailed = asyncFailed;
        this.costNs = costNs;
    }

    public int getMatchedListeners() {
        return matchedListeners;
    }

    public int getSyncExecuted() {
        return syncExecuted;
    }

    public int getSyncFailed() {
        return syncFailed;
    }

    public int getAsyncSubmitted() {
        return asyncSubmitted;
    }

    public int getAsyncRejected() {
        return asyncRejected;
    }

    public int getAsyncFailed() {
        return asyncFailed;
    }

    /**
     * 返回发布耗时（纳秒）。
     */
    public long getCostNs() {
        return costNs;
    }
}
