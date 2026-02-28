package yueyang.vostok.game.command;

public class VKGameCommand {
    private final String playerId;
    private final String type;
    private final Object payload;
    private final long clientSeq;
    private final long timestampMs;
    private final VKGameCommandPriority priority;

    public VKGameCommand(String playerId, String type, Object payload) {
        this(playerId, type, payload, -1L, System.currentTimeMillis(), VKGameCommandPriority.NORMAL);
    }

    public VKGameCommand(String playerId, String type, Object payload, VKGameCommandPriority priority) {
        this(playerId, type, payload, -1L, System.currentTimeMillis(), priority);
    }

    public VKGameCommand(String playerId, String type, Object payload, long clientSeq) {
        this(playerId, type, payload, clientSeq, System.currentTimeMillis(), VKGameCommandPriority.NORMAL);
    }

    public VKGameCommand(String playerId, String type, Object payload, long clientSeq, VKGameCommandPriority priority) {
        this(playerId, type, payload, clientSeq, System.currentTimeMillis(), priority);
    }

    public VKGameCommand(String playerId, String type, Object payload, long clientSeq, long timestampMs) {
        this(playerId, type, payload, clientSeq, timestampMs, VKGameCommandPriority.NORMAL);
    }

    public VKGameCommand(String playerId,
                         String type,
                         Object payload,
                         long clientSeq,
                         long timestampMs,
                         VKGameCommandPriority priority) {
        this.playerId = playerId;
        this.type = type;
        this.payload = payload;
        this.clientSeq = clientSeq;
        this.timestampMs = timestampMs;
        this.priority = priority == null ? VKGameCommandPriority.NORMAL : priority;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    public long getClientSeq() {
        return clientSeq;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public VKGameCommandPriority getPriority() {
        return priority;
    }
}
