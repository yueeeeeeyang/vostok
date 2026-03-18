package yueyang.vostok.cluster.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.cluster.VKClusterBroadcastResult;
import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.VKClusterMessage;
import yueyang.vostok.cluster.VKClusterMessageListener;
import yueyang.vostok.cluster.VKClusterNode;
import yueyang.vostok.cluster.VKClusterSubscription;
import yueyang.vostok.cluster.VKClusterStats;
import yueyang.vostok.cluster.exception.VKClusterErrorCode;
import yueyang.vostok.cluster.exception.VKClusterException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cluster 运行时。
 * 为了便于测试，同进程内可以创建多个 runtime 实例；Vostok.Cluster 仅持有其中一个静态单例。
 */
public final class VKClusterRuntime {
    private final Object lock = new Object();
    private final VKClusterMembershipManager membership = new VKClusterMembershipManager();
    private final VKClusterStatsCollector statsCollector = new VKClusterStatsCollector();
    private final VKClusterDispatchHub dispatchHub = new VKClusterDispatchHub();
    private final VKClusterConnectionManager connectionManager = new VKClusterConnectionManager();
    private final VKClusterBroadcastEngine broadcastEngine = new VKClusterBroadcastEngine();
    private final AtomicLong messageSeq = new AtomicLong(1);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile VKClusterConfig config;
    private volatile boolean started;
    private volatile ScheduledExecutorService scheduler;
    private volatile CountDownLatch readyLatch = new CountDownLatch(1);

    public void init(VKClusterConfig clusterConfig) {
        synchronized (lock) {
            if (started) {
                return;
            }
            VKClusterConfig cfg = clusterConfig == null ? new VKClusterConfig() : clusterConfig.copy();
            if (cfg.getAdvertiseHost() == null || cfg.getAdvertiseHost().isBlank()) {
                cfg.advertiseHost(cfg.getBindHost());
            }
            if (cfg.getAdvertisePort() <= 0) {
                cfg.advertisePort(cfg.getBindPort());
            }
            validateConfig(cfg);
            this.config = cfg;
            ready.set(false);
            readyLatch = new CountDownLatch(1);
            membership.init(cfg);
            dispatchHub.init(cfg, statsCollector);
            scheduler = Executors.newScheduledThreadPool(3, new SchedulerThreadFactory(cfg.getNodeId()));
            connectionManager.init(this, cfg, membership, statsCollector);
            broadcastEngine.init(this, cfg, membership, connectionManager, dispatchHub, statsCollector, scheduler);
            startBackgroundTasks();
            started = true;
            if (cfg.getSeedNodes().isEmpty()) {
                markReady();
            }
        }
    }

    public void reinit(VKClusterConfig clusterConfig) {
        synchronized (lock) {
            close();
            init(clusterConfig);
        }
    }

    public boolean started() {
        return started;
    }

    public VKClusterConfig config() {
        ensureStarted();
        return config.copy();
    }

    public void close() {
        synchronized (lock) {
            if (!started && config == null) {
                return;
            }
            try {
                connectionManager.sendLeaveAll();
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignore) {
            }
            broadcastEngine.close();
            connectionManager.close();
            dispatchHub.close();
            ScheduledExecutorService pool = scheduler;
            scheduler = null;
            if (pool != null) {
                pool.shutdownNow();
                try {
                    pool.awaitTermination(Math.max(100, config == null ? 1000 : config.getShutdownWaitMs()), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            started = false;
            config = null;
            ready.set(false);
            readyLatch = new CountDownLatch(1);
        }
    }

    public boolean awaitReady(long timeoutMs) {
        ensureStarted();
        if (ready.get()) {
            return true;
        }
        try {
            return readyLatch.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public VKClusterNode self() {
        ensureStarted();
        return membership.self();
    }

    public List<VKClusterNode> nodes() {
        ensureStarted();
        return membership.nodes();
    }

    public VKClusterNode node(String nodeId) {
        ensureStarted();
        return membership.node(nodeId);
    }

    public VKClusterStats stats() {
        ensureStarted();
        return statsCollector.snapshot(membership.totalNodes(), membership.aliveNodes(), connectionManager.openConnections());
    }

    public VKClusterSubscription on(String topic, VKClusterMessageListener listener) {
        ensureStarted();
        return dispatchHub.on(topic, listener);
    }

    public void off(VKClusterSubscription subscription) {
        ensureStarted();
        dispatchHub.off(subscription);
    }

    public void offAll(String topic) {
        ensureStarted();
        dispatchHub.offAll(topic);
    }

    public CompletableFuture<VKClusterBroadcastResult> broadcast(String topic, byte[] payload) {
        ensureStarted();
        return broadcastEngine.broadcast(topic, payload);
    }

    public CompletableFuture<VKClusterBroadcastResult> broadcastReliable(String topic, byte[] payload) {
        ensureStarted();
        return broadcastEngine.broadcastReliable(topic, payload);
    }

    public CompletableFuture<VKClusterBroadcastResult> broadcastBestEffort(String topic, byte[] payload) {
        ensureStarted();
        return broadcastEngine.broadcastBestEffort(topic, payload);
    }

    public String nextMessageId() {
        return membership.self().getNodeId() + '-' + System.currentTimeMillis() + '-' + messageSeq.getAndIncrement();
    }

    public int maxFrameBytes() {
        ensureStarted();
        int syncOverhead = Math.max(256 * 1024, config.getMaxNodeCount() * 1024);
        return Math.max(config.getMaxMessageBytes() + syncOverhead, 256 * 1024);
    }

    public String signHello(String type, String nonce) {
        return Vostok.Security.hmacSha256(type + '|' + membership.self().getClusterName() + '|'
                + membership.self().getNodeId() + '|' + membership.self().getHost() + '|'
                + membership.self().getPort() + '|' + membership.self().getIncarnation() + '|'
                + nonce, config.getClusterSecret());
    }

    void onFrame(VKClusterConnectionManager.ClusterConnection connection, VKClusterProtocol.Frame frame) {
        long now = System.currentTimeMillis();
        switch (frame.type()) {
            case VKClusterProtocol.HELLO -> handleHello(connection, frame, now);
            case VKClusterProtocol.HELLO_ACK -> handleHelloAck(connection, frame, now);
            case VKClusterProtocol.PING -> handlePing(connection, frame.timestamp(), now);
            case VKClusterProtocol.PONG -> handlePong(connection, now);
            case VKClusterProtocol.MEMBERSHIP_SYNC -> handleMembershipSync(connection, frame.members(), now);
            case VKClusterProtocol.BROADCAST -> handleBroadcast(connection, frame, now);
            case VKClusterProtocol.ACK -> handleAck(connection, frame.messageId(), now);
            case VKClusterProtocol.LEAVE -> handleLeave(frame.nodeId(), frame.incarnation(), frame.timestamp());
            default -> throw new VKClusterException(VKClusterErrorCode.PROTOCOL_ERROR,
                    "Unsupported cluster frame type: " + frame.type());
        }
    }

    void onConnectionClosed(String remoteNodeId) {
        if (!started || remoteNodeId == null || remoteNodeId.isBlank()) {
            return;
        }
        // 断链后不立刻标死，由心跳/超时状态机统一推进到 SUSPECT/DEAD，减少短暂抖动误判。
    }

    private void startBackgroundTasks() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                connectionManager.ensureSeedConnections();
                connectionManager.ensureConnectionsToDiscoveredNodes();
                connectionManager.sendPingAll();
                membership.tick(System.currentTimeMillis());
                broadcastEngine.cleanupDedupe(System.currentTimeMillis());
            } catch (Throwable ignore) {
            }
        }, 200L, config.getHeartbeatIntervalMs(), TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                connectionManager.sendMembershipSyncAll();
                connectionManager.ensureConnectionsToDiscoveredNodes();
            } catch (Throwable ignore) {
            }
        }, 500L, config.getSyncIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void handleHello(VKClusterConnectionManager.ClusterConnection connection,
                             VKClusterProtocol.Frame frame,
                             long now) {
        validateHello(frame, "HELLO");
        if (!membership.canAccept(frame.nodeId())) {
            throw new VKClusterException(VKClusterErrorCode.LIMIT_EXCEEDED,
                    "Cluster node count exceeded maxNodeCount");
        }
        membership.upsertDirect(frame.nodeId(), frame.clusterName(), frame.host(), frame.port(),
                frame.incarnation(), frame.labels(), now);
        connection.authenticate(frame.nodeId(), frame.incarnation());
        connectionManager.sendHelloAck(connection);
        markReady();
    }

    private void handleHelloAck(VKClusterConnectionManager.ClusterConnection connection,
                                VKClusterProtocol.Frame frame,
                                long now) {
        validateHello(frame, "ACK");
        membership.upsertDirect(frame.nodeId(), frame.clusterName(), frame.host(), frame.port(),
                frame.incarnation(), frame.labels(), now);
        connection.authenticate(frame.nodeId(), frame.incarnation());
        membership.mergeMembership(frame.members(), now);
        markReady();
    }

    private void handlePing(VKClusterConnectionManager.ClusterConnection connection, long ts, long now) {
        if (connection.remoteNodeId() != null) {
            membership.markSeen(connection.remoteNodeId(), now);
            connectionManager.sendPong(connection.remoteNodeId(), ts);
        }
    }

    private void handlePong(VKClusterConnectionManager.ClusterConnection connection, long now) {
        if (connection.remoteNodeId() != null) {
            membership.markSeen(connection.remoteNodeId(), now);
        }
    }

    private void handleMembershipSync(VKClusterConnectionManager.ClusterConnection connection,
                                      List<VKClusterProtocol.MemberRecord> members,
                                      long now) {
        if (connection.remoteNodeId() != null) {
            membership.markSeen(connection.remoteNodeId(), now);
        }
        membership.mergeMembership(members, now);
        markReady();
    }

    private void handleBroadcast(VKClusterConnectionManager.ClusterConnection connection,
                                 VKClusterProtocol.Frame frame,
                                 long now) {
        if (connection.remoteNodeId() != null) {
            membership.markSeen(connection.remoteNodeId(), now);
        }
        VKClusterBroadcastEngine.IncomingState incomingState =
                broadcastEngine.acceptIncoming(frame.messageId(), now);
        if (incomingState == VKClusterBroadcastEngine.IncomingState.COMMITTED_DUPLICATE) {
            if (frame.reliable() && connection.remoteNodeId() != null) {
                connectionManager.sendAck(connection.remoteNodeId(), frame.messageId());
            }
            return;
        }
        statsCollector.onMessageReceived();
        VKClusterMessage message = new VKClusterMessage(
                frame.messageId(),
                frame.topic(),
                frame.payload(),
                frame.fromNodeId(),
                frame.reliable(),
                frame.sentAt(),
                now
        );
        boolean accepted = dispatchHub.deliver(message);
        if (accepted) {
            broadcastEngine.markIncomingDelivered(frame.messageId(), now);
            if (frame.reliable() && connection.remoteNodeId() != null) {
                connectionManager.sendAck(connection.remoteNodeId(), frame.messageId());
            }
        }
    }

    private void handleAck(VKClusterConnectionManager.ClusterConnection connection,
                           String messageId,
                           long now) {
        if (connection.remoteNodeId() != null) {
            membership.markSeen(connection.remoteNodeId(), now);
            broadcastEngine.onAck(connection.remoteNodeId(), messageId);
        }
    }

    private void handleLeave(String nodeId, long incarnation, long timestamp) {
        membership.markLeft(nodeId, incarnation, timestamp <= 0 ? System.currentTimeMillis() : timestamp);
        connectionManager.closeConnection(nodeId);
    }

    private void validateHello(VKClusterProtocol.Frame frame, String type) {
        if (!config.getClusterName().equals(frame.clusterName())) {
            statsCollector.onAuthFailure();
            throw new VKClusterException(VKClusterErrorCode.AUTH_ERROR,
                    "Cluster name mismatch: " + frame.clusterName());
        }
        String signText = type + '|' + frame.clusterName() + '|' + frame.nodeId() + '|'
                + frame.host() + '|' + frame.port() + '|' + frame.incarnation() + '|' + frame.nonce();
        String expected = Vostok.Security.hmacSha256(signText, config.getClusterSecret());
        if (!expected.equals(frame.signature())) {
            statsCollector.onAuthFailure();
            throw new VKClusterException(VKClusterErrorCode.AUTH_ERROR,
                    "Cluster HMAC verification failed for node: " + frame.nodeId());
        }
    }

    private void validateConfig(VKClusterConfig cfg) {
        if (cfg.getNodeId() == null || cfg.getNodeId().isBlank()) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster nodeId is blank");
        }
        if (cfg.getClusterName() == null || cfg.getClusterName().isBlank()) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster clusterName is blank");
        }
        if (cfg.getClusterSecret() == null || cfg.getClusterSecret().isBlank()) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster clusterSecret is blank");
        }
        if (cfg.getBindHost() == null || cfg.getBindHost().isBlank()) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster bindHost is blank");
        }
        if (cfg.getBindPort() <= 0 || cfg.getBindPort() > 65535) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster bindPort is invalid");
        }
        if (cfg.getAdvertisePort() > 65535) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster advertisePort is invalid");
        }
        if (cfg.getDeadTimeoutMs() < cfg.getSuspectTimeoutMs()) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR,
                    "Cluster deadTimeoutMs must be >= suspectTimeoutMs");
        }
    }

    private void ensureStarted() {
        if (!started || config == null) {
            throw new VKClusterException(VKClusterErrorCode.STATE_ERROR, "Cluster module is not initialized");
        }
    }

    private void markReady() {
        if (ready.compareAndSet(false, true)) {
            readyLatch.countDown();
        }
    }

    private static final class SchedulerThreadFactory implements ThreadFactory {
        private final String nodeId;
        private final AtomicLong seq = new AtomicLong(1);

        private SchedulerThreadFactory(String nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "vostok-cluster-scheduler-" + nodeId + '-' + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
