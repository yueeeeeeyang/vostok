package yueyang.vostok.game.message;

public class VKGameMessage {
    private final String messageId;
    private final long seq;
    private final VKGameMessageType type;
    private final VKGameMessageScope scope;
    private final String scopeId;
    private final String senderId;
    private final String title;
    private final String content;
    private final Object payload;
    private final long createdAtMs;
    private final long expireAtMs;
    private final String eventId;
    private final long eventVersion;

    public VKGameMessage(String messageId,
                         long seq,
                         VKGameMessageType type,
                         VKGameMessageScope scope,
                         String scopeId,
                         String senderId,
                         String title,
                         String content,
                         Object payload,
                         long createdAtMs,
                         long expireAtMs,
                         String eventId,
                         long eventVersion) {
        this.messageId = messageId;
        this.seq = seq;
        this.type = type;
        this.scope = scope;
        this.scopeId = scopeId;
        this.senderId = senderId;
        this.title = title;
        this.content = content;
        this.payload = payload;
        this.createdAtMs = createdAtMs;
        this.expireAtMs = expireAtMs;
        this.eventId = eventId;
        this.eventVersion = eventVersion;
    }

    public String getMessageId() {
        return messageId;
    }

    public long getSeq() {
        return seq;
    }

    public VKGameMessageType getType() {
        return type;
    }

    public VKGameMessageScope getScope() {
        return scope;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Object getPayload() {
        return payload;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getExpireAtMs() {
        return expireAtMs;
    }

    public String getEventId() {
        return eventId;
    }

    public long getEventVersion() {
        return eventVersion;
    }
}
