package yueyang.vostok.game.core.command;

import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 命令治理与反作弊守卫。
 * 当前包含：
 * 1) 玩家身份/在线态校验
 * 2) 客户端时间戳偏差校验
 * 3) clientSeq 单调递增校验（防重放）
 * 4) 单玩家 QPS 限流
 */
public final class VKGameCommandGovernor {
    public enum RejectReason {
        NONE,
        PLAYER_NOT_FOUND,
        PLAYER_OFFLINE,
        TIME_SKEW,
        NON_MONOTONIC_SEQ,
        RATE_LIMIT
    }

    private static final class PlayerCommandState {
        private final AtomicLong windowStartMs = new AtomicLong(0L);
        private final AtomicInteger windowCount = new AtomicInteger(0);
        private final AtomicLong lastClientSeq = new AtomicLong(Long.MIN_VALUE);
    }

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, PlayerCommandState>> roomStates =
            new ConcurrentHashMap<>();
    private final Supplier<VKGameConfig> configSupplier;

    public VKGameCommandGovernor(Supplier<VKGameConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public RejectReason validate(VKGameRoom room, VKGameCommand command, long nowMs) {
        VKGameConfig config = configSupplier.get();
        if (!config.isAntiCheatEnabled()) {
            return RejectReason.NONE;
        }

        VKGamePlayerSession player = room.player(command.getPlayerId());
        if (player == null && config.isRequireOnlinePlayerCommand()) {
            return RejectReason.PLAYER_NOT_FOUND;
        }
        if (player != null && config.isRequireOnlinePlayerCommand() && !player.isOnline()) {
            return RejectReason.PLAYER_OFFLINE;
        }

        // 若未要求命令必须绑定在线玩家，则仅做轻量校验并跳过玩家维度限频/序列检查。
        if (player == null) {
            return RejectReason.NONE;
        }

        // 时间戳偏差校验（P0 #2）：
        // 直接用 nowMs - cmdTs 存在整数溢出风险（如 cmdTs=Long.MIN_VALUE 时结果为负）。
        // nowMs 来自 System.currentTimeMillis()，永远 >= 0；客户端正常时间戳同样 >= 0。
        // 因此：负数 cmdTs 必定超出任何合理偏差阈值，直接拒绝。
        // 对于非负 cmdTs，nowMs 和 cmdTs 符号相同，减法结果不会跨越最大正值，安全比较。
        long maxSkew = config.getMaxClientTimestampSkewMs();
        if (maxSkew > 0) {
            long cmdTs = command.getTimestampMs();
            if (cmdTs < 0) {
                // 负数时间戳（含 Long.MIN_VALUE）在 nowMs >= 0 时偏差无限大，直接拒绝
                return RejectReason.TIME_SKEW;
            }
            // 此时 cmdTs >= 0，nowMs >= 0，减法不会产生溢出绕环
            boolean tooOld = cmdTs < nowMs && (nowMs - cmdTs) > maxSkew;
            boolean tooNew = cmdTs > nowMs && (cmdTs - nowMs) > maxSkew;
            if (tooOld || tooNew) {
                return RejectReason.TIME_SKEW;
            }
        }

        PlayerCommandState state = state(room.getRoomId(), command.getPlayerId());
        if (config.isEnforceMonotonicClientSeq() && command.getClientSeq() >= 0) {
            long prev = state.lastClientSeq.get();
            if (prev != Long.MIN_VALUE && command.getClientSeq() <= prev) {
                return RejectReason.NON_MONOTONIC_SEQ;
            }
            state.lastClientSeq.set(command.getClientSeq());
        }

        int limit = Math.max(1, config.getMaxCommandsPerSecondPerPlayer());
        // 对单个玩家的窗口状态加锁，消除多线程并发重置窗口时的 race condition：
        // 若不加锁，多个线程同时看到过期窗口各自将 count 置 1 并全部放行，绕过 QPS 限制。
        synchronized (state) {
            long ws = state.windowStartMs.get();
            if (ws <= 0L || nowMs - ws >= 1000L) {
                state.windowStartMs.set(nowMs);
                state.windowCount.set(1);
                return RejectReason.NONE;
            }
            int count = state.windowCount.incrementAndGet();
            if (count > limit) {
                return RejectReason.RATE_LIMIT;
            }
        }
        return RejectReason.NONE;
    }

    public void removePlayer(String roomId, String playerId) {
        if (roomId == null || playerId == null) {
            return;
        }
        ConcurrentHashMap<String, PlayerCommandState> roomMap = roomStates.get(roomId);
        if (roomMap == null) {
            return;
        }
        roomMap.remove(playerId);
        if (roomMap.isEmpty()) {
            roomStates.remove(roomId, roomMap);
        }
    }

    public void removeRoom(String roomId) {
        if (roomId == null) {
            return;
        }
        roomStates.remove(roomId);
    }

    public void clear() {
        roomStates.clear();
    }

    private PlayerCommandState state(String roomId, String playerId) {
        ConcurrentHashMap<String, PlayerCommandState> roomMap =
                roomStates.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
        return roomMap.computeIfAbsent(playerId, k -> new PlayerCommandState());
    }
}
