package yueyang.vostok.game.room;

import yueyang.vostok.game.command.VKGameCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class VKGameRoom {
    private final String roomId;
    private final String gameType;
    private final long createdAt;
    // 是否启用帧同步模式：创建房间时指定，运行时只读。
    // 帧同步模式下，每帧结束后将本帧所有玩家输入打包广播给客户端；
    // 与状态同步模式（默认）并不互斥，onTick/onCommand 回调仍然执行。
    private final boolean frameSyncEnabled;
    private final AtomicLong lastActiveAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong emptySinceAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong tick = new AtomicLong(0L);
    private final Map<String, VKGamePlayerSession> players = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<VKGameCommand> highPriorityQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<VKGameCommand> normalPriorityQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<VKGameCommand> lowPriorityQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedCommands = new AtomicInteger(0);
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final AtomicReference<VKGameRoomState> state = new AtomicReference<>(VKGameRoomState.ACTIVE);
    private final AtomicLong drainingSinceAt = new AtomicLong(-1L);
    private final AtomicLong closedAt = new AtomicLong(-1L);
    private final AtomicReference<String> lifecycleReason = new AtomicReference<>("");
    // 最近一次被 Tick 跳过时的全局帧序号（0=从未被跳过），用于下帧优先调度被饥饿的房间
    private final AtomicLong lastSkippedTickNo = new AtomicLong(0L);
    // 最近一次被 Tick 实际处理时的全局帧序号（0=从未被处理），用于同等 lastSkippedTickNo 时的次级排序：
    // 处理帧序号越小的房间越优先，确保跳过组内先被跳过的房间优先、避免连续得分相同房间长期垄断。
    private final AtomicLong lastProcessedTickNo = new AtomicLong(0L);
    // 连续发生逻辑异常的 Tick 次数（仅统计 onTick/onCommand 异常），超阈值后触发隔离
    private final AtomicInteger consecutiveLogicErrors = new AtomicInteger(0);
    // P0-2: 上次实际执行 onTick 的时刻（毫秒）；仅 tick 线程写/读，volatile 保证可见性
    private volatile long lastTickedAtMs = 0L;
    // P1-4: 上次执行 applyLifecyclePolicy 的时刻（毫秒）；idle/drain 超时秒级，无需每帧检查
    private volatile long lastLifecycleCheckAt = 0L;
    // P1-5: 上次执行 cleanupHostedSessions 的时刻（毫秒）；reconnectGraceMs 默认 60s，每帧清理纯浪费
    private volatile long lastSessionCleanupAt = 0L;

    public VKGameRoom(String roomId, String gameType) {
        this(roomId, gameType, false);
    }

    public VKGameRoom(String roomId, String gameType, boolean frameSyncEnabled) {
        this.roomId = roomId;
        this.gameType = gameType;
        this.frameSyncEnabled = frameSyncEnabled;
        this.createdAt = System.currentTimeMillis();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getGameType() {
        return gameType;
    }

    public boolean isFrameSyncEnabled() {
        return frameSyncEnabled;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastActiveAt() {
        return lastActiveAt.get();
    }

    public long getCurrentTick() {
        return tick.get();
    }

    public long nextTick() {
        return tick.incrementAndGet();
    }

    /**
     * 恢复场景使用：将当前 tick 指针对齐到快照值。
     */
    public void restoreTick(long tickNo) {
        tick.set(Math.max(0L, tickNo));
    }

    public int getPlayerCount() {
        return players.size();
    }

    public List<VKGamePlayerSession> players() {
        return List.copyOf(players.values());
    }

    public VKGamePlayerSession player(String playerId) {
        return players.get(playerId);
    }

    public VKGamePlayerSession joinPlayer(String playerId, int maxPlayersPerRoom) {
        VKGamePlayerSession existing = players.get(playerId);
        if (existing != null) {
            existing.setOnline(true);
            touch();
            return existing;
        }
        // 先乐观检查（快速拒绝大多数超容请求），再通过 putIfAbsent 做原子插入。
        // 插入后若 size 超过上限（并发加入导致的 TOCTOU），立即回滚并返回 null。
        if (players.size() >= maxPlayersPerRoom) {
            return null;
        }
        VKGamePlayerSession created = new VKGamePlayerSession(playerId);
        VKGamePlayerSession prev = players.putIfAbsent(playerId, created);
        if (prev == null) {
            // 本线程成功插入了新玩家；检查插入后容量是否仍合法
            if (players.size() > maxPlayersPerRoom) {
                // 并发超容：回滚插入，拒绝此次加入
                players.remove(playerId, created);
                return null;
            }
        }
        VKGamePlayerSession out = prev == null ? created : prev;
        out.setOnline(true);
        if (players.size() > 0) {
            emptySinceAt.set(-1L);
        }
        touch();
        return out;
    }

    /**
     * 快照恢复专用（P1 #6）：将已构建好的 session（携带原始 token/joinedAt）直接写入玩家表，
     * 不触发 onPlayerJoin 回调，恢复的 session 初始为离线状态。
     */
    public VKGamePlayerSession restorePlayerSession(VKGamePlayerSession session, int maxPlayersPerRoom) {
        if (session == null) {
            return null;
        }
        if (players.size() >= maxPlayersPerRoom) {
            return null;
        }
        VKGamePlayerSession prev = players.putIfAbsent(session.getPlayerId(), session);
        if (prev == null) {
            if (players.size() > maxPlayersPerRoom) {
                players.remove(session.getPlayerId(), session);
                return null;
            }
            emptySinceAt.set(-1L);
            return session;
        }
        return prev;
    }

    public VKGamePlayerSession leavePlayer(String playerId) {
        VKGamePlayerSession removed = players.remove(playerId);
        if (removed != null) {
            removed.setOnline(false);
            if (players.isEmpty()) {
                emptySinceAt.set(System.currentTimeMillis());
            }
            touch();
        }
        return removed;
    }

    public boolean offerCommand(VKGameCommand command, int capacity) {
        int next = queuedCommands.incrementAndGet();
        if (next > capacity) {
            queuedCommands.decrementAndGet();
            return false;
        }
        switch (command.getPriority()) {
            case HIGH -> highPriorityQueue.offer(command);
            case LOW -> lowPriorityQueue.offer(command);
            default -> normalPriorityQueue.offer(command);
        }
        touch();
        return true;
    }

    public List<VKGameCommand> drainCommands(int maxCount) {
        return drainCommands(maxCount, 1, 1, 1);
    }

    /**
     * 按权重做队列轮转，避免低优先级命令长期饥饿。
     */
    public List<VKGameCommand> drainCommands(int maxCount, int highWeight, int normalWeight, int lowWeight) {
        int limit = Math.max(1, maxCount);
        ArrayList<VKGameCommand> out = new ArrayList<>(Math.min(128, limit));

        int h = Math.max(1, highWeight);
        int n = Math.max(1, normalWeight);
        int l = Math.max(1, lowWeight);
        int hLeft = h;
        int nLeft = n;
        int lLeft = l;

        for (int i = 0; i < limit; ) {
            if (hLeft <= 0 && nLeft <= 0 && lLeft <= 0) {
                hLeft = h;
                nLeft = n;
                lLeft = l;
            }

            VKGameCommand cmd = null;
            if (hLeft > 0) {
                cmd = highPriorityQueue.poll();
                hLeft--;
            }
            if (cmd == null && nLeft > 0) {
                cmd = normalPriorityQueue.poll();
                nLeft--;
            }
            if (cmd == null && lLeft > 0) {
                cmd = lowPriorityQueue.poll();
                lLeft--;
            }

            if (cmd == null) {
                boolean empty = highPriorityQueue.peek() == null
                        && normalPriorityQueue.peek() == null
                        && lowPriorityQueue.peek() == null;
                if (empty) {
                    break;
                }
                // 本轮剩余权重槽对应的队列均为空，提前归零以触发下次迭代的轮次重置，
                // 避免因权重值较大而对空队列做大量无效 poll（最坏 O(h+n+l) 次浪费）。
                hLeft = 0;
                nLeft = 0;
                lLeft = 0;
                continue;
            }
            queuedCommands.decrementAndGet();
            out.add(cmd);
            i++;
        }
        return out;
    }

    public int queuedCommands() {
        return Math.max(0, queuedCommands.get());
    }

    public void touch() {
        lastActiveAt.set(System.currentTimeMillis());
    }

    public VKGameRoomState getState() {
        return state.get();
    }

    public boolean isDraining() {
        return state.get() == VKGameRoomState.DRAINING;
    }

    public boolean isClosed() {
        return state.get() == VKGameRoomState.CLOSED;
    }

    public boolean markDraining(String reason, long nowMs) {
        if (state.compareAndSet(VKGameRoomState.ACTIVE, VKGameRoomState.DRAINING)) {
            drainingSinceAt.set(nowMs);
            lifecycleReason.set(reason == null ? "" : reason);
            return true;
        }
        return false;
    }

    public boolean markClosed(String reason, long nowMs) {
        VKGameRoomState old = state.getAndSet(VKGameRoomState.CLOSED);
        if (old == VKGameRoomState.CLOSED) {
            return false;
        }
        if (old != VKGameRoomState.DRAINING) {
            drainingSinceAt.set(nowMs);
        }
        closedAt.set(nowMs);
        lifecycleReason.set(reason == null ? "" : reason);
        return true;
    }

    public long getDrainingSinceAt() {
        return drainingSinceAt.get();
    }

    public long getClosedAt() {
        return closedAt.get();
    }

    public String getLifecycleReason() {
        return lifecycleReason.get();
    }

    public long getEmptySinceAt() {
        return emptySinceAt.get();
    }

    public boolean isEmptyFor(long timeoutMs, long nowMs) {
        if (timeoutMs <= 0) {
            return false;
        }
        if (!players.isEmpty()) {
            return false;
        }
        long start = emptySinceAt.get();
        if (start <= 0L) {
            start = createdAt;
        }
        return nowMs - start > timeoutMs;
    }

    public boolean isIdle(long idleTimeoutMs, long nowMs) {
        if (idleTimeoutMs <= 0) {
            return false;
        }
        return nowMs - lastActiveAt.get() > idleTimeoutMs;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public long getLastSkippedTickNo() {
        return lastSkippedTickNo.get();
    }

    public void setLastSkippedTickNo(long tickNo) {
        lastSkippedTickNo.set(tickNo);
    }

    public long getLastProcessedTickNo() {
        return lastProcessedTickNo.get();
    }

    public void setLastProcessedTickNo(long tickNo) {
        lastProcessedTickNo.set(tickNo);
    }

    public int incrementAndGetConsecutiveErrors() {
        return consecutiveLogicErrors.incrementAndGet();
    }

    public void resetConsecutiveErrors() {
        consecutiveLogicErrors.set(0);
    }

    public long getLastTickedAtMs() {
        return lastTickedAtMs;
    }

    public void setLastTickedAtMs(long ms) {
        lastTickedAtMs = ms;
    }

    public long getLastLifecycleCheckAt() {
        return lastLifecycleCheckAt;
    }

    public void setLastLifecycleCheckAt(long ms) {
        lastLifecycleCheckAt = ms;
    }

    public long getLastSessionCleanupAt() {
        return lastSessionCleanupAt;
    }

    public void setLastSessionCleanupAt(long ms) {
        lastSessionCleanupAt = ms;
    }
}
