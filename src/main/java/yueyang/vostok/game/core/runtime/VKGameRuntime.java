package yueyang.vostok.game.core.runtime;

import yueyang.vostok.Vostok;
import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.VKGameConfig;
import yueyang.vostok.game.VKGameLogic;
import yueyang.vostok.game.message.VKGameMessage;
import yueyang.vostok.game.message.VKGameMessageNotifier;
import yueyang.vostok.game.message.VKGameMessagePublishCommand;
import yueyang.vostok.game.message.VKGameMessageScope;
import yueyang.vostok.game.message.VKGameMessageType;
import yueyang.vostok.game.match.VKGameMatchNotifier;
import yueyang.vostok.game.match.VKGameMatchRequest;
import yueyang.vostok.game.match.VKGameMatchResult;
import yueyang.vostok.game.match.VKGameMatchStatus;
import yueyang.vostok.game.VKGameMetrics;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;
import yueyang.vostok.game.shard.VKGameShardMetrics;
import yueyang.vostok.game.core.command.VKGameCommandGovernor;
import yueyang.vostok.game.core.lifecycle.VKGameLifecycleManager;
import yueyang.vostok.game.core.lifecycle.VKGameLifecycleReason;
import yueyang.vostok.game.core.match.VKGameMatchmaker;
import yueyang.vostok.game.core.message.VKGameMessageCenter;
import yueyang.vostok.game.core.persistence.VKGameRoomPersistence;
import yueyang.vostok.game.core.shard.VKGameShardBalancer;
import yueyang.vostok.game.exception.VKGameErrorCode;
import yueyang.vostok.game.exception.VKGameException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Game 模块运行时编排层。
 */
public final class VKGameRuntime {
    private static final Object LOCK = new Object();
    private static final VKGameRuntime INSTANCE = new VKGameRuntime();

    private enum SubmitOutcome {
        ACCEPTED,
        QUEUE_FULL,
        REJECTED
    }

    private final ConcurrentHashMap<String, VKGameLogic> logics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKGameRoom> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> roomShardMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> roomLastMigratedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKGameMatchResultState> matchResults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKGameMatchNotifier> matchNotifiers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKGameJoinTokenState> joinTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> joinTokenByTicket = new ConcurrentHashMap<>();
    private final AtomicLong roomSeq = new AtomicLong(1L);
    private final AtomicLong matchRoomSeq = new AtomicLong(1L);
    private final AtomicLong matchEventSeq = new AtomicLong(1L);
    // 全局帧序号：每次 tickOnce 自增，用于 Tick 跳过公平性排序
    private final AtomicLong globalTickSeq = new AtomicLong(0L);

    private final VKGameRuntimeMetrics runtimeMetrics = new VKGameRuntimeMetrics();
    private final AtomicReference<List<VKGameShardMetrics>> lastShardMetrics = new AtomicReference<>(List.of());

    private final VKGameShardBalancer shardBalancer;
    private final VKGameLifecycleManager lifecycleManager;
    private final VKGameCommandGovernor commandGovernor;
    private final VKGameMatchmaker matchmaker;
    private final VKGameRoomPersistence roomPersistence;
    private final VKGameMessageCenter messageCenter;

    private volatile VKGameConfig config = new VKGameConfig();
    private volatile ScheduledExecutorService ticker;
    private volatile ExecutorService tickWorkers;
    private volatile boolean initialized;

    private static final class VKGameMatchResultState {
        volatile VKGameMatchResult result;
        volatile long expireAtMs;
        volatile long lastPushAtMs;
        volatile long nextPushAtMs;
        volatile int pushAttempts;

        VKGameMatchResultState(VKGameMatchResult result, long expireAtMs) {
            this.result = result;
            this.expireAtMs = expireAtMs;
            this.lastPushAtMs = -1L;
            this.nextPushAtMs = System.currentTimeMillis();
            this.pushAttempts = 0;
        }
    }

    private static final class VKGameJoinTokenState {
        final String token;
        final String ticketId;
        final String roomId;
        final String playerId;
        final long expireAtMs;
        volatile boolean used;

        VKGameJoinTokenState(String token, String ticketId, String roomId, String playerId, long expireAtMs) {
            this.token = token;
            this.ticketId = ticketId;
            this.roomId = roomId;
            this.playerId = playerId;
            this.expireAtMs = expireAtMs;
            this.used = false;
        }
    }

    private VKGameRuntime() {
        this.shardBalancer = new VKGameShardBalancer(
                roomShardMap,
                roomLastMigratedAt,
                () -> config,
                runtimeMetrics,
                this::logMigration
        );
        this.lifecycleManager = new VKGameLifecycleManager(
                rooms,
                roomShardMap,
                roomLastMigratedAt,
                logics,
                () -> config,
                runtimeMetrics,
                this::notifyRoomDraining,
                this::handleRoomClosed
        );
        this.commandGovernor = new VKGameCommandGovernor(() -> config);
        this.matchmaker = new VKGameMatchmaker(() -> config, runtimeMetrics);
        this.roomPersistence = new VKGameRoomPersistence(() -> config, this::logRuntime);
        this.messageCenter = new VKGameMessageCenter(
                () -> config,
                runtimeMetrics,
                this::collectOnlinePlayers,
                this::roomPlayers,
                this::roomContainsPlayer,
                this::logRuntime
        );
    }

    public static VKGameRuntime getInstance() {
        return INSTANCE;
    }

    public void init(VKGameConfig cfg) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = normalize(cfg);
            initialized = true;
            roomPersistence.ensureReady();
            refreshExecutorsLocked();
        }
    }

    public void reinit(VKGameConfig cfg) {
        synchronized (LOCK) {
            config = normalize(cfg);
            if (!initialized) {
                initialized = true;
            }
            roomPersistence.ensureReady();
            refreshExecutorsLocked();
        }
    }

    public boolean started() {
        return initialized;
    }

    public VKGameConfig config() {
        return config.copy();
    }

    public List<VKGameShardMetrics> shardMetrics() {
        return lastShardMetrics.get();
    }

    public void close() {
        synchronized (LOCK) {
            stopExecutorsLocked();
            long now = System.currentTimeMillis();
            for (VKGameRoom room : new ArrayList<>(rooms.values())) {
                lifecycleManager.closeRoom(room, VKGameLifecycleReason.MANUAL.code(), now);
            }
            rooms.clear();
            roomShardMap.clear();
            roomLastMigratedAt.clear();
            logics.clear();
            commandGovernor.clear();
            matchmaker.clear();
            matchResults.clear();
            matchNotifiers.clear();
            joinTokens.clear();
            joinTokenByTicket.clear();
            roomPersistence.clear();
            messageCenter.clear();
            lastShardMetrics.set(List.of());
            runtimeMetrics.reset();
            roomSeq.set(1L);
            matchRoomSeq.set(1L);
            matchEventSeq.set(1L);
            initialized = false;
        }
    }

    public void registerLogic(String gameType, VKGameLogic logic) {
        ensureInit();
        String key = normalizeName(gameType, "Game type is blank");
        if (logic == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "VKGameLogic is null");
        }
        logics.put(key, logic);
        recoverRoomsByGameType(key);
    }

    public void unregisterLogic(String gameType) {
        ensureInit();
        String key = normalizeName(gameType, "Game type is blank");
        logics.remove(key);
    }

    public Set<String> logicNames() {
        ensureInit();
        return Set.copyOf(logics.keySet());
    }

    /**
     * 启动恢复：在 logic 注册后按 gameType 恢复快照房间。
     * 说明：恢复时还原基础房间状态（tick/玩家列表含 sessionToken+joinedAt/attributes 类型化值），
     * 不自动回放 WAL 命令，避免对业务逻辑产生不可控副作用。
     */
    private void recoverRoomsByGameType(String gameType) {
        if (!config.isRoomPersistenceEnabled() || !config.isRoomRecoveryEnabled()) {
            return;
        }
        List<VKGameRoomPersistence.RoomSnapshot> snapshots = roomPersistence.loadSnapshotsByGameType(gameType);
        if (snapshots.isEmpty()) {
            return;
        }

        for (VKGameRoomPersistence.RoomSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.roomId == null || snapshot.roomId.isBlank()) {
                continue;
            }
            if (rooms.containsKey(snapshot.roomId)) {
                continue;
            }

            VKGameRoom room;
            try {
                room = createRoom(snapshot.roomId, snapshot.gameType);
            } catch (VKGameException e) {
                if (e.getCode() == VKGameErrorCode.ROOM_EXISTS) {
                    continue;
                }
                logRuntime("room_recover", e);
                continue;
            }

            room.restoreTick(snapshot.tick);
            if (snapshot.attributes != null && !snapshot.attributes.isEmpty()) {
                room.attributes().putAll(snapshot.attributes);
            }
            // 恢复玩家 session（P1 #6）：携带原始 sessionToken + joinedAt，
            // 保证断线玩家重启后能凭原 token 重连，恢复的 session 初始状态为离线。
            if (snapshot.players != null && !snapshot.players.isEmpty()) {
                for (VKGameRoomPersistence.PlayerSessionInfo psi : snapshot.players) {
                    if (psi == null || psi.playerId == null || psi.playerId.isBlank()) {
                        continue;
                    }
                    VKGamePlayerSession session = new VKGamePlayerSession(
                            psi.playerId, psi.sessionToken, psi.joinedAt);
                    room.restorePlayerSession(session, config.getMaxPlayersPerRoom());
                }
            }
            room.touch();
        }
    }

    public VKGameRoom createRoom(String gameType) {
        String roomId = "room-" + roomSeq.getAndIncrement();
        return createRoom(roomId, gameType);
    }

    public VKGameRoom createRoom(String roomId, String gameType) {
        ensureInit();
        String rid = normalizeName(roomId, "Room id is blank");
        String gtype = normalizeName(gameType, "Game type is blank");
        VKGameLogic logic = logics.get(gtype);
        if (logic == null) {
            throw new VKGameException(VKGameErrorCode.LOGIC_NOT_FOUND, "Game logic not found: " + gtype);
        }
        if (rooms.size() >= config.getMaxRooms()) {
            throw new VKGameException(VKGameErrorCode.ROOM_LIMIT_EXCEEDED,
                    "Room limit exceeded: " + config.getMaxRooms());
        }

        VKGameRoom created = new VKGameRoom(rid, gtype);
        VKGameRoom prev = rooms.putIfAbsent(rid, created);
        if (prev != null) {
            throw new VKGameException(VKGameErrorCode.ROOM_EXISTS, "Room already exists: " + rid);
        }

        shardBalancer.assignInitialShard(rid, Math.max(1, config.getTickWorkerThreads()));
        runtimeMetrics.onRoomCreated();
        safeInvoke(() -> logic.onRoomStart(created), "onRoomStart");
        return created;
    }

    public boolean removeRoom(String roomId) {
        ensureInit();
        String rid = normalizeName(roomId, "Room id is blank");
        VKGameRoom removed = rooms.get(rid);
        return lifecycleManager.closeRoom(removed, VKGameLifecycleReason.MANUAL.code(), System.currentTimeMillis());
    }

    public boolean drainRoom(String roomId, String reason) {
        ensureInit();
        VKGameRoom room = requireRoom(roomId);
        return lifecycleManager.markRoomDraining(room, reason, System.currentTimeMillis());
    }

    public VKGameRoom room(String roomId) {
        ensureInit();
        String rid = normalizeName(roomId, "Room id is blank");
        return rooms.get(rid);
    }

    public Set<String> roomIds() {
        ensureInit();
        return Set.copyOf(rooms.keySet());
    }

    public VKGamePlayerSession join(String roomId, String playerId) {
        ensureInit();
        VKGameRoom room = requireRoom(roomId);
        if (room.isDraining() || room.isClosed()) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR,
                    "Room is not joinable: " + room.getRoomId() + " state=" + room.getState());
        }

        String pid = normalizeName(playerId, "Player id is blank");
        VKGamePlayerSession existing = room.player(pid);
        if (existing != null) {
            if (!existing.isOnline() && config.isSessionHostingEnabled()) {
                throw new VKGameException(VKGameErrorCode.STATE_ERROR,
                        "Player is offline-hosted, use reconnect with session token");
            }
            existing.setOnline(true);
            room.touch();
            return existing;
        }

        VKGamePlayerSession joined = room.joinPlayer(pid, config.getMaxPlayersPerRoom());
        if (joined == null) {
            throw new VKGameException(VKGameErrorCode.PLAYER_LIMIT_EXCEEDED,
                    "Player limit exceeded: " + config.getMaxPlayersPerRoom());
        }

        runtimeMetrics.onPlayerJoined();
        VKGameLogic logic = logics.get(room.getGameType());
        if (logic != null) {
            safeInvoke(() -> logic.onPlayerJoin(room, joined), "onPlayerJoin");
        }
        return joined;
    }

    /**
     * 入房凭证闭环：
     * - token 绑定 roomId/playerId/ticketId；
     * - 单次使用，过期失效；
     * - 防止客户端伪造“直接入房”。
     */
    public VKGamePlayerSession joinWithToken(String roomId, String playerId, String joinToken) {
        ensureInit();
        if (!config.isMatchJoinRequiresToken()) {
            return join(roomId, playerId);
        }
        String rid = normalizeName(roomId, "Room id is blank");
        String pid = normalizeName(playerId, "Player id is blank");
        String token = normalizeName(joinToken, "joinToken is blank");

        VKGameJoinTokenState state = joinTokens.get(token);
        if (state == null) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Join token invalid");
        }
        if (state.used) {
            joinTokens.remove(token, state);
            joinTokenByTicket.remove(state.ticketId, token);
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Join token already used");
        }
        long now = System.currentTimeMillis();
        if (now > state.expireAtMs) {
            joinTokens.remove(token, state);
            joinTokenByTicket.remove(state.ticketId, token);
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Join token expired");
        }
        if (!rid.equals(state.roomId) || !pid.equals(state.playerId)) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Join token not matched with room/player");
        }

        VKGameMatchResultState matchState = loadAliveMatchResultState(state.ticketId, now);
        if (matchState == null || matchState.result == null) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Match result expired");
        }
        VKGameMatchStatus status = matchState.result.getStatus();
        if (status != VKGameMatchStatus.FOUND && status != VKGameMatchStatus.ACKED) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Match state not joinable: " + status);
        }

        VKGamePlayerSession session = join(rid, pid);
        state.used = true;
        joinTokens.remove(token, state);
        joinTokenByTicket.remove(state.ticketId, token);
        return session;
    }

    public VKGamePlayerSession leave(String roomId, String playerId) {
        ensureInit();
        VKGameRoom room = requireRoom(roomId);
        String pid = normalizeName(playerId, "Player id is blank");
        VKGamePlayerSession left = room.leavePlayer(pid);
        if (left == null) {
            return null;
        }

        commandGovernor.removePlayer(room.getRoomId(), pid);
        runtimeMetrics.onPlayerLeft();
        VKGameLogic logic = logics.get(room.getGameType());
        if (logic != null) {
            safeInvoke(() -> logic.onPlayerLeave(room, left), "onPlayerLeave");
        }

        if (config.isRemoveEmptyRoomOnLastLeave() && room.getPlayerCount() == 0) {
            lifecycleManager.closeRoom(room, VKGameLifecycleReason.EMPTY_TIMEOUT.code(), System.currentTimeMillis());
        }
        return left;
    }

    /**
     * 临时断线：保留会话，不移出房间。
     */
    public VKGamePlayerSession disconnect(String roomId, String playerId) {
        ensureInit();
        if (!config.isSessionHostingEnabled()) {
            return leave(roomId, playerId);
        }

        VKGameRoom room = requireRoom(roomId);
        String pid = normalizeName(playerId, "Player id is blank");
        VKGamePlayerSession session = room.player(pid);
        if (session == null) {
            return null;
        }
        if (!session.isOnline()) {
            return session;
        }

        long now = System.currentTimeMillis();
        session.markDisconnected(now);
        room.touch();
        runtimeMetrics.onPlayerDisconnected();

        VKGameLogic logic = logics.get(room.getGameType());
        if (logic != null) {
            safeInvoke(() -> logic.onPlayerDisconnect(room, session), "onPlayerDisconnect");
        }
        return session;
    }

    /**
     * 断线重连：需携带先前分配的 sessionToken。
     */
    public VKGamePlayerSession reconnect(String roomId, String playerId, String sessionToken) {
        ensureInit();
        VKGameRoom room = requireRoom(roomId);
        String pid = normalizeName(playerId, "Player id is blank");
        VKGamePlayerSession session = room.player(pid);
        if (session == null) {
            return null;
        }
        if (!config.isSessionHostingEnabled()) {
            session.setOnline(true);
            return session;
        }
        if (session.isOnline()) {
            return session;
        }

        String token = normalizeName(sessionToken, "sessionToken is blank");
        if (!token.equals(session.getSessionToken())) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Session token mismatch");
        }

        long now = System.currentTimeMillis();
        long graceMs = config.getReconnectGraceMs();
        if (graceMs > 0 && session.getDisconnectedAt() > 0 && now - session.getDisconnectedAt() > graceMs) {
            VKGamePlayerSession expired = room.leavePlayer(pid);
            if (expired != null) {
                commandGovernor.removePlayer(room.getRoomId(), pid);
                runtimeMetrics.onPlayerSessionExpired();
                runtimeMetrics.onPlayerLeft();
                VKGameLogic logic = logics.get(room.getGameType());
                if (logic != null) {
                    safeInvoke(() -> logic.onPlayerLeave(room, expired), "onPlayerLeave");
                }
            }
            return null;
        }

        session.markReconnected(now);
        room.touch();
        runtimeMetrics.onPlayerReconnected();
        VKGameLogic logic = logics.get(room.getGameType());
        if (logic != null) {
            safeInvoke(() -> logic.onPlayerReconnect(room, session), "onPlayerReconnect");
        }
        return session;
    }

    public void submit(String roomId, VKGameCommand command) {
        SubmitOutcome outcome = trySubmitInternal(roomId, command);
        if (outcome == SubmitOutcome.QUEUE_FULL) {
            throw new VKGameException(VKGameErrorCode.QUEUE_FULL, "Room command queue is full");
        }
        if (outcome == SubmitOutcome.REJECTED) {
            throw new VKGameException(VKGameErrorCode.COMMAND_REJECTED, "Command rejected by governance rules");
        }
    }

    public boolean trySubmit(String roomId, VKGameCommand command) {
        return trySubmitInternal(roomId, command) == SubmitOutcome.ACCEPTED;
    }

    private SubmitOutcome trySubmitInternal(String roomId, VKGameCommand command) {
        ensureInit();
        VKGameRoom room = requireRoom(roomId);
        if (room.isDraining() || room.isClosed()) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR,
                    "Room is not writable: " + room.getRoomId() + " state=" + room.getState());
        }

        validateCommand(command);
        VKGameCommandGovernor.RejectReason reject = commandGovernor.validate(room, command, System.currentTimeMillis());
        if (reject != VKGameCommandGovernor.RejectReason.NONE) {
            runtimeMetrics.onCommandDropped();
            runtimeMetrics.onCommandRejected(reject);
            return SubmitOutcome.REJECTED;
        }

        boolean accepted = room.offerCommand(command, config.getRoomCommandQueueCapacity());
        if (accepted) {
            runtimeMetrics.onCommandReceived();
            return SubmitOutcome.ACCEPTED;
        }

        runtimeMetrics.onCommandDropped();
        return SubmitOutcome.QUEUE_FULL;
    }

    public String enqueueMatch(VKGameMatchRequest request) {
        ensureInit();
        if (!config.isMatchmakingEnabled()) {
            throw new VKGameException(VKGameErrorCode.MATCHMAKING_ERROR, "Matchmaking is disabled");
        }
        if (request == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "VKGameMatchRequest is null");
        }

        String playerId = normalizeName(request.getPlayerId(), "Match playerId is blank");
        String gameType = normalizeName(request.getGameType(), "Match gameType is blank");
        if (!logics.containsKey(gameType)) {
            throw new VKGameException(VKGameErrorCode.LOGIC_NOT_FOUND, "Game logic not found: " + gameType);
        }

        VKGameMatchRequest normalized = new VKGameMatchRequest(
                playerId,
                gameType,
                request.getRating(),
                request.getRegion(),
                request.getRatingTolerance(),
                request.getEnqueueAtMs()
        );
        return matchmaker.enqueue(normalized);
    }

    public boolean cancelMatch(String ticketId) {
        ensureInit();
        String tid = normalizeName(ticketId, "Match ticketId is blank");
        VKGameMatchmaker.TicketSnapshot snapshot = matchmaker.snapshot(tid);
        boolean cancelled = matchmaker.cancel(tid);
        if (cancelled && snapshot != null) {
            VKGameMatchResult result = VKGameMatchResult.cancelled(
                    snapshot.ticketId,
                    snapshot.playerId,
                    snapshot.gameType,
                    snapshot.enqueueAtMs,
                    System.currentTimeMillis()
            );
            matchResults.put(snapshot.ticketId, new VKGameMatchResultState(
                    result,
                    result.getUpdatedAtMs() + config.getMatchResultTtlMs()
            ));
            cleanupJoinTokenByTicket(snapshot.ticketId);
        }
        return cancelled;
    }

    public int pendingMatchCount(String gameType) {
        ensureInit();
        if (gameType == null || gameType.isBlank()) {
            return matchmaker.pendingCount("");
        }
        return matchmaker.pendingCount(gameType.trim());
    }

    public void bindMatchNotifier(String playerId, VKGameMatchNotifier notifier) {
        ensureInit();
        String pid = normalizeName(playerId, "Match playerId is blank");
        if (notifier == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "VKGameMatchNotifier is null");
        }
        matchNotifiers.put(pid, notifier);
    }

    public void unbindMatchNotifier(String playerId) {
        ensureInit();
        String pid = normalizeName(playerId, "Match playerId is blank");
        matchNotifiers.remove(pid);
    }

    /**
     * 轮询兜底接口：
     * - FOUND/ACKED/CANCELLED 读取本地结果表；
     * - 队列中则返回 PENDING；
     * - 查不到返回 null（可能已超时清理）。
     */
    public VKGameMatchResult pollMatchResult(String ticketId) {
        ensureInit();
        String tid = normalizeName(ticketId, "Match ticketId is blank");
        VKGameMatchResultState state = loadAliveMatchResultState(tid, System.currentTimeMillis());
        if (state != null) {
            return state.result;
        }

        VKGameMatchmaker.TicketSnapshot pending = matchmaker.snapshot(tid);
        if (pending == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        return VKGameMatchResult.pending(
                pending.ticketId,
                pending.playerId,
                pending.gameType,
                pending.enqueueAtMs,
                now
        );
    }

    /**
     * 客户端收到 MATCH_FOUND 后回 ACK，服务端将状态改为 ACKED。
     */
    public VKGameMatchResult ackMatchFound(String ticketId, String playerId) {
        ensureInit();
        String tid = normalizeName(ticketId, "Match ticketId is blank");
        String pid = normalizeName(playerId, "Match playerId is blank");

        VKGameMatchResultState state = loadAliveMatchResultState(tid, System.currentTimeMillis());
        if (state == null) {
            return null;
        }

        VKGameMatchResult current = state.result;
        if (!pid.equals(current.getPlayerId())) {
            throw new VKGameException(VKGameErrorCode.STATE_ERROR, "Match ticket does not belong to player: " + pid);
        }
        if (current.getStatus() == VKGameMatchStatus.ACKED) {
            return current;
        }
        if (current.getStatus() != VKGameMatchStatus.FOUND) {
            return current;
        }

        long now = System.currentTimeMillis();
        VKGameMatchResult acked = current.acked(now);
        state.result = acked;
        state.expireAtMs = now + config.getMatchResultTtlMs();
        runtimeMetrics.onMatchAcked();
        return acked;
    }

    public List<VKGameMessage> publishMessages(List<VKGameMessagePublishCommand> commands) {
        ensureInit();
        return messageCenter.publishMessages(commands);
    }

    public List<VKGameMessage> pollMessages(String playerId,
                                            VKGameMessageScope scope,
                                            String scopeId,
                                            long fromSeq,
                                            int limit,
                                            List<VKGameMessageType> types) {
        ensureInit();
        return messageCenter.pollMessages(playerId, scope, scopeId, fromSeq, limit, types);
    }

    public int ackMessages(String playerId, List<String> messageIds) {
        ensureInit();
        return messageCenter.ackMessages(playerId, messageIds);
    }

    public void bindMessageNotifier(String playerId, VKGameMessageNotifier notifier) {
        ensureInit();
        messageCenter.bindNotifier(playerId, notifier);
    }

    public void unbindMessageNotifier(String playerId) {
        ensureInit();
        messageCenter.unbindNotifier(playerId);
    }

    private VKGameMatchResultState loadAliveMatchResultState(String ticketId, long nowMs) {
        VKGameMatchResultState state = matchResults.get(ticketId);
        if (state == null) {
            return null;
        }
        if (nowMs <= state.expireAtMs) {
            return state;
        }
        if (matchResults.remove(ticketId, state)) {
            runtimeMetrics.onMatchResultExpired();
            cleanupJoinTokenByTicket(ticketId);
        }
        return null;
    }

    public void tickOnce() {
        ensureInit();
        tickOnceInternal();
    }

    public VKGameMetrics metrics() {
        return runtimeMetrics.snapshot(initialized, rooms.size(), logics.size(), matchmaker.pendingCount(""));
    }

    /**
     * 单帧调度入口：
     * - 全房间分片并发处理
     * - 命令优先级调度
     * - 会话过期回收
     * - 热点迁移与匹配撮合
     */
    private void tickOnceInternal() {
        if (!config.isEnabled()) {
            return;
        }

        ArrayList<VKGameRoom> snapshot = new ArrayList<>(rooms.values());
        final long tickStartNs = System.nanoTime();
        final long timeoutMs = config.getTickTimeoutMs();
        final long timeoutNs = timeoutMs <= 0L ? Long.MAX_VALUE : TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        final long deadlineNs = timeoutNs == Long.MAX_VALUE ? Long.MAX_VALUE : tickStartNs + timeoutNs;
        final long now = System.currentTimeMillis();
        // 全局帧序号自增，用于标记本帧被跳过的房间，保证下帧优先处理
        final long currentTickNo = globalTickSeq.incrementAndGet();

        if (!snapshot.isEmpty()) {
            final ExecutorService workers = tickWorkers;
            final int shardCount = Math.max(1, config.getTickWorkerThreads());
            final AtomicBoolean timedOut = new AtomicBoolean(false);
            final AtomicLong skippedRooms = new AtomicLong();

            @SuppressWarnings("unchecked")
            ArrayList<VKGameRoom>[] shards = (ArrayList<VKGameRoom>[]) new ArrayList<?>[shardCount];
            for (VKGameRoom room : snapshot) {
                int idx = shardBalancer.shardIndexForRoom(room.getRoomId(), shardCount);
                ArrayList<VKGameRoom> bucket = shards[idx];
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    shards[idx] = bucket;
                }
                bucket.add(room);
            }

            // 双关键字排序确保公平调度（P1 #8）：
            // 主键：lastSkippedTickNo 降序 —— 被跳过时间越新（全局帧序号越大）的房间越优先；
            // 次键：lastProcessedTickNo 升序 —— 主键相同时，上次实际处理帧序号越小（越久没被处理）的房间越优先；
            //        解决多房间同帧同时被跳过导致次序固化、后序房间长期饥饿的问题。
            // 两个关键字均使用 Long.compareUnsigned 以正确处理 globalTickSeq 溢出。
            for (ArrayList<VKGameRoom> bucket : shards) {
                if (bucket != null && bucket.size() > 1) {
                    bucket.sort((a, b) -> {
                        int cmp = Long.compareUnsigned(b.getLastSkippedTickNo(), a.getLastSkippedTickNo());
                        if (cmp != 0) return cmp;
                        // 次级：处理帧序号越小的优先（越久没被处理的优先）
                        return Long.compareUnsigned(a.getLastProcessedTickNo(), b.getLastProcessedTickNo());
                    });
                }
            }

            VKGameTickStats.TickShardStat[] shardStats = new VKGameTickStats.TickShardStat[shardCount];
            int taskCount = 0;
            for (int i = 0; i < shardCount; i++) {
                ArrayList<VKGameRoom> bucket = shards[i];
                int roomCount = bucket == null ? 0 : bucket.size();
                shardStats[i] = new VKGameTickStats.TickShardStat(i, roomCount);
                if (roomCount > 0) {
                    taskCount++;
                }
            }

            if (taskCount > 0) {
                if (workers == null || shardCount == 1 || taskCount == 1) {
                    for (int i = 0; i < shardCount; i++) {
                        ArrayList<VKGameRoom> bucket = shards[i];
                        if (bucket == null || bucket.isEmpty()) {
                            continue;
                        }
                        processShard(bucket, shardStats[i], now, deadlineNs, timedOut, skippedRooms, currentTickNo);
                    }
                } else {
                    CountDownLatch latch = new CountDownLatch(taskCount);
                    for (int i = 0; i < shardCount; i++) {
                        ArrayList<VKGameRoom> bucket = shards[i];
                        if (bucket == null || bucket.isEmpty()) {
                            continue;
                        }
                        VKGameTickStats.TickShardStat stat = shardStats[i];
                        Runnable task = () -> {
                            try {
                                processShard(bucket, stat, now, deadlineNs, timedOut, skippedRooms, currentTickNo);
                            } finally {
                                latch.countDown();
                            }
                        };
                        try {
                            workers.execute(task);
                        } catch (RejectedExecutionException e) {
                            task.run();
                        }
                    }
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            long tickCostMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - tickStartNs);
            if (timeoutMs > 0 && tickCostMs > timeoutMs) {
                timedOut.set(true);
            }
            if (timedOut.get()) {
                runtimeMetrics.onTickTimeout(skippedRooms.get());
            }
            runtimeMetrics.onTickDone(tickCostMs);

            lastShardMetrics.set(shardBalancer.buildShardMetrics(shardStats));
            shardBalancer.rebalanceHotRooms(shardStats, now);
        } else {
            runtimeMetrics.onTickDone(0L);
            lastShardMetrics.set(List.of());
        }

        processMatchmaking(now);
        retryMatchNotifications(now);
        cleanupMatchResults(now);
        cleanupExpiredJoinTokens(now);
        messageCenter.onTick(now);
    }

    private void processMatchmaking(long nowMs) {
        List<VKGameMatchmaker.MatchedGroup> groups = matchmaker.pollMatchedGroups(nowMs);
        if (groups.isEmpty()) {
            return;
        }
        for (VKGameMatchmaker.MatchedGroup group : groups) {
            String roomId = null;
            try {
                roomId = config.getMatchmakingRoomIdPrefix() + matchRoomSeq.getAndIncrement();
                VKGameRoom room = createRoom(roomId, group.gameType);
                for (VKGameMatchmaker.MatchCandidate candidate : group.candidates) {
                    publishMatchFound(candidate, room.getRoomId(), nowMs);
                    // token 入房模式下，等待客户端拿 token 后再入房。
                    if (!config.isMatchJoinRequiresToken()) {
                        join(room.getRoomId(), candidate.request.getPlayerId());
                    }
                }
                runtimeMetrics.onMatchSucceeded();
            } catch (RuntimeException e) {
                rollbackMatchFound(group);
                if (roomId != null && rooms.containsKey(roomId)) {
                    lifecycleManager.closeRoom(rooms.get(roomId), VKGameLifecycleReason.MANUAL.code(), nowMs);
                }
                matchmaker.requeue(group, nowMs);
                logRuntime("matchmaking", e);
            }
        }
    }

    /**
     * 记录 FOUND 结果并立即尝试一次推送。
     */
    private void publishMatchFound(VKGameMatchmaker.MatchCandidate candidate, String roomId, long nowMs) {
        String eventId = "evt-" + matchEventSeq.getAndIncrement();
        String joinToken = nextJoinToken();
        VKGameMatchResult result = VKGameMatchResult.found(
                candidate.ticketId,
                eventId,
                1L,
                candidate.request.getPlayerId(),
                candidate.request.getGameType(),
                roomId,
                joinToken,
                candidate.request.getEnqueueAtMs() > 0 ? candidate.request.getEnqueueAtMs() : nowMs,
                nowMs
        );
        VKGameMatchResultState state = new VKGameMatchResultState(result, nowMs + config.getMatchResultTtlMs());
        state.nextPushAtMs = nowMs;
        matchResults.put(candidate.ticketId, state);
        registerJoinToken(candidate.ticketId, roomId, candidate.request.getPlayerId(), joinToken, nowMs);
        tryNotifyMatchFound(state, nowMs);
    }

    private void rollbackMatchFound(VKGameMatchmaker.MatchedGroup group) {
        if (group == null || group.candidates == null) {
            return;
        }
        for (VKGameMatchmaker.MatchCandidate candidate : group.candidates) {
            if (candidate == null) {
                continue;
            }
            matchResults.remove(candidate.ticketId);
            cleanupJoinTokenByTicket(candidate.ticketId);
        }
    }

    /**
     * 推送策略：
     * - 首次建房后立即推一次；
     * - 若未 ACK，按指数退避重试，直到达到最大次数或结果过期。
     */
    private void retryMatchNotifications(long nowMs) {
        if (matchResults.isEmpty()) {
            return;
        }
        int maxAttempts = config.getMatchNotifyMaxAttempts();
        for (VKGameMatchResultState state : matchResults.values()) {
            VKGameMatchResult result = state.result;
            if (result == null || result.getStatus() != VKGameMatchStatus.FOUND) {
                continue;
            }
            if (maxAttempts > 0 && state.pushAttempts >= maxAttempts) {
                continue;
            }
            if (state.nextPushAtMs > nowMs) {
                continue;
            }
            tryNotifyMatchFound(state, nowMs);
        }
    }

    private void tryNotifyMatchFound(VKGameMatchResultState state, long nowMs) {
        VKGameMatchResult result = state.result;
        if (result == null || result.getStatus() != VKGameMatchStatus.FOUND) {
            return;
        }
        VKGameMatchNotifier notifier = matchNotifiers.get(result.getPlayerId());
        if (notifier == null) {
            state.nextPushAtMs = nowMs + Math.max(50L, config.getMatchNotifyRetryIntervalMs());
            return;
        }
        try {
            notifier.onMatchFound(result);
            state.lastPushAtMs = nowMs;
            state.pushAttempts++;
            state.nextPushAtMs = nowMs + nextNotifyBackoffMs(state.pushAttempts);
            runtimeMetrics.onMatchNotified();
        } catch (Throwable t) {
            state.lastPushAtMs = nowMs;
            state.pushAttempts++;
            state.nextPushAtMs = nowMs + nextNotifyBackoffMs(state.pushAttempts);
            logRuntime("match_notify", t);
        }
    }

    /**
     * 过期清理：防止匹配结果无限堆积。
     */
    private void cleanupMatchResults(long nowMs) {
        if (matchResults.isEmpty()) {
            return;
        }
        for (var it : matchResults.entrySet()) {
            VKGameMatchResultState state = it.getValue();
            if (state == null) {
                continue;
            }
            if (nowMs <= state.expireAtMs) {
                continue;
            }
            if (matchResults.remove(it.getKey(), state)) {
                runtimeMetrics.onMatchResultExpired();
                cleanupJoinTokenByTicket(it.getKey());
            }
        }
    }

    private long nextNotifyBackoffMs(int pushAttempts) {
        long base = Math.max(0L, config.getMatchNotifyRetryIntervalMs());
        long cap = Math.max(base, config.getMatchNotifyMaxRetryIntervalMs());
        if (base <= 0L) {
            return 0L;
        }
        int shift = Math.max(0, Math.min(20, pushAttempts - 1));
        long factor = 1L << shift;
        long candidate;
        if (factor > Long.MAX_VALUE / base) {
            candidate = Long.MAX_VALUE;
        } else {
            candidate = base * factor;
        }
        return Math.min(cap, candidate);
    }

    private void registerJoinToken(String ticketId, String roomId, String playerId, String token, long nowMs) {
        cleanupJoinTokenByTicket(ticketId);
        long expireAt = nowMs + config.getMatchJoinTokenTtlMs();
        VKGameJoinTokenState state = new VKGameJoinTokenState(token, ticketId, roomId, playerId, expireAt);
        joinTokens.put(token, state);
        joinTokenByTicket.put(ticketId, token);
    }

    private void cleanupJoinTokenByTicket(String ticketId) {
        String token = joinTokenByTicket.remove(ticketId);
        if (token != null) {
            joinTokens.remove(token);
        }
    }

    private void cleanupExpiredJoinTokens(long nowMs) {
        if (joinTokens.isEmpty()) {
            return;
        }
        for (var it : joinTokens.entrySet()) {
            VKGameJoinTokenState state = it.getValue();
            if (state == null || nowMs <= state.expireAtMs) {
                continue;
            }
            if (joinTokens.remove(it.getKey(), state)) {
                joinTokenByTicket.remove(state.ticketId, it.getKey());
            }
        }
    }

    private void cleanupJoinTokensByRoom(String roomId) {
        if (roomId == null || roomId.isBlank() || joinTokens.isEmpty()) {
            return;
        }
        for (var it : joinTokens.entrySet()) {
            VKGameJoinTokenState state = it.getValue();
            if (state == null || !roomId.equals(state.roomId)) {
                continue;
            }
            if (joinTokens.remove(it.getKey(), state)) {
                joinTokenByTicket.remove(state.ticketId, it.getKey());
            }
        }
    }

    private static String nextJoinToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 分片处理：触发超时后停止本分片后续房间，防止帧耗时继续恶化。
     */
    private void processShard(List<VKGameRoom> bucket,
                              VKGameTickStats.TickShardStat stat,
                              long now,
                              long deadlineNs,
                              AtomicBoolean timedOut,
                              AtomicLong skippedRooms,
                              long currentTickNo) {
        for (int i = 0; i < bucket.size(); i++) {
            if (deadlineExceeded(deadlineNs)) {
                timedOut.set(true);
                skippedRooms.addAndGet(bucket.size() - i);
                // 标记本帧被跳过的房间，下帧优先处理以保证公平性
                for (int j = i; j < bucket.size(); j++) {
                    bucket.get(j).setLastSkippedTickNo(currentTickNo);
                }
                break;
            }
            if (timedOut.get()) {
                skippedRooms.addAndGet(bucket.size() - i);
                // 标记本帧被跳过的房间，下帧优先处理以保证公平性
                for (int j = i; j < bucket.size(); j++) {
                    bucket.get(j).setLastSkippedTickNo(currentTickNo);
                }
                break;
            }

            VKGameRoom room = bucket.get(i);
            VKGameTickStats.RoomProcessResult result = processRoom(room, now, deadlineNs, timedOut);
            if (result.timeoutBeforeRoom) {
                skippedRooms.addAndGet(bucket.size() - i);
                // 标记本帧被跳过的房间（含当前房间），下帧优先处理以保证公平性
                for (int j = i; j < bucket.size(); j++) {
                    bucket.get(j).setLastSkippedTickNo(currentTickNo);
                }
                break;
            }
            if (result.timeoutDuringRoom) {
                timedOut.set(true);
            }
            if (result.skipped) {
                continue;
            }

            // 记录本帧实际处理过的房间，作为次级排序依据（同 lastSkippedTickNo 时，处理越久之前的优先）
            room.setLastProcessedTickNo(currentTickNo);
            stat.commandsProcessed += result.commandsProcessed;
            stat.costNanos += result.costNanos;
            stat.processedRooms++;
            if (result.hot) {
                stat.hotRooms.add(new VKGameTickStats.HotRoomStat(result.roomId, result.hotScore));
            }
        }
    }

    private VKGameTickStats.RoomProcessResult processRoom(VKGameRoom room,
                                                          long now,
                                                          long deadlineNs,
                                                          AtomicBoolean timedOut) {
        if (room == null) {
            return VKGameTickStats.RoomProcessResult.skipped();
        }
        if (deadlineExceeded(deadlineNs)) {
            timedOut.set(true);
            return VKGameTickStats.RoomProcessResult.timeoutBeforeRoom();
        }

        String roomId = room.getRoomId();
        if (rooms.get(roomId) != room) {
            return VKGameTickStats.RoomProcessResult.skipped();
        }

        lifecycleManager.applyLifecyclePolicy(room, now);
        if (room.isClosed() || rooms.get(roomId) != room) {
            return VKGameTickStats.RoomProcessResult.skipped();
        }

        VKGameLogic logic = logics.get(room.getGameType());
        if (logic == null) {
            return VKGameTickStats.RoomProcessResult.skipped();
        }

        cleanupHostedSessions(room, now, logic);

        long startNs = System.nanoTime();
        int processed = 0;
        boolean timeoutDuringRoom = false;
        boolean anyErrorThisTick = false;
        long frameTickNo = room.getCurrentTick() + 1L;

        List<VKGameCommand> drained = room.drainCommands(
                config.getMaxCommandsPerTick(),
                config.getHighPriorityWeight(),
                config.getNormalPriorityWeight(),
                config.getLowPriorityWeight()
        );
        for (VKGameCommand command : drained) {
            if (deadlineExceeded(deadlineNs)) {
                timeoutDuringRoom = true;
                break;
            }
            runtimeMetrics.onCommandProcessed();
            processed++;
            if (!safeInvoke(() -> logic.onCommand(room, command), "onCommand")) {
                anyErrorThisTick = true;
            }
            roomPersistence.appendWal(room, command, frameTickNo, now);
        }

        if (!timeoutDuringRoom && !deadlineExceeded(deadlineNs)) {
            long tickNo = room.nextTick();
            if (!safeInvoke(() -> logic.onTick(room, tickNo), "onTick")) {
                anyErrorThisTick = true;
            }
            roomPersistence.maybeSnapshot(room, tickNo);
        } else {
            timeoutDuringRoom = true;
        }
        // WAL 批量 flush（P1 #7）：本帧所有命令写入缓冲后统一 flush，而非每条命令单独系统调用
        if (processed > 0) {
            roomPersistence.flushWal(roomId);
        }

        // 连续逻辑异常隔离：超过阈值后将房间标记为 DRAINING，防止坏房间持续消耗 Tick 资源
        int threshold = config.getRoomMaxConsecutiveLogicErrors();
        if (anyErrorThisTick) {
            int errCount = room.incrementAndGetConsecutiveErrors();
            if (threshold > 0 && errCount >= threshold) {
                lifecycleManager.markRoomDraining(room, VKGameLifecycleReason.LOGIC_ERROR.code(), now);
                runtimeMetrics.onRoomClosedByLogicError();
            }
        } else {
            room.resetConsecutiveErrors();
        }

        long costNanos = System.nanoTime() - startNs;
        int queuedNow = room.queuedCommands();
        boolean hot = shardBalancer.isHotRoom(processed, queuedNow, costNanos);
        double hotScore = shardBalancer.hotRoomScore(processed, queuedNow, costNanos);

        if (timeoutDuringRoom) {
            timedOut.set(true);
        }

        return new VKGameTickStats.RoomProcessResult(
                roomId,
                processed,
                costNanos,
                hot,
                hotScore,
                false,
                timeoutDuringRoom,
                false
        );
    }

    /**
     * 托管会话清理：离线超出宽限期后自动踢出玩家。
     */
    private void cleanupHostedSessions(VKGameRoom room, long now, VKGameLogic logic) {
        if (!config.isSessionHostingEnabled()) {
            return;
        }
        long grace = config.getReconnectGraceMs();
        if (grace <= 0L) {
            return;
        }

        for (VKGamePlayerSession player : room.players()) {
            if (player == null || player.isOnline()) {
                continue;
            }
            long disconnectedAt = player.getDisconnectedAt();
            if (disconnectedAt <= 0L || now - disconnectedAt <= grace) {
                continue;
            }

            VKGamePlayerSession removed = room.leavePlayer(player.getPlayerId());
            if (removed == null) {
                continue;
            }
            commandGovernor.removePlayer(room.getRoomId(), removed.getPlayerId());
            runtimeMetrics.onPlayerSessionExpired();
            runtimeMetrics.onPlayerLeft();
            safeInvoke(() -> logic.onPlayerLeave(room, removed), "onPlayerLeave");
        }
    }

    /**
     * 收集当前在线玩家（用于 GLOBAL/LOBBY 广播推送目标）。
     */
    private Set<String> collectOnlinePlayers() {
        HashSet<String> out = new HashSet<>();
        for (VKGameRoom room : rooms.values()) {
            if (room == null || room.isClosed()) {
                continue;
            }
            for (VKGamePlayerSession player : room.players()) {
                if (player == null || !player.isOnline()) {
                    continue;
                }
                out.add(player.getPlayerId());
            }
        }
        return out;
    }

    private Set<String> roomPlayers(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return Set.of();
        }
        VKGameRoom room = rooms.get(roomId.trim());
        if (room == null || room.isClosed()) {
            return Set.of();
        }
        HashSet<String> out = new HashSet<>();
        for (VKGamePlayerSession player : room.players()) {
            if (player == null || player.getPlayerId() == null || player.getPlayerId().isBlank()) {
                continue;
            }
            out.add(player.getPlayerId());
        }
        return out;
    }

    private boolean roomContainsPlayer(String roomId, String playerId) {
        if (roomId == null || roomId.isBlank() || playerId == null || playerId.isBlank()) {
            return false;
        }
        VKGameRoom room = rooms.get(roomId.trim());
        if (room == null || room.isClosed()) {
            return false;
        }
        return room.player(playerId.trim()) != null;
    }

    private VKGameRoom requireRoom(String roomId) {
        String rid = normalizeName(roomId, "Room id is blank");
        VKGameRoom room = rooms.get(rid);
        if (room == null || room.isClosed()) {
            throw new VKGameException(VKGameErrorCode.ROOM_NOT_FOUND, "Room not found: " + rid);
        }
        return room;
    }

    private void validateCommand(VKGameCommand command) {
        if (command == null) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "VKGameCommand is null");
        }
        if (command.getPlayerId() == null || command.getPlayerId().isBlank()) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "Command playerId is blank");
        }
        if (command.getType() == null || command.getType().isBlank()) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, "Command type is blank");
        }
    }

    private VKGameConfig normalize(VKGameConfig cfg) {
        return (cfg == null ? new VKGameConfig() : cfg.copy());
    }

    private String normalizeName(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new VKGameException(VKGameErrorCode.INVALID_ARGUMENT, message);
        }
        return value.trim();
    }

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (!initialized) {
                config = normalize(config);
                initialized = true;
                roomPersistence.ensureReady();
                refreshExecutorsLocked();
            }
        }
    }

    private void refreshExecutorsLocked() {
        stopExecutorsLocked();
        if (!initialized) {
            return;
        }

        int workers = Math.max(1, config.getTickWorkerThreads());
        tickWorkers = Executors.newFixedThreadPool(workers, new GameWorkerThreadFactory());

        if (!config.isAutoStartTicker() || !config.isEnabled()) {
            return;
        }
        long intervalMs = Math.max(1L, 1000L / Math.max(1, config.getTickRate()));
        ScheduledExecutorService createdTicker = Executors.newSingleThreadScheduledExecutor(new GameTickerThreadFactory());
        createdTicker.scheduleAtFixedRate(() -> {
            try {
                tickOnceInternal();
            } catch (Throwable t) {
                logRuntime("tick", t);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        ticker = createdTicker;
    }

    private void stopExecutorsLocked() {
        ScheduledExecutorService oldTicker = ticker;
        ticker = null;
        if (oldTicker != null) {
            oldTicker.shutdown();
            awaitTermination(oldTicker, Math.max(0L, config.getShutdownWaitMs()));
        }

        ExecutorService oldWorkers = tickWorkers;
        tickWorkers = null;
        if (oldWorkers != null) {
            oldWorkers.shutdown();
            awaitTermination(oldWorkers, Math.max(0L, config.getShutdownWaitMs()));
        }
    }

    private static void awaitTermination(ExecutorService executor, long waitMs) {
        try {
            if (!executor.awaitTermination(waitMs, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private boolean deadlineExceeded(long deadlineNs) {
        return deadlineNs != Long.MAX_VALUE && System.nanoTime() > deadlineNs;
    }

    private void notifyRoomDraining(VKGameRoom room, String reason) {
        VKGameLogic logic = room == null ? null : logics.get(room.getGameType());
        if (logic != null) {
            safeInvoke(() -> logic.onRoomDraining(room, reason), "onRoomDraining");
        }
    }

    private void handleRoomClosed(VKGameRoom room) {
        if (room != null) {
            commandGovernor.removeRoom(room.getRoomId());
            roomPersistence.saveSnapshot(room);
            // 关闭 WAL writer，flush 缓冲并释放文件句柄（P1 #7）
            roomPersistence.closeWal(room.getRoomId());
            cleanupJoinTokensByRoom(room.getRoomId());
            messageCenter.onRoomClosed(room.getRoomId());
        }
        notifyRoomClose(room);
    }

    private void notifyRoomClose(VKGameRoom room) {
        VKGameLogic logic = room == null ? null : logics.get(room.getGameType());
        if (logic != null) {
            safeInvoke(() -> logic.onRoomClose(room), "onRoomClose");
        }
    }

    /**
     * 安全执行游戏逻辑回调：捕获所有异常并记录日志，返回 true 表示执行成功，false 表示捕获到异常。
     * 已有调用方可忽略返回值，向后兼容。
     */
    private boolean safeInvoke(Runnable action, String stage) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            logRuntime(stage, t);
            return false;
        }
    }

    private void logRuntime(String stage, Throwable t) {
        try {
            Vostok.Log.warn("Vostok.Game stage={} err={}", stage, t == null ? "-" : t.getMessage());
        } catch (Throwable ignore) {
            // no-op
        }
    }

    private void logMigration(String roomId, int fromShard, int toShard) {
        try {
            Vostok.Log.info("Vostok.Game migrate room={} from={} to={}", roomId, fromShard, toShard);
        } catch (Throwable ignore) {
            // no-op
        }
    }

    private static final class GameTickerThreadFactory implements ThreadFactory {
        private final AtomicLong seq = new AtomicLong(1L);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "vostok-game-ticker-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    private static final class GameWorkerThreadFactory implements ThreadFactory {
        private final AtomicLong seq = new AtomicLong(1L);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "vostok-game-worker-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
