package yueyang.vostok.cluster;

import java.util.Arrays;

/** 广播消息快照。 */
public final class VKClusterMessage {
    private final String messageId;
    private final String topic;
    private final byte[] payload;
    private final String fromNodeId;
    private final boolean reliable;
    private final long sentAt;
    private final long receivedAt;

    public VKClusterMessage(String messageId, String topic, byte[] payload,
                            String fromNodeId, boolean reliable,
                            long sentAt, long receivedAt) {
        this.messageId = messageId;
        this.topic = topic;
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        this.fromNodeId = fromNodeId;
        this.reliable = reliable;
        this.sentAt = sentAt;
        this.receivedAt = receivedAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTopic() {
        return topic;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public boolean isReliable() {
        return reliable;
    }

    public long getSentAt() {
        return sentAt;
    }

    public long getReceivedAt() {
        return receivedAt;
    }
}
