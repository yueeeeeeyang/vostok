package yueyang.vostok.cluster.core;

import yueyang.vostok.cluster.VKClusterBroadcastMode;
import yueyang.vostok.cluster.VKClusterBroadcastResult;
import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.VKClusterMessage;
import yueyang.vostok.cluster.VKClusterNode;
import yueyang.vostok.cluster.exception.VKClusterErrorCode;
import yueyang.vostok.cluster.exception.VKClusterException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 广播引擎。
 * 可靠广播采用 ACK + 重试；尽力广播只做单次发送。
 */
final class VKClusterBroadcastEngine {
    private final ConcurrentMap<String, InflightMessage> inflight = new ConcurrentHashMap<>();
    private final DedupeWindow dedupeWindow = new DedupeWindow();
    private volatile VKClusterRuntime runtime;
    private volatile VKClusterConfig config;
    private volatile VKClusterMembershipManager membership;
    private volatile VKClusterConnectionManager connections;
    private volatile VKClusterDispatchHub dispatchHub;
    private volatile VKClusterStatsCollector stats;
    private volatile ScheduledExecutorService scheduler;

    void init(VKClusterRuntime runtime,
              VKClusterConfig config,
              VKClusterMembershipManager membership,
              VKClusterConnectionManager connections,
              VKClusterDispatchHub dispatchHub,
              VKClusterStatsCollector stats,
              ScheduledExecutorService scheduler) {
        this.runtime = runtime;
        this.config = config;
        this.membership = membership;
        this.connections = connections;
        this.dispatchHub = dispatchHub;
        this.stats = stats;
        this.scheduler = scheduler;
        dedupeWindow.configure(config.getDedupeRetentionMs());
        inflight.clear();
    }

    CompletableFuture<VKClusterBroadcastResult> broadcast(String topic, byte[] payload) {
        return broadcastReliable(topic, payload);
    }

    CompletableFuture<VKClusterBroadcastResult> broadcastReliable(String topic, byte[] payload) {
        validateMessage(topic, payload);
        long start = System.currentTimeMillis();
        byte[] copy = payload == null ? new byte[0] : payload.clone();
        String messageId = runtime.nextMessageId();
        boolean localDelivered = deliverLocal(messageId, topic, copy, true, start);
        stats.onMessageSent();

        Set<String> targets = membership.aliveRemoteNodes().stream()
                .map(VKClusterNode::getNodeId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (targets.isEmpty()) {
            return CompletableFuture.completedFuture(new VKClusterBroadcastResult(
                    messageId, VKClusterBroadcastMode.RELIABLE, 0, 0, 0,
                    localDelivered, System.currentTimeMillis() - start));
        }
        InflightMessage state = new InflightMessage(messageId, topic, copy, start, targets, localDelivered);
        inflight.put(messageId, state);
        sendAttempt(state, false);
        return state.future;
    }

    CompletableFuture<VKClusterBroadcastResult> broadcastBestEffort(String topic, byte[] payload) {
        validateMessage(topic, payload);
        long start = System.currentTimeMillis();
        byte[] copy = payload == null ? new byte[0] : payload.clone();
        String messageId = runtime.nextMessageId();
        boolean localDelivered = deliverLocal(messageId, topic, copy, false, start);
        stats.onMessageSent();
        int failed = 0;
        int targeted = 0;
        for (VKClusterNode node : membership.aliveRemoteNodes()) {
            targeted++;
            if (!connections.sendBroadcast(node.getNodeId(), messageId, topic,
                    membership.self().getNodeId(), false, start, copy)) {
                failed++;
            }
        }
        return CompletableFuture.completedFuture(new VKClusterBroadcastResult(
                messageId, VKClusterBroadcastMode.BEST_EFFORT,
                targeted, 0, failed, localDelivered,
                System.currentTimeMillis() - start));
    }

    void onAck(String fromNodeId, String messageId) {
        InflightMessage state = inflight.get(messageId);
        if (state == null || state.done.get()) {
            return;
        }
        state.acked.put(fromNodeId, Boolean.TRUE);
        stats.onAckReceived();
        tryComplete(state);
    }

    IncomingState acceptIncoming(String messageId, long now) {
        return dedupeWindow.mark(messageId, now);
    }

    void markIncomingDelivered(String messageId, long now) {
        dedupeWindow.markDelivered(messageId, now);
    }

    void cleanupDedupe(long now) {
        dedupeWindow.cleanup(now);
    }

    void close() {
        for (InflightMessage value : inflight.values()) {
            if (value.done.compareAndSet(false, true)) {
                value.future.complete(new VKClusterBroadcastResult(
                        value.messageId,
                        VKClusterBroadcastMode.RELIABLE,
                        value.targets.size(),
                        value.acked.size(),
                        Math.max(0, value.targets.size() - value.acked.size()),
                        value.localDelivered,
                        System.currentTimeMillis() - value.startMs));
            }
        }
        inflight.clear();
        dedupeWindow.clear();
    }

    private void validateMessage(String topic, byte[] payload) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new VKClusterException(VKClusterErrorCode.INVALID_ARGUMENT, "Cluster topic is blank");
        }
        if (payload != null && payload.length > config.getMaxMessageBytes()) {
            throw new VKClusterException(VKClusterErrorCode.LIMIT_EXCEEDED,
                    "Cluster message bytes exceeded maxMessageBytes: " + payload.length);
        }
    }

    private boolean deliverLocal(String messageId, String topic, byte[] payload, boolean reliable, long now) {
        if (!config.isIncludeSelfOnBroadcast()) {
            return false;
        }
        VKClusterMessage message = new VKClusterMessage(messageId, topic, payload,
                membership.self().getNodeId(), reliable, now, now);
        stats.onMessageReceived();
        return dispatchHub.deliver(message);
    }

    private void sendAttempt(InflightMessage state, boolean retry) {
        if (state.done.get()) {
            return;
        }
        if (retry) {
            state.attempt.incrementAndGet();
            stats.onReliableRetry();
        }
        for (String nodeId : state.targets) {
            if (state.acked.containsKey(nodeId)) {
                continue;
            }
            connections.sendBroadcast(nodeId, state.messageId, state.topic,
                    membership.self().getNodeId(), true, state.startMs, state.payload);
        }
        long delay = Math.max(config.getReliableAckTimeoutMs(),
                config.getReliableRetryBaseMs() * (1L << Math.max(0, state.attempt.get())));
        scheduler.schedule(() -> onRetryTimer(state.messageId), delay, TimeUnit.MILLISECONDS);
    }

    private void onRetryTimer(String messageId) {
        InflightMessage state = inflight.get(messageId);
        if (state == null || state.done.get()) {
            return;
        }
        if (state.acked.size() >= state.targets.size()) {
            tryComplete(state);
            return;
        }
        if (state.attempt.get() >= config.getReliableMaxRetries()) {
            fail(state);
            return;
        }
        sendAttempt(state, true);
    }

    private void tryComplete(InflightMessage state) {
        if (state.acked.size() < state.targets.size()) {
            return;
        }
        if (!state.done.compareAndSet(false, true)) {
            return;
        }
        inflight.remove(state.messageId);
        state.future.complete(new VKClusterBroadcastResult(
                state.messageId,
                VKClusterBroadcastMode.RELIABLE,
                state.targets.size(),
                state.acked.size(),
                0,
                state.localDelivered,
                System.currentTimeMillis() - state.startMs));
    }

    private void fail(InflightMessage state) {
        if (!state.done.compareAndSet(false, true)) {
            return;
        }
        inflight.remove(state.messageId);
        int failed = Math.max(0, state.targets.size() - state.acked.size());
        state.future.complete(new VKClusterBroadcastResult(
                state.messageId,
                VKClusterBroadcastMode.RELIABLE,
                state.targets.size(),
                state.acked.size(),
                failed,
                state.localDelivered,
                System.currentTimeMillis() - state.startMs));
    }

    private static final class InflightMessage {
        private final String messageId;
        private final String topic;
        private final byte[] payload;
        private final long startMs;
        private final Set<String> targets;
        private final ConcurrentMap<String, Boolean> acked = new ConcurrentHashMap<>();
        private final AtomicInteger attempt = new AtomicInteger(0);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final CompletableFuture<VKClusterBroadcastResult> future = new CompletableFuture<>();
        private final boolean localDelivered;

        private InflightMessage(String messageId, String topic, byte[] payload,
                                long startMs, Set<String> targets, boolean localDelivered) {
            this.messageId = messageId;
            this.topic = topic;
            this.payload = payload;
            this.startMs = startMs;
            this.targets = targets;
            this.localDelivered = localDelivered;
        }
    }

    /**
     * 去重窗口只保留消息 ID 最近一次看到的时间戳。
     * 首版使用时间窗哈希表，足够支撑 <=100 节点的小集群广播去重。
     */
    enum IncomingState {
        NEW,
        PENDING_DUPLICATE,
        COMMITTED_DUPLICATE
    }

    private static final class DedupeWindow {
        private final ConcurrentMap<String, Entry> seen = new ConcurrentHashMap<>();
        private volatile long retentionMs = 60_000L;
        private volatile long lastCleanupMs;

        private void configure(long retentionMs) {
            this.retentionMs = Math.max(1000L, retentionMs);
            this.lastCleanupMs = 0L;
            seen.clear();
        }

        private IncomingState mark(String messageId, long now) {
            cleanup(now);
            Entry fresh = new Entry(now, false);
            Entry existing = seen.putIfAbsent(messageId, fresh);
            if (existing == null) {
                return IncomingState.NEW;
            }
            existing.timestamp = now;
            return existing.delivered ? IncomingState.COMMITTED_DUPLICATE : IncomingState.PENDING_DUPLICATE;
        }

        private void markDelivered(String messageId, long now) {
            seen.computeIfPresent(messageId, (k, entry) -> {
                entry.timestamp = now;
                entry.delivered = true;
                return entry;
            });
        }

        private void cleanup(long now) {
            if (now - lastCleanupMs < Math.max(1000L, retentionMs / 2)) {
                return;
            }
            lastCleanupMs = now;
            for (Map.Entry<String, Entry> entry : seen.entrySet()) {
                if (now - entry.getValue().timestamp >= retentionMs) {
                    seen.remove(entry.getKey(), entry.getValue());
                }
            }
        }

        private void clear() {
            seen.clear();
        }

        private static final class Entry {
            private volatile long timestamp;
            private volatile boolean delivered;

            private Entry(long timestamp, boolean delivered) {
                this.timestamp = timestamp;
                this.delivered = delivered;
            }
        }
    }
}
