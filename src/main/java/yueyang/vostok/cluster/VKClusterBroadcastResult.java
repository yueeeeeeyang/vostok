package yueyang.vostok.cluster;

/** 集群广播结果。 */
public final class VKClusterBroadcastResult {
    private final String messageId;
    private final VKClusterBroadcastMode mode;
    private final int targetedNodes;
    private final int ackedNodes;
    private final int failedNodes;
    private final boolean localDelivered;
    private final long costMs;

    public VKClusterBroadcastResult(String messageId, VKClusterBroadcastMode mode,
                                    int targetedNodes, int ackedNodes, int failedNodes,
                                    boolean localDelivered, long costMs) {
        this.messageId = messageId;
        this.mode = mode;
        this.targetedNodes = targetedNodes;
        this.ackedNodes = ackedNodes;
        this.failedNodes = failedNodes;
        this.localDelivered = localDelivered;
        this.costMs = costMs;
    }

    public String getMessageId() {
        return messageId;
    }

    public VKClusterBroadcastMode getMode() {
        return mode;
    }

    public int getTargetedNodes() {
        return targetedNodes;
    }

    public int getAckedNodes() {
        return ackedNodes;
    }

    public int getFailedNodes() {
        return failedNodes;
    }

    public boolean isLocalDelivered() {
        return localDelivered;
    }

    public long getCostMs() {
        return costMs;
    }
}
