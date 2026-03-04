package yueyang.vostok.office.job;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 任务通知异步分发器。
 *
 * <p>通过 per-job future 链保证同一 job 的状态通知按提交顺序执行。</p>
 */
public final class VKOfficeJobDispatcher {
    private final VKOfficeJobCallbackHub callbackHub;
    private final ExecutorService callbackExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();

    public VKOfficeJobDispatcher(VKOfficeJobCallbackHub callbackHub, ExecutorService callbackExecutor) {
        this.callbackHub = callbackHub;
        this.callbackExecutor = callbackExecutor;
    }

    public void dispatch(VKOfficeJobNotification notification) {
        if (notification == null) {
            return;
        }
        String jobId = notification.jobId() == null ? "_anonymous_" : notification.jobId();
        chains.compute(jobId, (id, prev) -> {
            CompletableFuture<Void> base = prev == null ? CompletableFuture.completedFuture(null) : prev;
            return base.handle((v, ex) -> null)
                    .thenRunAsync(() -> callbackHub.dispatch(notification), callbackExecutor);
        });
    }

    public void clear() {
        chains.clear();
    }
}
