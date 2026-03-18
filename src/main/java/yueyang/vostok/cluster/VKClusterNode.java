package yueyang.vostok.cluster;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 集群节点快照。 */
public final class VKClusterNode {
    private final String nodeId;
    private final String clusterName;
    private final String host;
    private final int port;
    private final VKClusterNodeStatus status;
    private final Map<String, String> labels;
    private final boolean self;
    private final long incarnation;
    private final long discoveredAt;
    private final long lastSeenAt;

    public VKClusterNode(String nodeId, String clusterName, String host, int port,
                         VKClusterNodeStatus status, Map<String, String> labels,
                         boolean self, long incarnation, long discoveredAt, long lastSeenAt) {
        this.nodeId = nodeId;
        this.clusterName = clusterName;
        this.host = host;
        this.port = port;
        this.status = status;
        this.labels = labels == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        this.self = self;
        this.incarnation = incarnation;
        this.discoveredAt = discoveredAt;
        this.lastSeenAt = lastSeenAt;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public VKClusterNodeStatus getStatus() {
        return status;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public boolean isSelf() {
        return self;
    }

    public long getIncarnation() {
        return incarnation;
    }

    public long getDiscoveredAt() {
        return discoveredAt;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }
}
