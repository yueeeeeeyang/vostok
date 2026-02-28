package yueyang.vostok.game.core.lifecycle;

import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.VKGameLogic;
import yueyang.vostok.game.room.VKGameRoom;
import yueyang.vostok.game.core.runtime.VKGameRuntimeMetrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 房间生命周期管理器。
 * 负责处理：draining/closed 状态迁移、空闲/空房/寿命策略、drain 超时关闭。
 */
public final class VKGameLifecycleManager {
    private final ConcurrentHashMap<String, VKGameRoom> rooms;
    private final ConcurrentHashMap<String, Integer> roomShardMap;
    private final ConcurrentHashMap<String, Long> roomLastMigratedAt;
    private final ConcurrentHashMap<String, VKGameLogic> logics;
    private final Supplier<VKGameConfig> configSupplier;
    private final VKGameRuntimeMetrics metrics;
    private final BiConsumer<VKGameRoom, String> onRoomDraining;
    private final Consumer<VKGameRoom> onRoomClose;

    public VKGameLifecycleManager(ConcurrentHashMap<String, VKGameRoom> rooms,
                                  ConcurrentHashMap<String, Integer> roomShardMap,
                                  ConcurrentHashMap<String, Long> roomLastMigratedAt,
                                  ConcurrentHashMap<String, VKGameLogic> logics,
                                  Supplier<VKGameConfig> configSupplier,
                                  VKGameRuntimeMetrics metrics,
                                  BiConsumer<VKGameRoom, String> onRoomDraining,
                                  Consumer<VKGameRoom> onRoomClose) {
        this.rooms = rooms;
        this.roomShardMap = roomShardMap;
        this.roomLastMigratedAt = roomLastMigratedAt;
        this.logics = logics;
        this.configSupplier = configSupplier;
        this.metrics = metrics;
        this.onRoomDraining = onRoomDraining;
        this.onRoomClose = onRoomClose;
    }

    /**
     * 外部手动触发 draining：常用于维护窗口、灰度摘流等场景。
     */
    public boolean markRoomDraining(VKGameRoom room, String reason, long now) {
        if (room == null || room.isClosed()) {
            return false;
        }
        String normalizedReason = VKGameLifecycleReason.normalize(reason);
        if (!room.markDraining(normalizedReason, now)) {
            return false;
        }

        metrics.onRoomDraining();
        VKGameLogic logic = logics.get(room.getGameType());
        if (logic != null) {
            onRoomDraining.accept(room, normalizedReason);
        }
        return true;
    }

    /**
     * 按策略推进房间状态：
     * 1) ACTIVE -> DRAINING（空闲/空房/寿命）
     * 2) DRAINING -> CLOSED（无人房立即关闭 or drain 超时强制关闭）
     */
    public void applyLifecyclePolicy(VKGameRoom room, long now) {
        if (room == null || room.isClosed()) {
            return;
        }

        VKGameConfig config = configSupplier.get();
        if (!room.isDraining()) {
            if (config.getRoomMaxLifetimeMs() > 0L && now - room.getCreatedAt() > config.getRoomMaxLifetimeMs()) {
                markRoomDraining(room, VKGameLifecycleReason.MAX_LIFETIME.code(), now);
            } else if (config.getRoomIdleTimeoutMs() > 0L && room.isIdle(config.getRoomIdleTimeoutMs(), now)) {
                markRoomDraining(room, VKGameLifecycleReason.IDLE_TIMEOUT.code(), now);
            } else if (config.getRoomEmptyTimeoutMs() > 0L && room.isEmptyFor(config.getRoomEmptyTimeoutMs(), now)) {
                markRoomDraining(room, VKGameLifecycleReason.EMPTY_TIMEOUT.code(), now);
            }
        }

        if (!room.isDraining()) {
            return;
        }

        String reason = room.getLifecycleReason();
        if (room.getPlayerCount() == 0) {
            closeRoom(room,
                    reason == null || reason.isBlank() ? VKGameLifecycleReason.EMPTY_TIMEOUT.code() : reason,
                    now);
            return;
        }

        long drainTimeoutMs = config.getRoomDrainTimeoutMs();
        if (drainTimeoutMs <= 0L) {
            return;
        }

        long drainingSince = room.getDrainingSinceAt();
        if (drainingSince > 0L && now - drainingSince > drainTimeoutMs) {
            closeRoom(room, VKGameLifecycleReason.DRAIN_TIMEOUT.code(), now);
        }
    }

    /**
     * 真正关闭房间并回收索引。
     * 注意：这里通过 remove(roomId, room) 保证并发安全，只关闭当前实例。
     */
    public boolean closeRoom(VKGameRoom room, String reason, long now) {
        if (room == null) {
            return false;
        }
        String roomId = room.getRoomId();
        if (!rooms.remove(roomId, room)) {
            return false;
        }

        boolean wasDraining = room.isDraining();
        room.markClosed(VKGameLifecycleReason.normalize(reason), now);

        roomShardMap.remove(roomId);
        roomLastMigratedAt.remove(roomId);

        metrics.onRoomClosed(room.getLifecycleReason(), wasDraining);
        onRoomClose.accept(room);
        return true;
    }
}
