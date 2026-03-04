package yueyang.vostok.office.job;

import java.util.concurrent.atomic.AtomicBoolean;

/** 任务通知订阅句柄。 */
public final class VKOfficeJobSubscription {
    private final long id;
    private final VKOfficeJobStatus status;
    private final boolean once;
    private final Runnable cancelHook;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public VKOfficeJobSubscription(long id, VKOfficeJobStatus status, boolean once, Runnable cancelHook) {
        this.id = id;
        this.status = status;
        this.once = once;
        this.cancelHook = cancelHook;
    }

    /** 主动取消订阅。 */
    public void cancel() {
        if (cancelled.compareAndSet(false, true) && cancelHook != null) {
            cancelHook.run();
        }
    }

    public long id() {
        return id;
    }

    public VKOfficeJobStatus status() {
        return status;
    }

    public boolean once() {
        return once;
    }

    public VKOfficeJobSubscriptionStatus subscriptionStatus() {
        return cancelled.get() ? VKOfficeJobSubscriptionStatus.CANCELLED : VKOfficeJobSubscriptionStatus.ACTIVE;
    }
}
