package yueyang.vostok.game.shard;

public record VKGameShardMetrics(
        int shardId,
        int roomCount,
        long commandsProcessed,
        long costMs,
        int hotRoomCount
) {
}
