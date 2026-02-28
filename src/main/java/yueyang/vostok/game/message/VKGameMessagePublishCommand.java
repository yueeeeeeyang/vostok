package yueyang.vostok.game.message;

public class VKGameMessagePublishCommand {
    private final VKGameMessageType type;
    private final VKGameMessageScope scope;
    private final String scopeId;
    private final String senderId;
    private final String title;
    private final String content;
    private final Object payload;
    private final long expireAtMs;

    public VKGameMessagePublishCommand(VKGameMessageType type,
                                       VKGameMessageScope scope,
                                       String scopeId,
                                       String senderId,
                                       String title,
                                       String content,
                                       Object payload,
                                       long expireAtMs) {
        this.type = type;
        this.scope = scope;
        this.scopeId = scopeId;
        this.senderId = senderId;
        this.title = title;
        this.content = content;
        this.payload = payload;
        this.expireAtMs = expireAtMs;
    }

    public VKGameMessagePublishCommand(VKGameMessageType type,
                                       VKGameMessageScope scope,
                                       String scopeId,
                                       String senderId,
                                       String content) {
        this(type, scope, scopeId, senderId, "", content, null, 0L);
    }

    public static VKGameMessagePublishCommand playerChat(String roomId, String playerId, String content) {
        return new VKGameMessagePublishCommand(
                VKGameMessageType.PLAYER_CHAT,
                VKGameMessageScope.ROOM,
                roomId,
                playerId,
                "",
                content,
                null,
                0L
        );
    }

    public static VKGameMessagePublishCommand systemNotice(VKGameMessageScope scope,
                                                           String scopeId,
                                                           String title,
                                                           String content) {
        return new VKGameMessagePublishCommand(
                VKGameMessageType.SYSTEM_NOTICE,
                scope,
                scopeId,
                "SYSTEM",
                title,
                content,
                null,
                0L
        );
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

    public long getExpireAtMs() {
        return expireAtMs;
    }
}
