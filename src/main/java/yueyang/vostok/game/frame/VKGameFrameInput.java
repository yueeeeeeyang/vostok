package yueyang.vostok.game.frame;

/**
 * 帧同步单条玩家输入。
 *
 * <p>帧同步模式下，服务端在每帧结束时将本帧内所有经过治理验证的玩家指令收集为
 * {@link VKGameFrameInput} 列表，打包进 {@link VKGameFrame} 广播给房间内所有客户端。
 * 客户端拿到完整的输入集合后，在本地按相同顺序执行，实现逻辑一致的帧同步模拟。
 *
 * <p>字段含义：
 * <ul>
 *   <li>{@code playerId}       — 产生该输入的玩家 ID</li>
 *   <li>{@code commandType}    — 指令类型，与 {@link yueyang.vostok.game.command.VKGameCommand#getType()} 对应</li>
 *   <li>{@code payload}        — 指令载荷，语义由业务定义</li>
 *   <li>{@code clientSeq}      — 客户端指令序号（-1 表示未设置），客户端可用于重传检测</li>
 *   <li>{@code clientTimestampMs} — 客户端发出指令时的时间戳（毫秒），服务端不修改此值</li>
 * </ul>
 */
public record VKGameFrameInput(
        String playerId,
        String commandType,
        Object payload,
        long clientSeq,
        long clientTimestampMs
) {
}
