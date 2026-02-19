package yueyang.vostok.event.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.event.VKEventConfig;
import yueyang.vostok.event.VKEventListener;
import yueyang.vostok.event.VKEventListenerErrorStrategy;
import yueyang.vostok.event.VKEventPublishResult;
import yueyang.vostok.event.VKEventRejectionPolicy;
import yueyang.vostok.event.VKEventSubscription;
import yueyang.vostok.event.VKListenerMode;
import yueyang.vostok.util.VKAssert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class VKEventRuntime {
    private static final Object LOCK = new Object();
    private static final VKEventRuntime INSTANCE = new VKEventRuntime();

    private final AtomicLong listenerId = new AtomicLong(1);
    private final java.util.concurrent.ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<ListenerSlot>> listeners =
            new java.util.concurrent.ConcurrentHashMap<>();

    private volatile VKEventConfig config = new VKEventConfig();
    private volatile ThreadPoolExecutor asyncExecutor;
    private volatile boolean initialized;

    private VKEventRuntime() {
    }

    public static VKEventRuntime getInstance() {
        return INSTANCE;
    }

    public boolean started() {
        return initialized;
    }

    public VKEventConfig config() {
        return config.copy();
    }

    public void init(VKEventConfig cfg) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = normalized(cfg);
            asyncExecutor = buildExecutor(config);
            initialized = true;
        }
    }

    public void reinit(VKEventConfig cfg) {
        ThreadPoolExecutor old;
        synchronized (LOCK) {
            config = normalized(cfg);
            old = asyncExecutor;
            asyncExecutor = buildExecutor(config);
            initialized = true;
        }
        shutdownExecutor(old, config.getShutdownWaitMs());
    }

    public void close() {
        ThreadPoolExecutor old;
        synchronized (LOCK) {
            old = asyncExecutor;
            asyncExecutor = null;
            listeners.clear();
            initialized = false;
            listenerId.set(1);
        }
        shutdownExecutor(old, config.getShutdownWaitMs());
    }

    public <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener) {
        ensureInit();
        VKAssert.notNull(eventType, "Event type is null");
        VKAssert.notNull(mode, "Listener mode is null");
        VKAssert.notNull(listener, "Event listener is null");
        long id = listenerId.getAndIncrement();
        ListenerSlot slot = new ListenerSlot(id, eventType, mode, (VKEventListener<Object>) listener);
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(slot);
        return new VKEventSubscription(id, eventType, mode);
    }

    public void off(VKEventSubscription subscription) {
        if (subscription == null || subscription.getEventType() == null) {
            return;
        }
        CopyOnWriteArrayList<ListenerSlot> slots = listeners.get(subscription.getEventType());
        if (slots == null) {
            return;
        }
        slots.removeIf(it -> it.id == subscription.getId());
        if (slots.isEmpty()) {
            listeners.remove(subscription.getEventType(), slots);
        }
    }

    public void offAll(Class<?> eventType) {
        if (eventType == null) {
            return;
        }
        listeners.remove(eventType);
    }

    public VKEventPublishResult publish(Object event) {
        ensureInit();
        VKAssert.notNull(event, "Event is null");
        long start = System.currentTimeMillis();

        if (!config.isEnabled()) {
            return new VKEventPublishResult(0, 0, 0, 0, 0, System.currentTimeMillis() - start);
        }

        List<ListenerSlot> matched = resolveMatched(event.getClass());
        if (matched.isEmpty()) {
            return new VKEventPublishResult(0, 0, 0, 0, 0, System.currentTimeMillis() - start);
        }

        int syncExecuted = 0;
        int syncFailed = 0;
        int asyncSubmitted = 0;
        int asyncRejected = 0;
        for (ListenerSlot slot : matched) {
            if (slot.mode == VKListenerMode.SYNC) {
                try {
                    slot.listener.onEvent(event);
                    syncExecuted++;
                } catch (Throwable t) {
                    syncFailed++;
                    logListenerError("sync", event, slot, t);
                    if (config.getListenerErrorStrategy() == VKEventListenerErrorStrategy.FAIL_FAST) {
                        throw toRuntime(t);
                    }
                }
                continue;
            }

            try {
                ThreadPoolExecutor executor = asyncExecutor;
                if (executor == null) {
                    throw new RejectedExecutionException("Async executor is not ready");
                }
                executor.execute(() -> {
                    try {
                        slot.listener.onEvent(event);
                    } catch (Throwable t) {
                        logListenerError("async", event, slot, t);
                    }
                });
                asyncSubmitted++;
            } catch (RejectedExecutionException ex) {
                asyncRejected++;
                logReject(event, slot, ex);
            }
        }

        return new VKEventPublishResult(
                matched.size(), syncExecuted, syncFailed, asyncSubmitted, asyncRejected,
                System.currentTimeMillis() - start
        );
    }

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (!initialized) {
                config = normalized(config);
                asyncExecutor = buildExecutor(config);
                initialized = true;
            }
        }
    }

    private List<ListenerSlot> resolveMatched(Class<?> eventClass) {
        List<ListenerSlot> out = new ArrayList<>();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<ListenerSlot>> e : listeners.entrySet()) {
            if (e.getKey().isAssignableFrom(eventClass)) {
                out.addAll(e.getValue());
            }
        }
        out.sort(java.util.Comparator.comparingLong(o -> o.id));
        return out;
    }

    private VKEventConfig normalized(VKEventConfig cfg) {
        return (cfg == null ? new VKEventConfig() : cfg.copy());
    }

    private ThreadPoolExecutor buildExecutor(VKEventConfig cfg) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                Math.max(1, cfg.getAsyncCoreThreads()),
                Math.max(cfg.getAsyncCoreThreads(), cfg.getAsyncMaxThreads()),
                Math.max(0, cfg.getAsyncKeepAliveMs()),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, cfg.getAsyncQueueCapacity())),
                new EventThreadFactory(),
                rejectionHandler(cfg.getRejectionPolicy())
        );
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    private java.util.concurrent.RejectedExecutionHandler rejectionHandler(VKEventRejectionPolicy policy) {
        VKEventRejectionPolicy p = policy == null ? VKEventRejectionPolicy.CALLER_RUNS : policy;
        if (p == VKEventRejectionPolicy.ABORT) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        if (p == VKEventRejectionPolicy.DISCARD) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }

    private void shutdownExecutor(ThreadPoolExecutor executor, long waitMs) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(Math.max(0, waitMs), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void logListenerError(String mode, Object event, ListenerSlot slot, Throwable t) {
        try {
            Vostok.Log.error("Vostok.Event {} listener failed, eventType={}, listenerType={}, err={}",
                    mode, event.getClass().getName(), slot.eventType.getName(), t.getMessage());
            Vostok.Log.error("Vostok.Event listener stack", t);
        } catch (Throwable ignore) {
            // avoid impacting business flow
        }
    }

    private void logReject(Object event, ListenerSlot slot, Throwable t) {
        try {
            Vostok.Log.warn("Vostok.Event async listener rejected, eventType={}, listenerType={}, err={}",
                    event.getClass().getName(), slot.eventType.getName(), t.getMessage());
        } catch (Throwable ignore) {
            // avoid impacting business flow
        }
    }

    private RuntimeException toRuntime(Throwable t) {
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new IllegalStateException(t.getMessage(), t);
    }

    private static final class EventThreadFactory implements ThreadFactory {
        private final AtomicLong n = new AtomicLong(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "vostok-event-async-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    private static final class ListenerSlot {
        private final long id;
        private final Class<?> eventType;
        private final VKListenerMode mode;
        private final VKEventListener<Object> listener;

        private ListenerSlot(long id, Class<?> eventType, VKListenerMode mode, VKEventListener<Object> listener) {
            this.id = id;
            this.eventType = eventType;
            this.mode = mode;
            this.listener = listener;
        }
    }
}
