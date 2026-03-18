package yueyang.vostok.cluster;

/** Cluster 运行时统计快照。 */
public final class VKClusterStats {
    private final int totalNodes;
    private final int aliveNodes;
    private final int openConnections;
    private final long sentFrames;
    private final long receivedFrames;
    private final long sentMessages;
    private final long receivedMessages;
    private final long reliableRetries;
    private final long ackSent;
    private final long ackReceived;
    private final long authFailures;
    private final long protocolErrors;
    private final long queueDrops;
    private final long bytesSent;
    private final long bytesReceived;

    public VKClusterStats(int totalNodes, int aliveNodes, int openConnections,
                          long sentFrames, long receivedFrames,
                          long sentMessages, long receivedMessages,
                          long reliableRetries, long ackSent, long ackReceived,
                          long authFailures, long protocolErrors, long queueDrops,
                          long bytesSent, long bytesReceived) {
        this.totalNodes = totalNodes;
        this.aliveNodes = aliveNodes;
        this.openConnections = openConnections;
        this.sentFrames = sentFrames;
        this.receivedFrames = receivedFrames;
        this.sentMessages = sentMessages;
        this.receivedMessages = receivedMessages;
        this.reliableRetries = reliableRetries;
        this.ackSent = ackSent;
        this.ackReceived = ackReceived;
        this.authFailures = authFailures;
        this.protocolErrors = protocolErrors;
        this.queueDrops = queueDrops;
        this.bytesSent = bytesSent;
        this.bytesReceived = bytesReceived;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public int getAliveNodes() {
        return aliveNodes;
    }

    public int getOpenConnections() {
        return openConnections;
    }

    public long getSentFrames() {
        return sentFrames;
    }

    public long getReceivedFrames() {
        return receivedFrames;
    }

    public long getSentMessages() {
        return sentMessages;
    }

    public long getReceivedMessages() {
        return receivedMessages;
    }

    public long getReliableRetries() {
        return reliableRetries;
    }

    public long getAckSent() {
        return ackSent;
    }

    public long getAckReceived() {
        return ackReceived;
    }

    public long getAuthFailures() {
        return authFailures;
    }

    public long getProtocolErrors() {
        return protocolErrors;
    }

    public long getQueueDrops() {
        return queueDrops;
    }

    public long getBytesSent() {
        return bytesSent;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }
}
