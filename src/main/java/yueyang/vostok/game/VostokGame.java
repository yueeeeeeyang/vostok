package yueyang.vostok.game;

import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.core.runtime.VKGameRuntime;
import yueyang.vostok.game.frame.VKGameFrame;
import yueyang.vostok.game.frame.VKGameFrameNotifier;
import yueyang.vostok.game.match.VKGameMatchNotifier;
import yueyang.vostok.game.match.VKGameMatchRequest;
import yueyang.vostok.game.match.VKGameMatchResult;
import yueyang.vostok.game.message.VKGameMessage;
import yueyang.vostok.game.message.VKGameMessageNotifier;
import yueyang.vostok.game.message.VKGameMessagePublishCommand;
import yueyang.vostok.game.message.VKGameMessageScope;
import yueyang.vostok.game.message.VKGameMessageType;
import yueyang.vostok.game.room.VKGamePlayerSession;
import yueyang.vostok.game.room.VKGameRoom;
import yueyang.vostok.game.shard.VKGameShardMetrics;

import java.util.List;
import java.util.Set;

public class VostokGame {
    private static final VKGameRuntime RUNTIME = VKGameRuntime.getInstance();
    private static final VostokGame INSTANCE = new VostokGame();

    protected VostokGame() {
    }

    public static VostokGame init() {
        return init(new VKGameConfig());
    }

    public static VostokGame init(VKGameConfig config) {
        RUNTIME.init(config);
        return INSTANCE;
    }

    public static void reinit(VKGameConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKGameConfig config() {
        return RUNTIME.config();
    }

    public static VKGameMetrics metrics() {
        return RUNTIME.metrics();
    }

    public static List<VKGameShardMetrics> shardMetrics() {
        return RUNTIME.shardMetrics();
    }

    public static void tickOnce() {
        RUNTIME.tickOnce();
    }

    public static void close() {
        RUNTIME.close();
    }

    public VostokGame registerLogic(String gameType, VKGameLogic logic) {
        RUNTIME.registerLogic(gameType, logic);
        return this;
    }

    public VostokGame unregisterLogic(String gameType) {
        RUNTIME.unregisterLogic(gameType);
        return this;
    }

    public Set<String> logicNames() {
        return RUNTIME.logicNames();
    }

    public VKGameRoom createRoom(String roomId, String gameType) {
        return RUNTIME.createRoom(roomId, gameType, false);
    }

    public VKGameRoom createRoom(String gameType) {
        return RUNTIME.createRoom(gameType, false);
    }

    /**
     * 创建帧同步房间。
     *
     * <p>帧同步模式下，每帧 {@code onTick} 完成后，引擎将本帧所有玩家输入打包为
     * {@link VKGameFrame} 并通过 {@link VKGameFrameNotifier} 广播给应用层。
     * 广播通道与消息中心（{@code VKGameMessageNotifier}）完全隔离，
     * 避免业务消息队列对帧数据延迟的影响。
     *
     * <p>{@code onTick}/{@code onCommand} 回调仍然执行（与状态同步模式并不互斥），
     * 可用于服务端反作弊或权威状态维护。
     *
     * @param roomId          房间 ID
     * @param gameType        游戏类型（须已注册对应 logic）
     * @param frameSyncEnabled 是否启用帧同步模式
     */
    public VKGameRoom createRoom(String roomId, String gameType, boolean frameSyncEnabled) {
        return RUNTIME.createRoom(roomId, gameType, frameSyncEnabled);
    }

    /**
     * 创建帧同步房间（自动生成房间 ID）。
     *
     * @see #createRoom(String, String, boolean)
     */
    public VKGameRoom createRoom(String gameType, boolean frameSyncEnabled) {
        return RUNTIME.createRoom(gameType, frameSyncEnabled);
    }

    public boolean removeRoom(String roomId) {
        return RUNTIME.removeRoom(roomId);
    }

    public boolean drainRoom(String roomId, String reason) {
        return RUNTIME.drainRoom(roomId, reason);
    }

    public VKGamePlayerSession disconnect(String roomId, String playerId) {
        return RUNTIME.disconnect(roomId, playerId);
    }

    public VKGamePlayerSession reconnect(String roomId, String playerId, String sessionToken) {
        return RUNTIME.reconnect(roomId, playerId, sessionToken);
    }

    public VKGameRoom room(String roomId) {
        return RUNTIME.room(roomId);
    }

    public Set<String> roomIds() {
        return RUNTIME.roomIds();
    }

    public VKGamePlayerSession join(String roomId, String playerId) {
        return RUNTIME.join(roomId, playerId);
    }

    public VKGamePlayerSession joinWithToken(String roomId, String playerId, String joinToken) {
        return RUNTIME.joinWithToken(roomId, playerId, joinToken);
    }

    public VKGamePlayerSession leave(String roomId, String playerId) {
        return RUNTIME.leave(roomId, playerId);
    }

    public void submit(String roomId, VKGameCommand command) {
        RUNTIME.submit(roomId, command);
    }

    public void submit(String roomId, String playerId, String type, Object payload) {
        RUNTIME.submit(roomId, new VKGameCommand(playerId, type, payload));
    }

    public boolean trySubmit(String roomId, VKGameCommand command) {
        return RUNTIME.trySubmit(roomId, command);
    }

    public String enqueueMatch(VKGameMatchRequest request) {
        return RUNTIME.enqueueMatch(request);
    }

    public boolean cancelMatch(String ticketId) {
        return RUNTIME.cancelMatch(ticketId);
    }

    public int pendingMatchCount(String gameType) {
        return RUNTIME.pendingMatchCount(gameType);
    }

    public VostokGame bindMatchNotifier(String playerId, VKGameMatchNotifier notifier) {
        RUNTIME.bindMatchNotifier(playerId, notifier);
        return this;
    }

    public VostokGame unbindMatchNotifier(String playerId) {
        RUNTIME.unbindMatchNotifier(playerId);
        return this;
    }

    public VKGameMatchResult pollMatchResult(String ticketId) {
        return RUNTIME.pollMatchResult(ticketId);
    }

    public VKGameMatchResult ackMatchFound(String ticketId, String playerId) {
        return RUNTIME.ackMatchFound(ticketId, playerId);
    }

    public List<VKGameMessage> publishMessages(List<VKGameMessagePublishCommand> commands) {
        return RUNTIME.publishMessages(commands);
    }

    public List<VKGameMessage> pollMessages(String playerId,
                                            VKGameMessageScope scope,
                                            String scopeId,
                                            long fromSeq,
                                            int limit,
                                            List<VKGameMessageType> types) {
        return RUNTIME.pollMessages(playerId, scope, scopeId, fromSeq, limit, types);
    }

    public int ackMessages(String playerId, List<String> messageIds) {
        return RUNTIME.ackMessages(playerId, messageIds);
    }

    public VostokGame bindMessageNotifier(String playerId, VKGameMessageNotifier notifier) {
        RUNTIME.bindMessageNotifier(playerId, notifier);
        return this;
    }

    public VostokGame unbindMessageNotifier(String playerId) {
        RUNTIME.unbindMessageNotifier(playerId);
        return this;
    }

    /**
     * 绑定帧同步广播回调（房间级）。
     *
     * <p>每帧 {@code onTick} 完成后触发，应用层在回调中将帧数据经由网络协议广播给客户端。
     * 回调在 Tick Worker 线程中同步调用，实现应尽量快速返回（建议仅做序列化与异步发送）。
     *
     * @param roomId   帧同步房间 ID
     * @param notifier 帧回调
     * @return this（链式调用）
     * @throws yueyang.vostok.game.exception.VKGameException STATE_ERROR — 房间未启用帧同步模式
     */
    public VostokGame bindFrameNotifier(String roomId, VKGameFrameNotifier notifier) {
        RUNTIME.bindFrameNotifier(roomId, notifier);
        return this;
    }

    /**
     * 解绑帧同步广播回调。
     *
     * @param roomId 房间 ID
     * @return this（链式调用）
     */
    public VostokGame unbindFrameNotifier(String roomId) {
        RUNTIME.unbindFrameNotifier(roomId);
        return this;
    }

    /**
     * 拉取帧同步历史帧（供断线重连或慢客户端追帧）。
     *
     * <p>环形缓冲区保留最近 N 帧（{@code frameSyncHistoryCapacity}，默认 300），
     * 超出范围的旧帧已被覆盖，返回帧数可能少于 {@code limit}。
     *
     * @param roomId      帧同步房间 ID
     * @param fromFrameNo 期望的起始帧序号（含，从 1 开始）
     * @param limit       最多返回帧数
     * @return 历史帧列表；若尚无历史则返回空列表
     */
    public List<VKGameFrame> pollFrames(String roomId, long fromFrameNo, int limit) {
        return RUNTIME.pollFrames(roomId, fromFrameNo, limit);
    }
}
