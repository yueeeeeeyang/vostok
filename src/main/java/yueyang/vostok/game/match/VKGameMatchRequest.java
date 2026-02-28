package yueyang.vostok.game.match;

/**
 * 匹配请求。
 * rating 为匹配评分（如段位/ELO），region 用于区域优先匹配。
 */
public class VKGameMatchRequest {
    private final String playerId;
    private final String gameType;
    private final int rating;
    private final String region;
    private final int ratingTolerance;
    private final long enqueueAtMs;

    public VKGameMatchRequest(String playerId, String gameType) {
        this(playerId, gameType, 1000, "", 0, System.currentTimeMillis());
    }

    public VKGameMatchRequest(String playerId, String gameType, int rating, String region) {
        this(playerId, gameType, rating, region, 0, System.currentTimeMillis());
    }

    public VKGameMatchRequest(String playerId,
                              String gameType,
                              int rating,
                              String region,
                              int ratingTolerance,
                              long enqueueAtMs) {
        this.playerId = playerId;
        this.gameType = gameType;
        this.rating = rating;
        this.region = region == null ? "" : region;
        this.ratingTolerance = Math.max(0, ratingTolerance);
        this.enqueueAtMs = Math.max(0L, enqueueAtMs);
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getGameType() {
        return gameType;
    }

    public int getRating() {
        return rating;
    }

    public String getRegion() {
        return region;
    }

    public int getRatingTolerance() {
        return ratingTolerance;
    }

    public long getEnqueueAtMs() {
        return enqueueAtMs;
    }
}
