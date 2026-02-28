package yueyang.vostok.game.core.message;

import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.message.VKGameMessage;
import yueyang.vostok.game.message.VKGameMessageNotifier;
import yueyang.vostok.game.message.VKGameMessagePublishCommand;
import yueyang.vostok.game.message.VKGameMessageScope;
import yueyang.vostok.game.message.VKGameMessageType;
import yueyang.vostok.game.core.runtime.VKGameRuntimeMetrics;
import yueyang.vostok.game.exception.VKGameErrorCode;
import yueyang.vostok.game.exception.VKGameException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 统一消息中心（玩家消息 + 系统消息）。
 * 能力：
 * - 统一发布（按 type/scope）
 * - Push + Poll 兜底
 * - ACK（仅对 requireAck 类型生效）
 * - 指数退避重推与过期清理
 */
public final class VKGameMessageCenter {
    private static final String GLOBAL_SCOPE_ID = "global";
    private static final String LOBBY_SCOPE_ID = "lobby";

    private static final class MessageState {
        final String streamKey;
        final VKGameMessage message;
        final long expireAtMs;
        final Set<String> targetPlayers;
        final Set<String> pendingAckPlayers;
        final Set<String> deliveredPlayers;
        final ConcurrentHashMap<String, Integer> pushAttemptsByPlayer;
        final ConcurrentHashMap<String, Long> nextPushAtByPlayer;

        MessageState(String streamKey, VKGameMessage message, long expireAtMs, Set<String> targetPlayers) {
            this.streamKey = streamKey;
            this.message = message;
            this.expireAtMs = expireAtMs;
            this.targetPlayers = targetPlayers;
            this.pendingAckPlayers = ConcurrentHashMap.newKeySet();
            this.deliveredPlayers = ConcurrentHashMap.newKeySet();
            this.pushAttemptsByPlayer = new ConcurrentHashMap<>();
            this.nextPushAtByPlayer = new ConcurrentHashMap<>();
        }
    }

    private final Supplier<VKGameConfig> configSupplier;
    private final VKGameRuntimeMetrics metrics;
    private final Supplier<Set<String>> onlinePlayersSupplier;
    private final Function<String, Set<String>> roomPlayersResolver;
    private final BiPredicate<String, String> roomPlayerChecker;
    private final BiConsumer<String, Throwable> logger;

    private final ConcurrentHashMap<String, AtomicLong> seqByStream = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<MessageState>> streamBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MessageState> stateByMessageId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKGameMessageNotifier> notifierByPlayer = new ConcurrentHashMap<>();
    private final AtomicLong messageIdSeq = new AtomicLong(1L);
    private final AtomicLong eventSeq = new AtomicLong(1L);

    public VKGameMessageCenter(Supplier<VKGameConfig> configSupplier,
                               VKGameRuntimeMetrics metrics,
                               Supplier<Set<String>> onlinePlayersSupplier,
                               Function<String, Set<String>> roomPlayersResolver,
                               BiPredicate<String, String> roomPlayerChecker,
                               BiConsumer<String, Throwable> logger) {
        this.configSupplier = configSupplier;
        this.metrics = metrics;
        this.onlinePlayersSupplier = onlinePlayersSupplier;
        this.roomPlayersResolver = roomPlayersResolver;
        this.roomPlayerChecker = roomPlayerChecker;
        this.logger = logger;
    }

    public void bindNotifier(String playerId, VKGameMessageNotifier notifier) {
        String pid = requireName(playerId, "Message playerId is blank");
        if (notifier == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "VKGameMessageNotifier is null");
        }
        notifierByPlayer.put(pid, notifier);
    }

    public void unbindNotifier(String playerId) {
        String pid = requireName(playerId, "Message playerId is blank");
        notifierByPlayer.remove(pid);
    }

    public List<VKGameMessage> publishMessages(List<VKGameMessagePublishCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        VKGameConfig cfg = configSupplier.get();
        int capacity = Math.max(10, cfg.getMessageStreamCapacity());

        ArrayList<VKGameMessage> out = new ArrayList<>(commands.size());
        for (VKGameMessagePublishCommand cmd : commands) {
            if (cmd == null) {
                continue;
            }
            VKGameMessageType type = requireType(cmd.getType());
            VKGameMessageScope scope = requireScope(cmd.getScope());
            String scopeId = normalizeScopeId(scope, cmd.getScopeId());
            String senderId = normalizeSender(type, cmd.getSenderId());
            String content = normalizeContent(cmd.getContent());

            long expireAtMs = resolveExpireAt(now, cmd.getExpireAtMs(), cfg.getMessageDefaultTtlMs());
            String streamKey = streamKey(scope, scopeId);
            long seq = seqByStream.computeIfAbsent(streamKey, k -> new AtomicLong(0L)).incrementAndGet();

            String messageId = "msg-" + messageIdSeq.getAndIncrement();
            String eventId = "msg-evt-" + eventSeq.getAndIncrement();
            VKGameMessage message = new VKGameMessage(
                    messageId,
                    seq,
                    type,
                    scope,
                    scopeId,
                    senderId,
                    cmd.getTitle() == null ? "" : cmd.getTitle(),
                    content,
                    cmd.getPayload(),
                    now,
                    expireAtMs,
                    eventId,
                    1L
            );

            Set<String> targets = resolveTargets(scope, scopeId);
            MessageState state = new MessageState(streamKey, message, expireAtMs, Set.copyOf(targets));
            if (type.isRequireAck()) {
                state.pendingAckPlayers.addAll(targets);
            }
            for (String playerId : targets) {
                state.nextPushAtByPlayer.put(playerId, now);
            }

            stateByMessageId.put(messageId, state);
            ConcurrentLinkedDeque<MessageState> deque = streamBuffers.computeIfAbsent(streamKey, k -> new ConcurrentLinkedDeque<>());
            deque.addLast(state);
            while (deque.size() > capacity) {
                MessageState removed = deque.pollFirst();
                if (removed == null) {
                    break;
                }
                removeState(removed, true);
            }

            metrics.onMessagePublished();
            out.add(message);
            tryPushForState(state, now);
        }

        return List.copyOf(out);
    }

    public List<VKGameMessage> pollMessages(String playerId,
                                            VKGameMessageScope scope,
                                            String scopeId,
                                            long fromSeq,
                                            int limit,
                                            List<VKGameMessageType> types) {
        String pid = requireName(playerId, "Message playerId is blank");
        VKGameMessageScope resolvedScope = requireScope(scope);
        String resolvedScopeId = normalizeScopeId(resolvedScope, scopeId);
        int fetch = Math.max(1, Math.min(1000, limit));

        Set<VKGameMessageType> typeFilter = null;
        if (types != null && !types.isEmpty()) {
            typeFilter = Set.copyOf(types);
        }

        String streamKey = streamKey(resolvedScope, resolvedScopeId);
        ConcurrentLinkedDeque<MessageState> deque = streamBuffers.get(streamKey);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        ArrayList<VKGameMessage> out = new ArrayList<>(Math.min(32, fetch));
        for (MessageState state : deque) {
            if (state == null || state.message == null) {
                continue;
            }
            if (now > state.expireAtMs) {
                continue;
            }
            VKGameMessage message = state.message;
            // 使用无符号比较应对 AtomicLong 溢出场景：seq 在 Long.MAX_VALUE 后回绕至负数，
            // 有符号比较 (seq <= fromSeq) 会把新产生的大序列号错误地视为"旧消息"而跳过。
            if (Long.compareUnsigned(message.getSeq(), fromSeq) <= 0) {
                continue;
            }
            if (!canAccess(pid, message)) {
                continue;
            }
            if (typeFilter != null && !typeFilter.contains(message.getType())) {
                continue;
            }
            out.add(message);
            if (out.size() >= fetch) {
                break;
            }
        }
        return List.copyOf(out);
    }

    public int ackMessages(String playerId, List<String> messageIds) {
        String pid = requireName(playerId, "Message playerId is blank");
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }

        int acked = 0;
        for (String messageId : messageIds) {
            if (messageId == null || messageId.isBlank()) {
                continue;
            }
            MessageState state = stateByMessageId.get(messageId.trim());
            if (state == null || state.message == null) {
                continue;
            }
            if (!state.message.getType().isRequireAck()) {
                continue;
            }
            if (state.pendingAckPlayers.remove(pid)) {
                acked++;
                metrics.onMessageAcked();
            }
        }
        return acked;
    }

    public void onTick(long nowMs) {
        if (stateByMessageId.isEmpty()) {
            return;
        }
        for (MessageState state : stateByMessageId.values()) {
            if (state == null) {
                continue;
            }
            if (nowMs > state.expireAtMs) {
                removeState(state, true);
                continue;
            }
            tryPushForState(state, nowMs);
        }
    }

    public void onRoomClosed(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }
        for (MessageState state : stateByMessageId.values()) {
            if (state == null || state.message == null) {
                continue;
            }
            VKGameMessage message = state.message;
            if (message.getScope() != VKGameMessageScope.ROOM) {
                continue;
            }
            if (!roomId.equals(message.getScopeId())) {
                continue;
            }
            removeState(state, false);
        }
        streamBuffers.remove(streamKey(VKGameMessageScope.ROOM, roomId));
    }

    public void clear() {
        seqByStream.clear();
        streamBuffers.clear();
        stateByMessageId.clear();
        notifierByPlayer.clear();
        messageIdSeq.set(1L);
        eventSeq.set(1L);
    }

    /**
     * 重试策略：
     * - 非 ACK 消息：推送成功后不再重试；
     * - ACK 消息：直到收到 ACK 或超出最大尝试次数。
     */
    private void tryPushForState(MessageState state, long nowMs) {
        VKGameMessage message = state.message;
        if (message == null || state.targetPlayers.isEmpty()) {
            return;
        }
        VKGameConfig cfg = configSupplier.get();
        int maxAttempts = Math.max(1, cfg.getMessageRetryMaxAttempts());

        for (String playerId : state.targetPlayers) {
            if (playerId == null || playerId.isBlank()) {
                continue;
            }
            if (message.getType().isRequireAck()) {
                if (!state.pendingAckPlayers.contains(playerId)) {
                    continue;
                }
            } else if (state.deliveredPlayers.contains(playerId)) {
                continue;
            }

            long nextAt = state.nextPushAtByPlayer.getOrDefault(playerId, nowMs);
            if (nowMs < nextAt) {
                continue;
            }

            VKGameMessageNotifier notifier = notifierByPlayer.get(playerId);
            if (notifier == null) {
                long retry = Math.max(50L, cfg.getMessageRetryIntervalMs());
                state.nextPushAtByPlayer.put(playerId, nowMs + retry);
                continue;
            }

            int attempts = state.pushAttemptsByPlayer.getOrDefault(playerId, 0);
            if (attempts >= maxAttempts) {
                continue;
            }

            boolean success = false;
            try {
                notifier.onMessage(message);
                metrics.onMessagePushed();
                state.deliveredPlayers.add(playerId);
                success = true;
            } catch (Throwable t) {
                logger.accept("message_notify", t);
            } finally {
                // 仅在成功推送时计入 pushAttempts（P1 #9）：
                // 若把异常失败也计入 maxAttempts，最终会因失败次数耗尽而永远放弃推送，
                // 即使消息从未被成功送达。失败时只更新下次重试时间，不消耗成功次数配额。
                if (success) {
                    int nextAttempts = attempts + 1;
                    state.pushAttemptsByPlayer.put(playerId, nextAttempts);
                    if (!message.getType().isRequireAck()) {
                        // 非 ACK 消息只要推送成功一次就停止重试。
                        state.pushAttemptsByPlayer.put(playerId, maxAttempts);
                    }
                }
                // 无论成功与否，均按退避策略安排下次尝试时间
                long nextDelay = nextBackoff(cfg, success ? attempts + 1 : attempts);
                state.nextPushAtByPlayer.put(playerId, nowMs + nextDelay);
            }
        }
    }

    private long nextBackoff(VKGameConfig cfg, int attempts) {
        long base = Math.max(0L, cfg.getMessageRetryIntervalMs());
        long cap = Math.max(base, cfg.getMessageRetryMaxIntervalMs());
        if (base <= 0L) {
            return 0L;
        }
        int shift = Math.max(0, Math.min(20, attempts - 1));
        long factor = 1L << shift;
        long delay;
        if (factor > Long.MAX_VALUE / base) {
            delay = Long.MAX_VALUE;
        } else {
            delay = base * factor;
        }
        return Math.min(cap, delay);
    }

    private void removeState(MessageState state, boolean expired) {
        if (state == null || state.message == null) {
            return;
        }
        String messageId = state.message.getMessageId();
        if (!stateByMessageId.remove(messageId, state)) {
            return;
        }
        ConcurrentLinkedDeque<MessageState> deque = streamBuffers.get(state.streamKey);
        if (deque != null) {
            deque.remove(state);
        }
        if (expired) {
            metrics.onMessageExpired();
        }
    }

    private boolean canAccess(String playerId, VKGameMessage message) {
        if (message == null) {
            return false;
        }
        return switch (message.getScope()) {
            case GLOBAL, LOBBY -> true;
            case PLAYER -> playerId.equals(message.getScopeId());
            case ROOM -> roomPlayerChecker.test(message.getScopeId(), playerId);
        };
    }

    private Set<String> resolveTargets(VKGameMessageScope scope, String scopeId) {
        return switch (scope) {
            case PLAYER -> Set.of(scopeId);
            case ROOM -> Set.copyOf(roomPlayersResolver.apply(scopeId));
            case LOBBY, GLOBAL -> Set.copyOf(onlinePlayersSupplier.get());
        };
    }

    private static long resolveExpireAt(long nowMs, long commandExpireAtMs, long defaultTtlMs) {
        long fallback = nowMs + Math.max(1000L, defaultTtlMs);
        if (commandExpireAtMs <= nowMs) {
            return fallback;
        }
        return commandExpireAtMs;
    }

    private static VKGameMessageType requireType(VKGameMessageType type) {
        if (type == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "Message type is null");
        }
        return type;
    }

    private static VKGameMessageScope requireScope(VKGameMessageScope scope) {
        if (scope == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "Message scope is null");
        }
        return scope;
    }

    private static String normalizeSender(VKGameMessageType type, String senderId) {
        if (type.isSystemMessage()) {
            return (senderId == null || senderId.isBlank()) ? "SYSTEM" : senderId.trim();
        }
        return requireName(senderId, "Player message senderId is blank");
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "Message content is blank");
        }
        return content;
    }

    private static String normalizeScopeId(VKGameMessageScope scope, String scopeId) {
        return switch (scope) {
            case GLOBAL -> GLOBAL_SCOPE_ID;
            case LOBBY -> (scopeId == null || scopeId.isBlank()) ? LOBBY_SCOPE_ID : scopeId.trim();
            case ROOM, PLAYER -> requireName(scopeId, "Message scopeId is blank");
        };
    }

    private static String streamKey(VKGameMessageScope scope, String scopeId) {
        return scope.name() + "::" + scopeId;
    }

    private static String requireName(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, message);
        }
        return value.trim();
    }
}
