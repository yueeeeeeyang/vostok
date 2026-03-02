package yueyang.vostok.cache.pipeline;

/**
 * 批量命令 Pipeline 接口（write-only）。
 * <p>
 * Pipeline 提供链式 API，在 {@code executePipeline()} 调用前不立即发送命令，
 * 而是将命令收集为列表，在 flush 阶段一次性批量发送，从而降低网络 RTT。
 * <p>
 * 使用示例：
 * <pre>{@code
 * VostokCache.pipeline(pipe -> pipe
 *     .set("k1", value, 60_000)
 *     .incr("counter")
 *     .expire("k2", 30_000));
 * }</pre>
 * <p>
 * 注意：Pipeline 仅支持写操作（set/del/incr/expire），不支持读取返回值。
 * 如需获取命令执行结果，请使用 {@link yueyang.vostok.cache.VostokCache#pipelineWithResult}。
 */
public interface VKCachePipeline {

    /**
     * 追加一条 SET 命令。
     *
     * @param key   缓存 key
     * @param value 已序列化的字节数组
     * @param ttlMs TTL（毫秒），0 表示永不过期
     * @return this（链式调用）
     */
    VKCachePipeline set(String key, byte[] value, long ttlMs);

    /**
     * 追加一条 DEL 命令。
     *
     * @param keys 要删除的 key 列表
     * @return this
     */
    VKCachePipeline del(String... keys);

    /**
     * 追加一条 INCRBY 命令（delta = 1 时相当于 INCR）。
     *
     * @param key   计数器 key
     * @param delta 增量
     * @return this
     */
    VKCachePipeline incrBy(String key, long delta);

    /**
     * 追加一条 EXPIRE 命令。
     *
     * @param key   目标 key
     * @param ttlMs 新的 TTL（毫秒）
     * @return this
     */
    VKCachePipeline expire(String key, long ttlMs);
}
