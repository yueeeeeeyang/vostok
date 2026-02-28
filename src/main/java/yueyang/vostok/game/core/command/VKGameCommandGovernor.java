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

        long skew = Math.abs(nowMs - command.getTimestampMs());
        if (config.getMaxClientTimestampSkewMs() > 0 && skew > config.getMaxClientTimestampSkewMs()) {
            return RejectReason.TIME_SKEW;
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
