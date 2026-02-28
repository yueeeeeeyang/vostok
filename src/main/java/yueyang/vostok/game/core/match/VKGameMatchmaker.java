package yueyang.vostok.game.core.match;

import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.match.VKGameMatchRequest;
import yueyang.vostok.game.core.runtime.VKGameRuntimeMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 简单匹配器（单机内存版）。
 * 策略：按 gameType 分队列，优先基于 rating/region 匹配；超时后放宽约束强制成组。
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

        QueuedEntry(String ticketId, VKGameMatchRequest request, long enqueueAtMs) {
            this.ticketId = ticketId;
            this.request = request;
            this.enqueueAtMs = enqueueAtMs;
        }

        String playerKey() {
            return request.getGameType() + "::" + request.getPlayerId();
        }
    }

    private final Object lock = new Object();
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

    public String enqueue(VKGameMatchRequest request) {
        long now = System.currentTimeMillis();
        long enqueueAt = request.getEnqueueAtMs() > 0 ? request.getEnqueueAtMs() : now;

        synchronized (lock) {
            String playerKey = request.getGameType() + "::" + request.getPlayerId();
            String existing = ticketByPlayer.get(playerKey);
            if (existing != null) {
                return existing;
            }

            String ticketId = "mm-" + ticketSeq.getAndIncrement();
            QueuedEntry entry = new QueuedEntry(ticketId, request, enqueueAt);
            queuesByGameType.computeIfAbsent(request.getGameType(), k -> new ArrayDeque<>()).addLast(entry);
            entryByTicket.put(ticketId, entry);
            ticketByPlayer.put(playerKey, ticketId);
            metrics.onMatchEnqueued();
            return ticketId;
        }
    }

    public boolean cancel(String ticketId) {
        synchronized (lock) {
            QueuedEntry entry = entryByTicket.remove(ticketId);
            if (entry == null) {
                return false;
            }
            ArrayDeque<QueuedEntry> queue = queuesByGameType.get(entry.request.getGameType());
            if (queue != null) {
                queue.remove(entry);
                if (queue.isEmpty()) {
                    queuesByGameType.remove(entry.request.getGameType(), queue);
                }
            }
            ticketByPlayer.remove(entry.playerKey());
            metrics.onMatchCancelled();
            return true;
        }
    }

    public TicketSnapshot snapshot(String ticketId) {
        synchronized (lock) {
            QueuedEntry entry = entryByTicket.get(ticketId);
            if (entry == null) {
                return null;
            }
            return new TicketSnapshot(
                    entry.ticketId,
                    entry.request.getPlayerId(),
                    entry.request.getGameType(),
                    entry.enqueueAtMs
            );
        }
    }

    public int pendingCount(String gameType) {
        synchronized (lock) {
            if (gameType == null || gameType.isBlank()) {
                int total = 0;
                for (ArrayDeque<QueuedEntry> q : queuesByGameType.values()) {
                    total += q.size();
                }
                return total;
            }
            ArrayDeque<QueuedEntry> queue = queuesByGameType.get(gameType.trim());
            return queue == null ? 0 : queue.size();
        }
    }

    public List<MatchedGroup> pollMatchedGroups(long nowMs) {
        VKGameConfig cfg = configSupplier.get();
        if (!cfg.isMatchmakingEnabled()) {
            return List.of();
        }

        synchronized (lock) {
            int roomSize = Math.max(2, cfg.getMatchmakingRoomSize());
            if (queuesByGameType.isEmpty()) {
                return List.of();
            }

            ArrayList<MatchedGroup> groups = new ArrayList<>();
            for (var e : queuesByGameType.entrySet()) {
                String gameType = e.getKey();
                ArrayDeque<QueuedEntry> queue = e.getValue();
                while (queue.size() >= roomSize) {
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
                }
            }

            queuesByGameType.entrySet().removeIf(it -> it.getValue().isEmpty());
            return groups;
        }
    }

    public void requeue(MatchedGroup group, long nowMs) {
        if (group == null || group.candidates == null || group.candidates.isEmpty()) {
            return;
        }

        synchronized (lock) {
            ArrayDeque<QueuedEntry> queue = queuesByGameType.computeIfAbsent(group.gameType, k -> new ArrayDeque<>());
            for (MatchCandidate candidate : group.candidates) {
                QueuedEntry entry = new QueuedEntry(candidate.ticketId, candidate.request, nowMs);
                queue.addFirst(entry);
                entryByTicket.put(entry.ticketId, entry);
                ticketByPlayer.put(entry.playerKey(), entry.ticketId);
            }
        }
    }

    public void clear() {
        synchronized (lock) {
            queuesByGameType.clear();
            entryByTicket.clear();
            ticketByPlayer.clear();
        }
    }

    private List<QueuedEntry> pickGroup(ArrayDeque<QueuedEntry> queue,
                                        long nowMs,
                                        VKGameConfig cfg,
                                        int roomSize) {
        QueuedEntry base = queue.peekFirst();
        if (base == null) {
            return null;
        }

        int dynamicTolerance = resolveTolerance(base, nowMs, cfg);
        ArrayList<QueuedEntry> picked = new ArrayList<>(roomSize);
        picked.add(base);

        for (QueuedEntry entry : queue) {
            if (entry == base) {
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

        // 超时兜底：长时间等不到足够“接近”的对手时，放宽条件强制开局。
        if (cfg.getMatchmakingMaxWaitMs() > 0 && nowMs - base.enqueueAtMs >= cfg.getMatchmakingMaxWaitMs()) {
            picked.clear();
            Set<String> usedPlayers = new HashSet<>();
            for (QueuedEntry entry : queue) {
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

    private static boolean regionCompatible(String a, String b) {
        String ra = a == null ? "" : a.trim();
        String rb = b == null ? "" : b.trim();
        if (ra.isEmpty() || rb.isEmpty()) {
            return true;
        }
        return ra.equalsIgnoreCase(rb);
    }
}
