package yueyang.vostok.game.core.runtime;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Tick 周期内的临时统计模型。
 * 说明：仅服务单次 tick 计算，和对外暴露的 metrics（累计）不同。
 */
public final class VKGameTickStats {
    private VKGameTickStats() {
    }

    public static final class TickShardStat {
        public final int shardId;
        public final int roomCount;
        public int processedRooms;
        public long commandsProcessed;
        public long costNanos;
        public final ArrayList<HotRoomStat> hotRooms = new ArrayList<>();

        public TickShardStat(int shardId, int roomCount) {
            this.shardId = shardId;
            this.roomCount = roomCount;
        }

        /**
         * 分片负载评分：优先看耗时，再叠加命令量/房间处理量，避免单一指标误判。
         */
        public long loadScore() {
            long ms = TimeUnit.NANOSECONDS.toMillis(costNanos);
            return Math.max(1L, ms * 10L + commandsProcessed + processedRooms);
        }
    }

    public static final class HotRoomStat {
        public final String roomId;
        public final double score;

        public HotRoomStat(String roomId, double score) {
            this.roomId = roomId;
            this.score = score;
        }
    }

    public static final class RoomProcessResult {
        public final String roomId;
        public final int commandsProcessed;
        public final long costNanos;
        public final boolean hot;
        public final double hotScore;
        public final boolean skipped;
        public final boolean timeoutDuringRoom;
        public final boolean timeoutBeforeRoom;

        public RoomProcessResult(String roomId,
                                 int commandsProcessed,
                                 long costNanos,
                                 boolean hot,
                                 double hotScore,
                                 boolean skipped,
                                 boolean timeoutDuringRoom,
                                 boolean timeoutBeforeRoom) {
            this.roomId = roomId;
            this.commandsProcessed = commandsProcessed;
            this.costNanos = costNanos;
            this.hot = hot;
            this.hotScore = hotScore;
            this.skipped = skipped;
            this.timeoutDuringRoom = timeoutDuringRoom;
            this.timeoutBeforeRoom = timeoutBeforeRoom;
        }

        public static RoomProcessResult skipped() {
            return new RoomProcessResult("", 0, 0L, false, 0D, true, false, false);
        }

        public static RoomProcessResult timeoutBeforeRoom() {
            return new RoomProcessResult("", 0, 0L, false, 0D, true, false, true);
        }
    }
}
