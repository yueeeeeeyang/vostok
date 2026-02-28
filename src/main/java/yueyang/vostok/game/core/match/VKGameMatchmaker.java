package yueyang.vostok.game.core.match;

import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.match.VKGameMatchRequest;
import yueyang.vostok.game.core.runtime.VKGameRuntimeMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 简单匹配器（单机内存版）。
 * 策略：按 gameType 分队列，优先基于 rating/region 匹配；超时后放宽约束强制成组。
 *
 * 并发模型：
 * - 每个 gameType 独立一把锁（{@code gameTypeLocks}），不同 gameType 的操作可完全并发。
 * - cancel() 改为标记删除（{@code cancelled=true}），避免 ArrayDeque.remove() 的 O(n) 开销。
 * - clear() 使用全局锁独占所有 gameType 锁，确保清理完整性。
 */
public final class VKGameMatchmaker {
    public static final class MatchCandidate {
        public final String ticketId;
        public final VKGameMatchRequest request;

        public MatchCandidate(String ticketId, VKGameMatchRequest request) {
            this.ticketId = ticketId;
            this.request = request;
        }
    }

    public static final class MatchedGroup {
        public final String gameType;
        public final List<MatchCandidate> candidates;

        public MatchedGroup(String gameType, List<MatchCandidate> candidates) {
            this.gameType = gameType;
            this.candidates = candidates;
        }
    }

    public static final class TicketSnapshot {
        public final String ticketId;
        public final String playerId;
        public final String gameType;
        public final long enqueueAtMs;

        public TicketSnapshot(String ticketId, String playerId, String gameType, long enqueueAtMs) {
            this.ticketId = ticketId;
            this.playerId = playerId;
            this.gameType = gameType;
            this.enqueueAtMs = enqueueAtMs;
        }
    }

    private static final class QueuedEntry {
        final String ticketId;
        final VKGameMatchRequest request;
        final long enqueueAtMs;
        // 标记删除：cancel() 时设为 true，避免 O(n) 的 ArrayDeque.remove()。
        // 迭代器在 pick/poll 阶段负责跳过并清理已取消的条目。
        volatile boolean cancelled;

        QueuedEntry(String ticketId, VKGameMatchRequest request, long enqueueAtMs) {
            this.ticketId = ticketId;
            this.request = request;
            this.enqueueAtMs = enqueueAtMs;
        }

        String playerKey() {
            return request.getGameType() + "::" + request.getPlayerId();
        }
    }

    // 按 gameType 分桶加锁，高并发多游戏类型场景下不再串行（P2 #11）
    private final ConcurrentHashMap<String, Object> gameTypeLocks = new ConcurrentHashMap<>();
    // 全局序列化 clear() 操作，防止与 per-type 锁交叉
    private final Object globalClearLock = new Object();

    private final AtomicLong ticketSeq = new AtomicLong(1L);
    private final ConcurrentHashMap<String, ArrayDeque<QueuedEntry>> queuesByGameType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QueuedEntry> entryByTicket = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> ticketByPlayer = new ConcurrentHashMap<>();

    private final Supplier<VKGameConfig> configSupplier;
    private final VKGameRuntimeMetrics metrics;

    public VKGameMatchmaker(Supplier<VKGameConfig> configSupplier, VKGameRuntimeMetrics metrics) {
        this.configSupplier = configSupplier;
        this.metrics = metrics;
    }

    /**
     * 获取或创建 gameType 对应的分桶锁。
     */
    private Object lockFor(String gameType) {
        return gameTypeLocks.computeIfAbsent(gameType, k -> new Object());
    }

    public String enqueue(VKGameMatchRequest request) {
        long now = System.currentTimeMillis();
        long enqueueAt = request.getEnqueueAtMs() > 0 ? request.getEnqueueAtMs() : now;
        String gameType = request.getGameType();

        synchronized (lockFor(gameType)) {
            String playerKey = request.getGameType() + "::" + request.getPlayerId();
            String existing = ticketByPlayer.get(playerKey);
            if (existing != null) {
                return existing;
            }

            String ticketId = "mm-" + ticketSeq.getAndIncrement();
            QueuedEntry entry = new QueuedEntry(ticketId, request, enqueueAt);
            queuesByGameType.computeIfAbsent(gameType, k -> new ArrayDeque<>()).addLast(entry);
            entryByTicket.put(ticketId, entry);
            ticketByPlayer.put(playerKey, ticketId);
            metrics.onMatchEnqueued();
            return ticketId;
        }
    }

    /**
     * 取消匹配：O(1) 标记删除，不再做 O(n) 的 ArrayDeque.remove()（P2 #12）。
     * 已取消的条目在后续 pollMatchedGroups/pickGroup 中被惰性清理。
     */
    public boolean cancel(String ticketId) {
        QueuedEntry entry = entryByTicket.remove(ticketId);
        if (entry == null) {
            return false;
        }
        // 先标记为已取消，再清理索引；即使并发 pollMatchedGroups 也能正确跳过
        entry.cancelled = true;
        ticketByPlayer.remove(entry.playerKey());
        metrics.onMatchCancelled();
        return true;
    }

    public TicketSnapshot snapshot(String ticketId) {
        // entryByTicket 是 ConcurrentHashMap，直接读取即可
        QueuedEntry entry = entryByTicket.get(ticketId);
        if (entry == null || entry.cancelled) {
            return null;
        }
        return new TicketSnapshot(
                entry.ticketId,
                entry.request.getPlayerId(),
                entry.request.getGameType(),
                entry.enqueueAtMs
        );
    }

    public int pendingCount(String gameType) {
        if (gameType == null || gameType.isBlank()) {
            // 全量统计：遍历所有 gameType 的队列，跳过已取消条目
            int total = 0;
            for (var e : queuesByGameType.entrySet()) {
                synchronized (lockFor(e.getKey())) {
                    for (QueuedEntry entry : e.getValue()) {
                        if (!entry.cancelled) {
                            total++;
                        }
                    }
                }
            }
            return total;
        }
        String gt = gameType.trim();
        synchronized (lockFor(gt)) {
            ArrayDeque<QueuedEntry> queue = queuesByGameType.get(gt);
            if (queue == null) {
                return 0;
            }
            int count = 0;
            for (QueuedEntry entry : queue) {
                if (!entry.cancelled) {
                    count++;
                }
            }
            return count;
        }
    }

    public List<MatchedGroup> pollMatchedGroups(long nowMs) {
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isMatchmakingEnabled()) {
            return List.of();
        }

        ArrayList<MatchedGroup> groups = new ArrayList<>();
        int roomSize = Math.max(2, cfg.getMatchmakingRoomSize());

        // 按 gameType 逐个加锁处理，不同 gameType 之间完全并发
        for (var e : queuesByGameType.entrySet()) {
            String gameType = e.getKey();
            synchronized (lockFor(gameType)) {
                ArrayDeque<QueuedEntry> queue = e.getValue();
                // 惰性清理队头的已取消条目
                drainCancelledHead(queue);

                while (activeSize(queue) >= roomSize) {
                    List<QueuedEntry> picked = pickGroup(queue, nowMs, cfg, roomSize);
                    if (picked == null || picked.size() < roomSize) {
                        break;
                    }
                    ArrayList<MatchCandidate> candidates = new ArrayList<>(picked.size());
                    for (QueuedEntry item : picked) {
                        queue.remove(item);
                        entryByTicket.remove(item.ticketId);
                        ticketByPlayer.remove(item.playerKey());
                        candidates.add(new MatchCandidate(item.ticketId, item.request));
                    }
                    groups.add(new MatchedGroup(gameType, List.copyOf(candidates)));
                    drainCancelledHead(queue);
                }
                if (queue.isEmpty()) {
                    queuesByGameType.remove(gameType, queue);
                    gameTypeLocks.remove(gameType);
                }
            }
        }
        return groups;
    }

    /**
     * 将已超时/失败的成组候选重新入队。
     * 使用 addLast 保持 FIFO 公平性，先入队的玩家不会因 requeue 被置于队尾后方（P0 #4）。
     */
    public void requeue(MatchedGroup group, long nowMs) {
        if (group == null || group.candidates == null || group.candidates.isEmpty()) {
            return;
        }

        synchronized (lockFor(group.gameType)) {
            ArrayDeque<QueuedEntry> queue = queuesByGameType.computeIfAbsent(group.gameType, k -> new ArrayDeque<>());
            for (MatchCandidate candidate : group.candidates) {
                QueuedEntry entry = new QueuedEntry(candidate.ticketId, candidate.request, nowMs);
                // addLast 保持 FIFO：先到先匹配，不因 requeue 破坏公平性
                queue.addLast(entry);
                entryByTicket.put(entry.ticketId, entry);
                ticketByPlayer.put(entry.playerKey(), entry.ticketId);
            }
        }
    }

    public void clear() {
        // 全局清理：先锁定所有 gameType 锁，再清空数据结构
        synchronized (globalClearLock) {
            for (String gt : queuesByGameType.keySet()) {
                synchronized (lockFor(gt)) {
                    // 在锁内清空各队列
                }
            }
            queuesByGameType.clear();
            entryByTicket.clear();
            ticketByPlayer.clear();
            gameTypeLocks.clear();
        }
    }

    /**
     * 从队头开始清理已取消的条目，减少后续迭代成本。
     * 必须在对应 gameType 锁内调用。
     */
    private static void drainCancelledHead(ArrayDeque<QueuedEntry> queue) {
        while (!queue.isEmpty() && queue.peekFirst().cancelled) {
            queue.pollFirst();
        }
    }

    /**
     * 计算队列中未取消的有效条目数量（需在锁内调用）。
     */
    private static int activeSize(ArrayDeque<QueuedEntry> queue) {
        int count = 0;
        for (QueuedEntry e : queue) {
            if (!e.cancelled) {
                count++;
            }
        }
        return count;
    }

    private List<QueuedEntry> pickGroup(ArrayDeque<QueuedEntry> queue,
                                        long nowMs,
                                        VKGameConfig cfg,
                                        int roomSize) {
        // 找第一个未取消的队头作为基准
        QueuedEntry base = null;
        for (QueuedEntry e : queue) {
            if (!e.cancelled) {
                base = e;
                break;
            }
        }
        if (base == null) {
            return null;
        }

        int dynamicTolerance = resolveTolerance(base, nowMs, cfg);
        ArrayList<QueuedEntry> picked = new ArrayList<>(roomSize);
        picked.add(base);

        for (QueuedEntry entry : queue) {
            if (entry == base || entry.cancelled) {
                continue;
            }
            if (!regionCompatible(base.request.getRegion(), entry.request.getRegion())) {
                continue;
            }
            int diff = Math.abs(base.request.getRating() - entry.request.getRating());
            if (diff > dynamicTolerance) {
                continue;
            }
            picked.add(entry);
            if (picked.size() >= roomSize) {
                return picked;
            }
        }

        // 超时兜底：长时间等不到足够"接近"的对手时，放宽条件强制开局。
        if (cfg.getMatchmakingMaxWaitMs() > 0 && nowMs - base.enqueueAtMs >= cfg.getMatchmakingMaxWaitMs()) {
            picked.clear();
            Set<String> usedPlayers = new HashSet<>();
            for (QueuedEntry entry : queue) {
                if (entry.cancelled) {
                    continue;
                }
                String playerId = entry.request.getPlayerId();
                if (!usedPlayers.add(playerId)) {
                    continue;
                }
                picked.add(entry);
                if (picked.size() >= roomSize) {
                    return picked;
                }
            }
        }
        return null;
    }

    private static int resolveTolerance(QueuedEntry base, long nowMs, VKGameConfig cfg) {
        long waitedMs = Math.max(0L, nowMs - base.enqueueAtMs);
        int grow = (int) ((waitedMs / 1000L) * Math.max(0, cfg.getMatchmakingRatingExpandPerSecond()));
        int baseTol = base.request.getRatingTolerance() > 0
                ? base.request.getRatingTolerance()
                : cfg.getMatchmakingBaseRatingTolerance();
        return Math.max(0, baseTol + grow);
    }

    /**
     * region 兼容性判断（P0 #4）：
     * 原逻辑 ra.isEmpty() || rb.isEmpty() → true 导致空 region 与任意非空 region 都"兼容"，语义错误。
     * 修正：仅当两者均为空（均未指定 region）时才视为兼容；一空一非空视为不兼容。
     */
    private static boolean regionCompatible(String a, String b) {
        String ra = a == null ? "" : a.trim();
        String rb = b == null ? "" : b.trim();
        if (ra.isEmpty() && rb.isEmpty()) {
            return true;
        }
        if (ra.isEmpty() || rb.isEmpty()) {
            // 一方未指定 region，不能与已指定 region 的玩家混组
            return false;
        }
        return ra.equalsIgnoreCase(rb);
    }
}
