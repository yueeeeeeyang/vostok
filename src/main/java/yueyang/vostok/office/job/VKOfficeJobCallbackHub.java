package yueyang.vostok.office.job;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务回调注册中心。
 *
 * <p>使用 COW 容器，读多写少场景下可降低分发路径锁竞争。</p>
 */
public final class VKOfficeJobCallbackHub {
    private final AtomicLong idGen = new AtomicLong(1);
    private final CopyOnWriteArrayList<Slot> slots = new CopyOnWriteArrayList<>();
    private final AtomicReference<VKOfficeJobDeadLetterHandler> deadLetterRef = new AtomicReference<>();

    public VKOfficeJobSubscription on(VKOfficeJobStatus status,
                                      VKOfficeJobFilter filter,
                                      VKOfficeJobListener listener,
                                      boolean once) {
        if (listener == null) {
            return null;
        }
        long id = idGen.getAndIncrement();
        Slot slot = new Slot(id, status, filter, listener, once);
        slots.add(slot);
        return new VKOfficeJobSubscription(id, status, once, () -> slots.remove(slot));
    }

    public void off(VKOfficeJobSubscription subscription) {
        if (subscription == null) {
            return;
        }
        subscription.cancel();
    }

    public void offAll() {
        slots.clear();
    }

    public void onDeadLetter(VKOfficeJobDeadLetterHandler handler) {
        deadLetterRef.set(handler);
    }

    /**
     * 同步分发一次通知。
     *
     * @return 命中的监听器数量
     */
    public int dispatch(VKOfficeJobNotification notification) {
        if (notification == null) {
            return 0;
        }
        List<Slot> snapshot = slots;
        int matched = 0;
        for (Slot slot : snapshot) {
            if (!slot.match(notification)) {
                continue;
            }
            if (slot.once && !slot.consumed.compareAndSet(false, true)) {
                continue;
            }
            matched++;
            try {
                slot.listener.onJob(notification);
            } catch (Throwable ignore) {
                // 单监听器异常隔离，不影响其他监听器。
            } finally {
                if (slot.once) {
                    slots.remove(slot);
                }
            }
        }

        if (matched == 0) {
            VKOfficeJobDeadLetterHandler handler = deadLetterRef.get();
            if (handler != null) {
                try {
                    handler.onDeadLetter(notification);
                } catch (Throwable ignore) {
                }
            }
        }
        return matched;
    }

    private static final class Slot {
        private final long id;
        private final VKOfficeJobStatus status;
        private final VKOfficeJobFilter filter;
        private final VKOfficeJobListener listener;
        private final boolean once;
        private final AtomicBoolean consumed = new AtomicBoolean(false);

        private Slot(long id,
                     VKOfficeJobStatus status,
                     VKOfficeJobFilter filter,
                     VKOfficeJobListener listener,
                     boolean once) {
            this.id = id;
            this.status = status;
            this.filter = filter;
            this.listener = listener;
            this.once = once;
        }

        private boolean match(VKOfficeJobNotification n) {
            if (n == null) {
                return false;
            }
            if (status != null && n.status() != status) {
                return false;
            }
            if (filter != null && !filter.test(n)) {
                return false;
            }
            return true;
        }
    }
}
