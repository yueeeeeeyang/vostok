package yueyang.vostok.game.core.shard;

import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.shard.VKGameShardMetrics;
import yueyang.vostok.game.core.runtime.VKGameRuntimeMetrics;
import yueyang.vostok.game.core.runtime.VKGameTickStats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分片负载统计与热点迁移策略。
 * 目标：在“全房间每帧触发”的前提下，把热点房间逐步迁移到空闲分片，避免单分片长期过载。
 */
public final class VKGameShardBalancer {
    @FunctionalInterface
    public interface MigrationLogger {
        void log(String roomId, int fromShard, int toShard);
    }

    private final ConcurrentHashMap<String, Integer> roomShardMap;
    private final ConcurrentHashMap<String, Long> roomLastMigratedAt;
    private final Supplier<VKGameConfig> configSupplier;
    private final VKGameRuntimeMetrics metrics;
    private final MigrationLogger migrationLogger;

    public VKGameShardBalancer(ConcurrentHashMap<String, Integer> roomShardMap,
                               ConcurrentHashMap<String, Long> roomLastMigratedAt,
                               Supplier<VKGameConfig> configSupplier,
                               VKGameRuntimeMetrics metrics,
                               MigrationLogger migrationLogger) {
        this.roomShardMap = roomShardMap;
        this.roomLastMigratedAt = roomLastMigratedAt;
        this.configSupplier = configSupplier;
        this.metrics = metrics;
        this.migrationLogger = migrationLogger;
    }

    public void assignInitialShard(String roomId, int shardCount) {
        int idx = shardIndex(roomId, shardCount);
        roomShardMap.putIfAbsent(roomId, idx);
    }

    /**
     * 查询房间当前分片；若不存在则回退到 hash 分片并写回。
     */
    public int shardIndexForRoom(String roomId, int shardCount) {
        Integer assigned = roomShardMap.get(roomId);
        if (assigned != null) {
            int idx = floorMod(assigned, shardCount);
            if (idx != assigned) {
                roomShardMap.put(roomId, idx);
            }
            return idx;
        }
        int idx = shardIndex(roomId, shardCount);
        Integer existing = roomShardMap.putIfAbsent(roomId, idx);
        return existing == null ? idx : floorMod(existing, shardCount);
    }

    /**
     * 热点判定采用三因子：本帧处理命令数、队列积压、本房间处理耗时。
     */
    public boolean isHotRoom(int processedCommands, int queuedCommands, long costNanos) {
        VKGameConfig config = configSupplier.get();
        if (processedCommands >= config.getHotRoomCommandThreshold()) {
            return true;
        }
        if (queuedCommands >= config.getHotRoomQueuedCommandThreshold()) {
            return true;
        }
        long costMs = TimeUnit.NANOSECONDS.toMillis(costNanos);
        return costMs >= config.getHotRoomCostThresholdMs();
    }

    /**
     * 热点评分（P2 #13）：三因子权重改为从 VKGameConfig 读取，不再硬编码 8.0/2.0/1.0。
     * 默认值保持与原逻辑一致（命令权重 8、队列权重 2、耗时权重 1）。
     */
    public double hotRoomScore(int processedCommands, int queuedCommands, long costNanos) {
        VKGameConfig config = configSupplier.get();
        return processedCommands * config.getHotRoomScoreCommandWeight()
                + queuedCommands * config.getHotRoomScoreQueueWeight()
                + TimeUnit.NANOSECONDS.toMillis(costNanos) * config.getHotRoomScoreCostWeight();
    }

    public List<VKGameShardMetrics> buildShardMetrics(VKGameTickStats.TickShardStat[] shardStats) {
        if (shardStats == null || shardStats.length == 0) {
            return List.of();
        }

        ArrayList<VKGameShardMetrics> out = new ArrayList<>(shardStats.length);
        for (VKGameTickStats.TickShardStat stat : shardStats) {
            if (stat == null) {
                continue;
            }
            out.add(new VKGameShardMetrics(
                    stat.shardId,
                    stat.roomCount,
                    stat.commandsProcessed,
                    TimeUnit.NANOSECONDS.toMillis(stat.costNanos),
                    stat.hotRooms.size()
            ));
        }
        return List.copyOf(out);
    }

    /**
     * 迁移策略：
     * - 选择 loadScore 最大/最小分片；
     * - 达到失衡阈值后，从最重分片里选择得分最高热点房间迁移；
     * - 受每帧最大迁移数和冷却时间约束。
     */
    public void rebalanceHotRooms(VKGameTickStats.TickShardStat[] shardStats, long nowMs) {
        if (shardStats == null || shardStats.length <= 1) {
            return;
        }

        VKGameTickStats.TickShardStat max = null;
        VKGameTickStats.TickShardStat min = null;
        int used = 0;

        for (VKGameTickStats.TickShardStat stat : shardStats) {
            if (stat == null) {
                continue;
            }
            long load = stat.loadScore();
            used++;
            if (max == null || load > max.loadScore()) {
                max = stat;
            }
            if (min == null || load < min.loadScore()) {
                min = stat;
            }
        }

        if (used <= 1 || max == null || min == null) {
            return;
        }

        long minLoad = Math.max(1L, min.loadScore());
        long maxLoad = max.loadScore();
        VKGameConfig config = configSupplier.get();
        double ratio = (double) maxLoad / (double) minLoad;
        if (ratio < config.getShardImbalanceThreshold()) {
            return;
        }

        metrics.onShardImbalance();
        if (max.hotRooms.isEmpty()) {
            return;
        }

        max.hotRooms.sort((a, b) -> Double.compare(b.score, a.score));
        int migrated = 0;
        int maxPerTick = Math.max(1, config.getMaxShardMigrationsPerTick());

        for (VKGameTickStats.HotRoomStat hot : max.hotRooms) {
            if (migrated >= maxPerTick) {
                break;
            }
            if (!canMigrateRoom(hot.roomId, nowMs, config.getShardMigrationCooldownMs())) {
                continue;
            }
            roomShardMap.put(hot.roomId, min.shardId);
            roomLastMigratedAt.put(hot.roomId, nowMs);
            metrics.onShardMigration();
            migrated++;
            migrationLogger.log(hot.roomId, max.shardId, min.shardId);
        }
    }

    private boolean canMigrateRoom(String roomId, long nowMs, long cooldownMs) {
        long normalizedCooldownMs = Math.max(0L, cooldownMs);
        if (normalizedCooldownMs == 0L) {
            return true;
        }
        Long last = roomLastMigratedAt.get(roomId);
        return last == null || nowMs - last >= normalizedCooldownMs;
    }

    private static int shardIndex(String roomId, int shardCount) {
        int h = roomId == null ? 0 : roomId.hashCode();
        return floorMod(h, shardCount);
    }

    private static int floorMod(int value, int mod) {
        int idx = value % mod;
        return idx < 0 ? idx + mod : idx;
    }
}
