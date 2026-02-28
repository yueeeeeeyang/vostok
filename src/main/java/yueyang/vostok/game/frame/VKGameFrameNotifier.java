package yueyang.vostok.game.frame;

/**
 * 帧同步广播回调接口（房间级）。
 *
 * <p>帧同步模式下，每帧 {@code onTick} 完成后，运行时通过此接口将 {@link VKGameFrame}
 * 推送给应用层。应用层负责将帧数据经由 WebSocket / UDP 等网络协议分发给房间内所有在线玩家。
 *
 * <p><b>与 {@link yueyang.vostok.game.message.VKGameMessageNotifier} 的区别：</b>
 * <ul>
 *   <li>{@code VKGameMessageNotifier} 绑定到单个玩家，用于业务消息（聊天、系统通知等），
 *       经由消息中心排队、重试、ACK 确认。</li>
 *   <li>{@code VKGameFrameNotifier} 绑定到整个房间，帧同步数据直接触发回调，绕过消息中心，
 *       无重试、无持久化，保证最低延迟。应用层可在回调中向所有在线玩家批量广播。</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * Vostok.Game.init().bindFrameNotifier("room-1001", frame -> {
 *     byte[] bytes = FrameSerializer.toBytes(frame);
 *     for (String playerId : getOnlinePlayers(frame.roomId())) {
 *         websocket.send(playerId, bytes);
 *     }
 * });
 * }</pre>
 *
 * <p><b>线程安全：</b>回调在 Tick Worker 线程中调用，实现应保证线程安全，且执行耗时不应过长，
 * 避免阻塞 Tick 循环。建议在回调内仅做序列化与异步发送，不执行业务逻辑。
 */
@FunctionalInterface
public interface VKGameFrameNotifier {

    /**
     * 帧广播回调，每帧 {@code onTick} 完成后触发一次。
     *
     * @param frame 本帧数据，包含帧序号、服务器时间戳及所有玩家输入
     */
    void onFrame(VKGameFrame frame);
}
