package yueyang.vostok.cluster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cluster 模块配置。 */
public final class VKClusterConfig {
    private boolean enabled = true;
    private String clusterName = "default";
    private String nodeId = "";
    private String bindHost = "127.0.0.1";
    private int bindPort = 18888;
    private String advertiseHost = "";
    private int advertisePort = 0;
    private final List<String> seedNodes = new ArrayList<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private String clusterSecret = "";
    private int ioThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private int workerThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private long heartbeatIntervalMs = 2_000;
    private long suspectTimeoutMs = 6_000;
    private long deadTimeoutMs = 15_000;
    private long syncIntervalMs = 10_000;
    private long connectTimeoutMs = 3_000;
    private int maxNodeCount = 100;
    private int maxMessageBytes = 1024 * 1024;
    private int outboundQueueCapacity = 4096;
    private int listenerQueueCapacity = 8192;
    private long reliableAckTimeoutMs = 1_500;
    private int reliableMaxRetries = 3;
    private long reliableRetryBaseMs = 500;
    private long dedupeRetentionMs = 60_000;
    private boolean includeSelfOnBroadcast = true;
    private long shutdownWaitMs = 3_000;

    public VKClusterConfig copy() {
        return new VKClusterConfig()
                .enabled(enabled)
                .clusterName(clusterName)
                .nodeId(nodeId)
                .bindHost(bindHost)
                .bindPort(bindPort)
                .advertiseHost(advertiseHost)
                .advertisePort(advertisePort)
                .seedNodes(seedNodes)
                .labels(labels)
                .clusterSecret(clusterSecret)
                .ioThreads(ioThreads)
                .workerThreads(workerThreads)
                .heartbeatIntervalMs(heartbeatIntervalMs)
                .suspectTimeoutMs(suspectTimeoutMs)
                .deadTimeoutMs(deadTimeoutMs)
                .syncIntervalMs(syncIntervalMs)
                .connectTimeoutMs(connectTimeoutMs)
                .maxNodeCount(maxNodeCount)
                .maxMessageBytes(maxMessageBytes)
                .outboundQueueCapacity(outboundQueueCapacity)
                .listenerQueueCapacity(listenerQueueCapacity)
                .reliableAckTimeoutMs(reliableAckTimeoutMs)
                .reliableMaxRetries(reliableMaxRetries)
                .reliableRetryBaseMs(reliableRetryBaseMs)
                .dedupeRetentionMs(dedupeRetentionMs)
                .includeSelfOnBroadcast(includeSelfOnBroadcast)
                .shutdownWaitMs(shutdownWaitMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public VKClusterConfig enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getClusterName() {
        return clusterName;
    }

    public VKClusterConfig clusterName(String clusterName) {
        this.clusterName = clusterName == null ? "" : clusterName.trim();
        return this;
    }

    public String getNodeId() {
        return nodeId;
    }

    public VKClusterConfig nodeId(String nodeId) {
        this.nodeId = nodeId == null ? "" : nodeId.trim();
        return this;
    }

    public String getBindHost() {
        return bindHost;
    }

    public VKClusterConfig bindHost(String bindHost) {
        this.bindHost = bindHost == null ? "" : bindHost.trim();
        return this;
    }

    public int getBindPort() {
        return bindPort;
    }

    public VKClusterConfig bindPort(int bindPort) {
        this.bindPort = bindPort;
        return this;
    }

    public String getAdvertiseHost() {
        return advertiseHost;
    }

    public VKClusterConfig advertiseHost(String advertiseHost) {
        this.advertiseHost = advertiseHost == null ? "" : advertiseHost.trim();
        return this;
    }

    public int getAdvertisePort() {
        return advertisePort;
    }

    public VKClusterConfig advertisePort(int advertisePort) {
        this.advertisePort = advertisePort;
        return this;
    }

    public List<String> getSeedNodes() {
        return List.copyOf(seedNodes);
    }

    public VKClusterConfig seedNodes(List<String> seedNodes) {
        this.seedNodes.clear();
        if (seedNodes != null) {
            for (String seedNode : seedNodes) {
                if (seedNode != null && !seedNode.trim().isEmpty()) {
                    this.seedNodes.add(seedNode.trim());
                }
            }
        }
        return this;
    }

    public VKClusterConfig seedNodes(String... seedNodes) {
        this.seedNodes.clear();
        if (seedNodes != null) {
            for (String seedNode : seedNodes) {
                if (seedNode != null && !seedNode.trim().isEmpty()) {
                    this.seedNodes.add(seedNode.trim());
                }
            }
        }
        return this;
    }

    public Map<String, String> getLabels() {
        return Map.copyOf(labels);
    }

    public VKClusterConfig labels(Map<String, String> labels) {
        this.labels.clear();
        if (labels != null) {
            labels.forEach((k, v) -> {
                if (k != null && !k.trim().isEmpty()) {
                    this.labels.put(k.trim(), v == null ? "" : v);
                }
            });
        }
        return this;
    }

    public VKClusterConfig label(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            this.labels.put(key.trim(), value == null ? "" : value);
        }
        return this;
    }

    public String getClusterSecret() {
        return clusterSecret;
    }

    public VKClusterConfig clusterSecret(String clusterSecret) {
        this.clusterSecret = clusterSecret == null ? "" : clusterSecret;
        return this;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public VKClusterConfig ioThreads(int ioThreads) {
        this.ioThreads = Math.max(1, ioThreads);
        return this;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public VKClusterConfig workerThreads(int workerThreads) {
        this.workerThreads = Math.max(1, workerThreads);
        return this;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public VKClusterConfig heartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = Math.max(200, heartbeatIntervalMs);
        return this;
    }

    public long getSuspectTimeoutMs() {
        return suspectTimeoutMs;
    }

    public VKClusterConfig suspectTimeoutMs(long suspectTimeoutMs) {
        this.suspectTimeoutMs = Math.max(heartbeatIntervalMs, suspectTimeoutMs);
        return this;
    }

    public long getDeadTimeoutMs() {
        return deadTimeoutMs;
    }

    public VKClusterConfig deadTimeoutMs(long deadTimeoutMs) {
        this.deadTimeoutMs = Math.max(suspectTimeoutMs, deadTimeoutMs);
        return this;
    }

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public VKClusterConfig syncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = Math.max(500, syncIntervalMs);
        return this;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKClusterConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = Math.max(200, connectTimeoutMs);
        return this;
    }

    public int getMaxNodeCount() {
        return maxNodeCount;
    }

    public VKClusterConfig maxNodeCount(int maxNodeCount) {
        this.maxNodeCount = Math.max(1, maxNodeCount);
        return this;
    }

    public int getMaxMessageBytes() {
        return maxMessageBytes;
    }

    public VKClusterConfig maxMessageBytes(int maxMessageBytes) {
        this.maxMessageBytes = Math.max(1024, maxMessageBytes);
        return this;
    }

    public int getOutboundQueueCapacity() {
        return outboundQueueCapacity;
    }

    public VKClusterConfig outboundQueueCapacity(int outboundQueueCapacity) {
        this.outboundQueueCapacity = Math.max(1, outboundQueueCapacity);
        return this;
    }

    public int getListenerQueueCapacity() {
        return listenerQueueCapacity;
    }

    public VKClusterConfig listenerQueueCapacity(int listenerQueueCapacity) {
        this.listenerQueueCapacity = Math.max(1, listenerQueueCapacity);
        return this;
    }

    public long getReliableAckTimeoutMs() {
        return reliableAckTimeoutMs;
    }

    public VKClusterConfig reliableAckTimeoutMs(long reliableAckTimeoutMs) {
        this.reliableAckTimeoutMs = Math.max(50, reliableAckTimeoutMs);
        return this;
    }

    public int getReliableMaxRetries() {
        return reliableMaxRetries;
    }

    public VKClusterConfig reliableMaxRetries(int reliableMaxRetries) {
        this.reliableMaxRetries = Math.max(0, reliableMaxRetries);
        return this;
    }

    public long getReliableRetryBaseMs() {
        return reliableRetryBaseMs;
    }

    public VKClusterConfig reliableRetryBaseMs(long reliableRetryBaseMs) {
        this.reliableRetryBaseMs = Math.max(50, reliableRetryBaseMs);
        return this;
    }

    public long getDedupeRetentionMs() {
        return dedupeRetentionMs;
    }

    public VKClusterConfig dedupeRetentionMs(long dedupeRetentionMs) {
        this.dedupeRetentionMs = Math.max(1000, dedupeRetentionMs);
        return this;
    }

    public boolean isIncludeSelfOnBroadcast() {
        return includeSelfOnBroadcast;
    }

    public VKClusterConfig includeSelfOnBroadcast(boolean includeSelfOnBroadcast) {
        this.includeSelfOnBroadcast = includeSelfOnBroadcast;
        return this;
    }

    public long getShutdownWaitMs() {
        return shutdownWaitMs;
    }

    public VKClusterConfig shutdownWaitMs(long shutdownWaitMs) {
        this.shutdownWaitMs = Math.max(0, shutdownWaitMs);
        return this;
    }
}
