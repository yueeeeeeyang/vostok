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

    public VKGameRoom(String roomId, String gameType) {
        this.roomId = roomId;
        this.gameType = gameType;
        this.createdAt = System.currentTimeMillis();
    }

    public String getRoomId() {
        return roomId;
    }

    public String getGameType() {
        return gameType;
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
        if (players.size() >= maxPlayersPerRoom) {
            return null;
        }
        VKGamePlayerSession created = new VKGamePlayerSession(playerId);
        VKGamePlayerSession prev = players.putIfAbsent(playerId, created);
        VKGamePlayerSession out = prev == null ? created : prev;
        out.setOnline(true);
        if (players.size() > 0) {
            emptySinceAt.set(-1L);
        }
        touch();
        return out;
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
}
