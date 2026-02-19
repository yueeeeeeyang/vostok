package yueyang.vostok.event;

public final class VKEventPublishResult {
    private final int matchedListeners;
    private final int syncExecuted;
    private final int syncFailed;
    private final int asyncSubmitted;
    private final int asyncRejected;
    private final long costMs;

    public VKEventPublishResult(int matchedListeners, int syncExecuted, int syncFailed,
                                int asyncSubmitted, int asyncRejected, long costMs) {
        this.matchedListeners = matchedListeners;
        this.syncExecuted = syncExecuted;
        this.syncFailed = syncFailed;
        this.asyncSubmitted = asyncSubmitted;
        this.asyncRejected = asyncRejected;
        this.costMs = costMs;
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

    public long getCostMs() {
        return costMs;
    }
}
