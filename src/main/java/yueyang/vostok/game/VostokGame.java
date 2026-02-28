package yueyang.vostok.game;

import yueyang.vostok.game.command.VKGameCommand;
import yueyang.vostok.game.core.runtime.VKGameRuntime;
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
        return RUNTIME.createRoom(roomId, gameType);
    }

    public VKGameRoom createRoom(String gameType) {
        return RUNTIME.createRoom(gameType);
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
}
