package yueyang.vostok.cluster.core;

import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.VKClusterMessage;
import yueyang.vostok.cluster.VKClusterMessageListener;
import yueyang.vostok.cluster.VKClusterSubscription;
import yueyang.vostok.cluster.exception.VKClusterErrorCode;
import yueyang.vostok.cluster.exception.VKClusterException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * topic -> listener 分发中心。
 * 监听器回调全部在独立线程池执行，避免阻塞网络读写线程。
 */
final class VKClusterDispatchHub {
    private final AtomicLong listenerId = new AtomicLong(1);
    private final Map<String, CopyOnWriteArrayList<ListenerSlot>> listeners = new ConcurrentHashMap<>();
    private volatile ThreadPoolExecutor executor;
    private volatile VKClusterStatsCollector stats;

    void init(VKClusterConfig config, VKClusterStatsCollector statsCollector) {
        this.stats = statsCollector;
        close();
        executor = new ThreadPoolExecutor(
                config.getWorkerThreads(),
                config.getWorkerThreads(),
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getListenerQueueCapacity()),
                new NamedThreadFactory("vostok-cluster-dispatch"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    VKClusterSubscription on(String topic, VKClusterMessageListener listener) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new VKClusterException(VKClusterErrorCode.INVALID_ARGUMENT, "Cluster topic is blank");
        }
        if (listener == null) {
            throw new VKClusterException(VKClusterErrorCode.INVALID_ARGUMENT, "Cluster listener is null");
        }
        String normalized = topic.trim();
        long id = listenerId.getAndIncrement();
        listeners.computeIfAbsent(normalized, k -> new CopyOnWriteArrayList<>())
                .add(new ListenerSlot(id, listener));
        return new VKClusterSubscription(id, normalized, () -> off(id, normalized));
    }

    void off(VKClusterSubscription subscription) {
        if (subscription == null) {
            return;
        }
        off(subscription.getId(), subscription.getTopic());
    }

    void offAll(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return;
        }
        listeners.remove(topic.trim());
    }

    boolean deliver(VKClusterMessage message) {
        CopyOnWriteArrayList<ListenerSlot> slots = listeners.get(message.getTopic());
        if (slots == null || slots.isEmpty()) {
            return true;
        }
        ThreadPoolExecutor pool = executor;
        if (pool == null) {
            return false;
        }
        try {
            pool.execute(() -> dispatch(slots, message));
            return true;
        } catch (RejectedExecutionException ex) {
            if (stats != null) {
                stats.onQueueDrop();
            }
            return false;
        }
    }

    void close() {
        ThreadPoolExecutor pool = executor;
        executor = null;
        if (pool != null) {
            pool.shutdownNow();
        }
        listeners.clear();
    }

    private void off(long id, String topic) {
        listeners.computeIfPresent(topic, (k, slots) -> {
            slots.removeIf(slot -> slot.id == id);
            return slots.isEmpty() ? null : slots;
        });
    }

    private void dispatch(List<ListenerSlot> slots, VKClusterMessage message) {
        for (ListenerSlot slot : slots) {
            try {
                slot.listener.onMessage(message);
            } catch (Throwable ignore) {
                // 监听器异常不反向影响网络层 ACK 语义
            }
        }
    }

    private static final class ListenerSlot {
        private final long id;
        private final VKClusterMessageListener listener;

        private ListenerSlot(long id, VKClusterMessageListener listener) {
            this.id = id;
            this.listener = listener;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicLong seq = new AtomicLong(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
