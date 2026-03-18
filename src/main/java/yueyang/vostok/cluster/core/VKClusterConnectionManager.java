package yueyang.vostok.cluster.core;

import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.VKClusterNode;
import yueyang.vostok.cluster.exception.VKClusterErrorCode;
import yueyang.vostok.cluster.exception.VKClusterException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网络连接管理器。
 * 采用单端口接入 + 长连接模式；每个连接一读一写线程，配合有界发送队列控制背压。
 */
final class VKClusterConnectionManager {
    private final Map<String, ClusterConnection> resolvedConnections = new ConcurrentHashMap<>();
    private final Set<String> connectingNodes = ConcurrentHashMap.newKeySet();
    private volatile VKClusterRuntime runtime;
    private volatile VKClusterConfig config;
    private volatile VKClusterMembershipManager membership;
    private volatile VKClusterStatsCollector stats;
    private volatile ServerSocketChannel serverChannel;
    private volatile Thread acceptThread;
    private volatile ExecutorService connectorPool;
    private final AtomicBoolean started = new AtomicBoolean(false);

    void init(VKClusterRuntime runtime,
              VKClusterConfig config,
              VKClusterMembershipManager membership,
              VKClusterStatsCollector stats) {
        close();
        this.runtime = runtime;
        this.config = config;
        this.membership = membership;
        this.stats = stats;
        this.connectorPool = Executors.newFixedThreadPool(
                Math.max(1, config.getIoThreads()),
                new NamedThreadFactory("vostok-cluster-connect")
        );
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverChannel.bind(new InetSocketAddress(config.getBindHost(), config.getBindPort()));
            started.set(true);
            acceptThread = new Thread(this::acceptLoop,
                    "vostok-cluster-accept-" + config.getNodeId());
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException e) {
            throw new VKClusterException(VKClusterErrorCode.IO_ERROR,
                    "Failed to start cluster server on " + config.getBindHost() + ":" + config.getBindPort(), e);
        }
    }

    int openConnections() {
        int count = 0;
        for (ClusterConnection connection : resolvedConnections.values()) {
            if (connection != null && connection.isOpen()) {
                count++;
            }
        }
        return count;
    }

    void ensureSeedConnections() {
        for (String seed : config.getSeedNodes()) {
            Endpoint endpoint = parseEndpoint(seed);
            if (endpoint.port == config.getAdvertisePort() && endpoint.host.equals(config.getAdvertiseHost())) {
                continue;
            }
            connectAsync(null, endpoint.host, endpoint.port);
        }
    }

    void ensureConnectionsToDiscoveredNodes() {
        for (VKClusterNode node : membership.connectCandidates()) {
            ensureConnected(node);
        }
    }

    void ensureConnected(VKClusterNode node) {
        if (node == null || node.isSelf()) {
            return;
        }
        ClusterConnection existing = resolvedConnections.get(node.getNodeId());
        if (existing != null && existing.isOpen()) {
            return;
        }
        connectAsync(node.getNodeId(), node.getHost(), node.getPort());
    }

    boolean sendBroadcast(String nodeId, String messageId, String topic,
                          String fromNodeId, boolean reliable, long sentAt, byte[] payload) {
        ClusterConnection connection = resolvedConnections.get(nodeId);
        if (connection == null || !connection.isOpen()) {
            VKClusterNode node = membership.node(nodeId);
            if (node != null) {
                ensureConnected(node);
            }
            return false;
        }
        return connection.send(VKClusterProtocol.broadcast(messageId, topic, fromNodeId, reliable, sentAt, payload));
    }

    void sendPingAll() {
        long now = System.currentTimeMillis();
        for (ClusterConnection connection : resolvedConnections.values()) {
            if (connection != null && connection.isOpen()) {
                connection.send(VKClusterProtocol.ping(now));
            }
        }
    }

    void sendMembershipSyncAll() {
        VKClusterProtocol.Frame frame = VKClusterProtocol.membershipSync(membership.memberRecords());
        for (ClusterConnection connection : resolvedConnections.values()) {
            if (connection != null && connection.isOpen() && connection.isAuthenticated()) {
                connection.send(frame);
            }
        }
    }

    void sendAck(String nodeId, String messageId) {
        ClusterConnection connection = resolvedConnections.get(nodeId);
        if (connection != null && connection.isOpen()) {
            if (connection.send(VKClusterProtocol.ack(messageId))) {
                stats.onAckSent();
            }
        }
    }

    void sendPong(String nodeId, long timestamp) {
        ClusterConnection connection = resolvedConnections.get(nodeId);
        if (connection != null && connection.isOpen()) {
            connection.send(VKClusterProtocol.pong(timestamp));
        }
    }

    void sendHelloAck(ClusterConnection connection) {
        connection.send(VKClusterProtocol.helloAck(
                membership.self().getClusterName(),
                membership.self().getNodeId(),
                membership.self().getHost(),
                membership.self().getPort(),
                membership.self().getIncarnation(),
                membership.self().getLabels(),
                connection.localNonce,
                runtime.signHello("ACK", connection.localNonce),
                membership.memberRecords()
        ));
    }

    void sendLeaveAll() {
        VKClusterProtocol.Frame leave = VKClusterProtocol.leave(
                membership.self().getNodeId(),
                membership.self().getIncarnation(),
                System.currentTimeMillis());
        for (ClusterConnection connection : resolvedConnections.values()) {
            if (connection != null && connection.isOpen()) {
                connection.send(leave);
            }
        }
    }

    void closeConnection(String nodeId) {
        ClusterConnection connection = resolvedConnections.remove(nodeId);
        if (connection != null) {
            connection.close();
        }
    }

    void close() {
        started.set(false);
        ServerSocketChannel ch = serverChannel;
        serverChannel = null;
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ignore) {
            }
        }
        Thread accept = acceptThread;
        acceptThread = null;
        if (accept != null) {
            accept.interrupt();
        }
        for (ClusterConnection connection : resolvedConnections.values()) {
            if (connection != null) {
                connection.close();
            }
        }
        resolvedConnections.clear();
        connectingNodes.clear();
        ExecutorService pool = connectorPool;
        connectorPool = null;
        if (pool != null) {
            pool.shutdownNow();
            try {
                pool.awaitTermination(Math.max(100, config == null ? 1000 : config.getShutdownWaitMs()), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void acceptLoop() {
        while (started.get()) {
            try {
                ServerSocketChannel server = serverChannel;
                if (server == null) {
                    return;
                }
                SocketChannel channel = server.accept();
                if (channel == null) {
                    continue;
                }
                configureChannel(channel);
                new ClusterConnection(channel, false).start();
            } catch (IOException e) {
                if (started.get()) {
                    stats.onProtocolError();
                }
            }
        }
    }

    private void connectAsync(String nodeIdHint, String host, int port) {
        String key = nodeIdHint == null || nodeIdHint.isBlank() ? host + ':' + port : nodeIdHint;
        if (!connectingNodes.add(key)) {
            return;
        }
        ExecutorService pool = connectorPool;
        if (pool == null) {
            connectingNodes.remove(key);
            return;
        }
        pool.execute(() -> {
            try {
                SocketChannel channel = SocketChannel.open();
                configureChannel(channel);
                channel.socket().connect(new InetSocketAddress(host, port), (int) config.getConnectTimeoutMs());
                new ClusterConnection(channel, true).start();
            } catch (IOException ignore) {
                // 连接失败走下一次定时维护重试
            } finally {
                connectingNodes.remove(key);
            }
        });
    }

    private void configureChannel(SocketChannel channel) throws IOException {
        channel.configureBlocking(true);
        channel.setOption(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
        channel.setOption(StandardSocketOptions.SO_KEEPALIVE, Boolean.TRUE);
    }

    private Endpoint parseEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR, "Cluster seed node is blank");
        }
        int idx = endpoint.lastIndexOf(':');
        if (idx <= 0 || idx == endpoint.length() - 1) {
            throw new VKClusterException(VKClusterErrorCode.CONFIG_ERROR,
                    "Cluster seed node must be host:port, got: " + endpoint);
        }
        return new Endpoint(endpoint.substring(0, idx), Integer.parseInt(endpoint.substring(idx + 1)));
    }

    private ClusterConnection resolveAuthenticatedConnection(ClusterConnection candidate,
                                                             String remoteNodeId,
                                                             long remoteIncarnation) {
        candidate.remoteNodeId = remoteNodeId;
        candidate.remoteIncarnation = remoteIncarnation;
        candidate.authenticated.set(true);
        resolvedConnections.compute(remoteNodeId, (k, existing) -> chooseConnection(existing, candidate));
        ClusterConnection current = resolvedConnections.get(remoteNodeId);
        if (current != candidate) {
            candidate.close();
        }
        return current;
    }

    private ClusterConnection chooseConnection(ClusterConnection existing, ClusterConnection candidate) {
        if (existing == null || !existing.isOpen()) {
            return candidate;
        }
        if (existing == candidate) {
            return existing;
        }
        if (candidate.remoteIncarnation > existing.remoteIncarnation) {
            existing.close();
            return candidate;
        }
        boolean keepOutbound = membership.self().getNodeId().compareTo(candidate.remoteNodeId) < 0;
        ClusterConnection preferred = keepOutbound
                ? preferredByDirection(existing, candidate, true)
                : preferredByDirection(existing, candidate, false);
        ClusterConnection other = preferred == existing ? candidate : existing;
        other.close();
        return preferred;
    }

    private ClusterConnection preferredByDirection(ClusterConnection a, ClusterConnection b, boolean outbound) {
        if (a.outbound == outbound && b.outbound != outbound) {
            return a;
        }
        if (b.outbound == outbound && a.outbound != outbound) {
            return b;
        }
        return a;
    }

    private record Endpoint(String host, int port) {
    }

    final class ClusterConnection {
        private final SocketChannel channel;
        private final boolean outbound;
        private final ArrayBlockingQueue<byte[]> outbox = new ArrayBlockingQueue<>(config.getOutboundQueueCapacity());
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicBoolean authenticated = new AtomicBoolean(false);
        private final String localNonce = UUID.randomUUID().toString();
        private volatile String remoteNodeId;
        private volatile long remoteIncarnation;
        private volatile Thread readerThread;
        private volatile Thread writerThread;

        private ClusterConnection(SocketChannel channel, boolean outbound) {
            this.channel = channel;
            this.outbound = outbound;
        }

        private void start() throws IOException {
            writerThread = new Thread(this::writeLoop,
                    "vostok-cluster-write-" + config.getNodeId() + "-" + System.nanoTime());
            writerThread.setDaemon(true);
            writerThread.start();
            readerThread = new Thread(this::readLoop,
                    "vostok-cluster-read-" + config.getNodeId() + "-" + System.nanoTime());
            readerThread.setDaemon(true);
            readerThread.start();
            send(VKClusterProtocol.hello(
                    membership.self().getClusterName(),
                    membership.self().getNodeId(),
                    membership.self().getHost(),
                    membership.self().getPort(),
                    membership.self().getIncarnation(),
                    membership.self().getLabels(),
                    localNonce,
                    runtime.signHello("HELLO", localNonce)
            ));
        }

        private boolean send(VKClusterProtocol.Frame frame) {
            return enqueue(VKClusterProtocol.encode(frame));
        }

        private boolean enqueue(byte[] frameBytes) {
            if (!isOpen()) {
                return false;
            }
            boolean offered = outbox.offer(frameBytes);
            if (!offered) {
                stats.onQueueDrop();
            }
            return offered;
        }

        private void readLoop() {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(channel.socket().getInputStream()))) {
                while (isOpen()) {
                    int len = in.readInt();
                    if (len <= 0 || len > runtime.maxFrameBytes()) {
                        throw new VKClusterException(VKClusterErrorCode.PROTOCOL_ERROR,
                                "Illegal cluster frame size: " + len);
                    }
                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    stats.onFrameReceived(len);
                    runtime.onFrame(this, VKClusterProtocol.decode(bytes));
                }
            } catch (EOFException ignore) {
                // 对端正常关闭
            } catch (Throwable e) {
                if (isOpen()) {
                    stats.onProtocolError();
                }
            } finally {
                close();
            }
        }

        private void writeLoop() {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(channel.socket().getOutputStream()))) {
                while (isOpen() || !outbox.isEmpty()) {
                    byte[] bytes = outbox.poll(500, TimeUnit.MILLISECONDS);
                    if (bytes == null) {
                        continue;
                    }
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();
                    stats.onFrameSent(bytes.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignore) {
            } finally {
                close();
            }
        }

        boolean isOpen() {
            return open.get();
        }

        boolean isAuthenticated() {
            return authenticated.get();
        }

        String remoteNodeId() {
            return remoteNodeId;
        }

        void authenticate(String remoteNodeId, long remoteIncarnation) {
            resolveAuthenticatedConnection(this, remoteNodeId, remoteIncarnation);
        }

        void close() {
            if (!open.compareAndSet(true, false)) {
                return;
            }
            if (remoteNodeId != null) {
                resolvedConnections.remove(remoteNodeId, this);
                runtime.onConnectionClosed(remoteNodeId);
            }
            try {
                channel.close();
            } catch (IOException ignore) {
            }
            Thread reader = readerThread;
            if (reader != null && reader != Thread.currentThread()) {
                reader.interrupt();
            }
            Thread writer = writerThread;
            if (writer != null && writer != Thread.currentThread()) {
                writer.interrupt();
            }
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
