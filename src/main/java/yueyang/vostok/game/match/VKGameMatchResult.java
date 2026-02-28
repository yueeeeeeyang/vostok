package yueyang.vostok.game.match;

public class VKGameMatchResult {
    private final String ticketId;
    private final String eventId;
    private final long eventVersion;
    private final String playerId;
    private final String gameType;
    private final VKGameMatchStatus status;
    private final String roomId;
    private final String joinToken;
    private final long enqueueAtMs;
    private final long matchedAtMs;
    private final long updatedAtMs;

    public VKGameMatchResult(String ticketId,
                             String eventId,
                             long eventVersion,
                             String playerId,
                             String gameType,
                             VKGameMatchStatus status,
                             String roomId,
                             String joinToken,
                             long enqueueAtMs,
                             long matchedAtMs,
                             long updatedAtMs) {
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.eventVersion = Math.max(0L, eventVersion);
        this.playerId = playerId;
        this.gameType = gameType;
        this.status = status == null ? VKGameMatchStatus.PENDING : status;
        this.roomId = roomId;
        this.joinToken = joinToken;
        this.enqueueAtMs = enqueueAtMs;
        this.matchedAtMs = matchedAtMs;
        this.updatedAtMs = updatedAtMs;
    }

    public static VKGameMatchResult pending(String ticketId,
                                            String playerId,
                                            String gameType,
                                            long enqueueAtMs,
                                            long nowMs) {
        return new VKGameMatchResult(
                ticketId,
                null,
                0L,
                playerId,
                gameType,
                VKGameMatchStatus.PENDING,
                null,
                null,
                enqueueAtMs,
                -1L,
                nowMs
        );
    }

    public static VKGameMatchResult found(String ticketId,
                                          String eventId,
                                          long eventVersion,
                                          String playerId,
                                          String gameType,
                                          String roomId,
                                          String joinToken,
                                          long enqueueAtMs,
                                          long nowMs) {
        return new VKGameMatchResult(
                ticketId,
                eventId,
                eventVersion,
                playerId,
                gameType,
                VKGameMatchStatus.FOUND,
                roomId,
                joinToken,
                enqueueAtMs,
                nowMs,
                nowMs
        );
    }

    public static VKGameMatchResult cancelled(String ticketId,
                                              String playerId,
                                              String gameType,
                                              long enqueueAtMs,
                                              long nowMs) {
        return new VKGameMatchResult(
                ticketId,
                null,
                0L,
                playerId,
                gameType,
                VKGameMatchStatus.CANCELLED,
                null,
                null,
                enqueueAtMs,
                -1L,
                nowMs
        );
    }

    public VKGameMatchResult acked(long nowMs) {
        return new VKGameMatchResult(
                ticketId,
                eventId,
                eventVersion + 1L,
                playerId,
                gameType,
                VKGameMatchStatus.ACKED,
                roomId,
                joinToken,
                enqueueAtMs,
                matchedAtMs,
                nowMs
        );
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getEventId() {
        return eventId;
    }

    public long getEventVersion() {
        return eventVersion;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getGameType() {
        return gameType;
    }

    public VKGameMatchStatus getStatus() {
        return status;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getJoinToken() {
        return joinToken;
    }

    public long getEnqueueAtMs() {
        return enqueueAtMs;
    }

    public long getMatchedAtMs() {
        return matchedAtMs;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }
}
