package yueyang.vostok.game.frame;

import java.util.List;

/**
 * 帧同步数据帧。
 *
 * <p>服务端每完成一次 {@code onTick} 后，将本帧内所有已通过治理验证的玩家输入打包为一个
 * {@code VKGameFrame}，通过 {@link VKGameFrameNotifier} 广播给客户端。
 * 客户端按帧序号顺序执行 {@code inputs}，与服务端保持逻辑一致的本地模拟。
 *
 * <p>字段含义：
 * <ul>
 *   <li>{@code roomId}          — 所属房间 ID</li>
 *   <li>{@code frameNo}         — 房间帧序号（等于该帧执行后的 {@code room.getCurrentTick()} 值），从 1 开始单调递增</li>
 *   <li>{@code serverTimestamp} — 服务端处理该帧时的 Unix 时间戳（毫秒），用于客户端网络延迟估算</li>
 *   <li>{@code inputs}          — 本帧内所有已处理的玩家输入列表，顺序与服务端执行顺序一致；
 *                                 若本帧无任何输入（静默帧），此列表为空</li>
 * </ul>
 */
public record VKGameFrame(
        String roomId,
        long frameNo,
        long serverTimestamp,
        List<VKGameFrameInput> inputs
) {
}
