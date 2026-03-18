package yueyang.vostok.cluster.core;

import yueyang.vostok.cluster.VKClusterConfig;
import yueyang.vostok.cluster.VKClusterNode;
import yueyang.vostok.cluster.VKClusterNodeStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点成员表与状态机。
 * 首版按 <=100 节点优化，优先保证状态收敛与实现稳定性。
 */
final class VKClusterMembershipManager {
    private final ConcurrentHashMap<String, NodeState> nodes = new ConcurrentHashMap<>();
    private volatile VKClusterConfig config;
    private volatile NodeState selfState;

    void init(VKClusterConfig clusterConfig) {
        this.config = clusterConfig.copy();
        nodes.clear();
        long now = System.currentTimeMillis();
        String advertiseHost = clusterConfig.getAdvertiseHost() == null || clusterConfig.getAdvertiseHost().isBlank()
                ? clusterConfig.getBindHost()
                : clusterConfig.getAdvertiseHost();
        int advertisePort = clusterConfig.getAdvertisePort() <= 0
                ? clusterConfig.getBindPort()
                : clusterConfig.getAdvertisePort();
        NodeState self = new NodeState(
                clusterConfig.getNodeId(),
                clusterConfig.getClusterName(),
                advertiseHost,
                advertisePort,
                VKClusterNodeStatus.ALIVE,
                clusterConfig.getLabels(),
                true,
                System.currentTimeMillis(),
                now,
                now
        );
        selfState = self;
        nodes.put(self.nodeId, self);
    }

    VKClusterNode self() {
        return selfState.snapshot();
    }

    VKClusterNode node(String nodeId) {
        NodeState state = nodes.get(nodeId);
        return state == null ? null : state.snapshot();
    }

    List<VKClusterNode> nodes() {
        return nodes.values().stream()
                .map(NodeState::snapshot)
                .sorted(Comparator.comparing(VKClusterNode::getNodeId))
                .toList();
    }

    int totalNodes() {
        return nodes.size();
    }

    int aliveNodes() {
        int count = 0;
        for (NodeState state : nodes.values()) {
            if (state.status == VKClusterNodeStatus.ALIVE) {
                count++;
            }
        }
        return count;
    }

    List<VKClusterNode> aliveRemoteNodes() {
        List<VKClusterNode> out = new ArrayList<>();
        for (NodeState state : nodes.values()) {
            if (!state.self && state.status == VKClusterNodeStatus.ALIVE) {
                out.add(state.snapshot());
            }
        }
        out.sort(Comparator.comparing(VKClusterNode::getNodeId));
        return out;
    }

    List<VKClusterNode> connectCandidates() {
        List<VKClusterNode> out = new ArrayList<>();
        for (NodeState state : nodes.values()) {
            if (state.self) {
                continue;
            }
            if (state.status == VKClusterNodeStatus.ALIVE
                    || state.status == VKClusterNodeStatus.JOINING
                    || state.status == VKClusterNodeStatus.SUSPECT) {
                out.add(state.snapshot());
            }
        }
        out.sort(Comparator.comparing(VKClusterNode::getNodeId));
        return out;
    }

    boolean canAccept(String nodeId) {
        return nodes.containsKey(nodeId) || nodes.size() < config.getMaxNodeCount();
    }

    VKClusterNode upsertDirect(String nodeId, String clusterName, String host, int port,
                               long incarnation, Map<String, String> labels, long now) {
        if (selfState.nodeId.equals(nodeId)) {
            return selfState.snapshot();
        }
        nodes.compute(nodeId, (k, current) -> merge(current,
                new NodeState(nodeId, clusterName, host, port, VKClusterNodeStatus.ALIVE,
                        labels, false, incarnation, now, now),
                true, now));
        return node(nodeId);
    }

    void markSeen(String nodeId, long now) {
        if (nodeId == null || selfState.nodeId.equals(nodeId)) {
            return;
        }
        nodes.computeIfPresent(nodeId, (k, current) -> current.withStatus(VKClusterNodeStatus.ALIVE, now));
    }

    void markLeft(String nodeId, long incarnation, long now) {
        if (nodeId == null || selfState.nodeId.equals(nodeId)) {
            return;
        }
        nodes.computeIfPresent(nodeId, (k, current) -> {
            if (incarnation < current.incarnation) {
                return current;
            }
            return new NodeState(current.nodeId, current.clusterName, current.host, current.port,
                    VKClusterNodeStatus.LEFT, current.labels, false,
                    incarnation, current.discoveredAt, now);
        });
    }

    void mergeMembership(List<VKClusterProtocol.MemberRecord> members, long now) {
        if (members == null) {
            return;
        }
        for (VKClusterProtocol.MemberRecord member : members) {
            if (member == null || selfState.nodeId.equals(member.nodeId())) {
                continue;
            }
            if (!selfState.clusterName.equals(member.clusterName())) {
                continue;
            }
            if (!canAccept(member.nodeId())) {
                continue;
            }
            NodeState incoming = new NodeState(
                    member.nodeId(),
                    member.clusterName(),
                    member.host(),
                    member.port(),
                    member.status(),
                    member.labels(),
                    false,
                    member.incarnation(),
                    member.discoveredAt(),
                    member.lastSeenAt()
            );
            nodes.compute(member.nodeId(), (k, current) -> merge(current, incoming, false, now));
        }
    }

    void tick(long now) {
        for (Map.Entry<String, NodeState> entry : nodes.entrySet()) {
            NodeState state = entry.getValue();
            if (state.self || state.status == VKClusterNodeStatus.LEFT || state.status == VKClusterNodeStatus.DEAD) {
                continue;
            }
            long idle = now - state.lastSeenAt;
            if (idle >= config.getDeadTimeoutMs()) {
                nodes.computeIfPresent(entry.getKey(), (k, current) -> current.withStatus(VKClusterNodeStatus.DEAD, current.lastSeenAt));
            } else if (idle >= config.getSuspectTimeoutMs()) {
                nodes.computeIfPresent(entry.getKey(), (k, current) -> {
                    if (current.status == VKClusterNodeStatus.ALIVE || current.status == VKClusterNodeStatus.JOINING) {
                        return current.withStatus(VKClusterNodeStatus.SUSPECT, current.lastSeenAt);
                    }
                    return current;
                });
            }
        }
    }

    List<VKClusterProtocol.MemberRecord> memberRecords() {
        return nodes.values().stream()
                .sorted(Comparator.comparing(state -> state.nodeId))
                .map(NodeState::record)
                .toList();
    }

    private NodeState merge(NodeState current, NodeState incoming, boolean direct, long now) {
        if (current == null) {
            return new NodeState(
                    incoming.nodeId,
                    incoming.clusterName,
                    incoming.host,
                    incoming.port,
                    direct ? VKClusterNodeStatus.ALIVE : normalizeIncomingStatus(incoming.status),
                    incoming.labels,
                    false,
                    incoming.incarnation,
                    incoming.discoveredAt > 0 ? incoming.discoveredAt : now,
                    incoming.lastSeenAt > 0 ? incoming.lastSeenAt : now
            );
        }
        if (incoming.incarnation > current.incarnation) {
            return new NodeState(
                    incoming.nodeId,
                    incoming.clusterName,
                    incoming.host,
                    incoming.port,
                    direct ? VKClusterNodeStatus.ALIVE : normalizeIncomingStatus(incoming.status),
                    incoming.labels,
                    false,
                    incoming.incarnation,
                    current.discoveredAt,
                    Math.max(now, incoming.lastSeenAt)
            );
        }
        if (incoming.incarnation < current.incarnation) {
            return current;
        }
        long mergedLastSeen = Math.max(current.lastSeenAt, incoming.lastSeenAt);
        VKClusterNodeStatus status = current.status;
        if (direct) {
            status = VKClusterNodeStatus.ALIVE;
            mergedLastSeen = now;
        } else if (incoming.status == VKClusterNodeStatus.LEFT || incoming.status == VKClusterNodeStatus.DEAD) {
            if (incoming.lastSeenAt >= current.lastSeenAt) {
                status = incoming.status;
            }
        } else if (incoming.lastSeenAt >= current.lastSeenAt) {
            status = normalizeIncomingStatus(incoming.status);
        }
        return new NodeState(
                current.nodeId,
                current.clusterName,
                incoming.host == null || incoming.host.isBlank() ? current.host : incoming.host,
                incoming.port <= 0 ? current.port : incoming.port,
                status,
                incoming.labels == null || incoming.labels.isEmpty() ? current.labels : incoming.labels,
                false,
                current.incarnation,
                current.discoveredAt,
                mergedLastSeen
        );
    }

    private VKClusterNodeStatus normalizeIncomingStatus(VKClusterNodeStatus status) {
        if (status == null) {
            return VKClusterNodeStatus.JOINING;
        }
        return switch (status) {
            case ALIVE -> VKClusterNodeStatus.ALIVE;
            case SUSPECT -> VKClusterNodeStatus.SUSPECT;
            case DEAD -> VKClusterNodeStatus.DEAD;
            case LEFT -> VKClusterNodeStatus.LEFT;
            case JOINING -> VKClusterNodeStatus.JOINING;
        };
    }

    private static final class NodeState {
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

        private NodeState(String nodeId, String clusterName, String host, int port,
                          VKClusterNodeStatus status, Map<String, String> labels,
                          boolean self, long incarnation, long discoveredAt, long lastSeenAt) {
            this.nodeId = nodeId;
            this.clusterName = clusterName;
            this.host = host;
            this.port = port;
            this.status = status;
            this.labels = labels == null ? Map.of() : Map.copyOf(labels);
            this.self = self;
            this.incarnation = incarnation;
            this.discoveredAt = discoveredAt;
            this.lastSeenAt = lastSeenAt;
        }

        private NodeState withStatus(VKClusterNodeStatus status, long lastSeenAt) {
            return new NodeState(nodeId, clusterName, host, port, status, labels, self,
                    incarnation, discoveredAt, lastSeenAt);
        }

        private VKClusterNode snapshot() {
            return new VKClusterNode(nodeId, clusterName, host, port, status, labels,
                    self, incarnation, discoveredAt, lastSeenAt);
        }

        private VKClusterProtocol.MemberRecord record() {
            return new VKClusterProtocol.MemberRecord(nodeId, clusterName, host, port,
                    status, labels, incarnation, discoveredAt, lastSeenAt);
        }
    }
}
