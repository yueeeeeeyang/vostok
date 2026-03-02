package yueyang.vostok.cache.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存客户端接口，定义所有底层缓存命令。
 * <p>
 * 所有方法均操作原始字节数组（byte[]），编解码由上层完成。
 * 实现类通常由 {@link VKCacheProvider#createClient()} 工厂方法创建，
 * 并由连接池（{@link yueyang.vostok.cache.core.VKCacheConnectionPool}）统一管理生命周期。
 */
public interface VKCacheClient extends AutoCloseable {
    byte[] get(String key);

    void set(String key, byte[] value, long ttlMs);

    long del(String... keys);

    boolean exists(String key);

    boolean expire(String key, long ttlMs);

    long incrBy(String key, long delta);

    List<byte[]> mget(String... keys);

    void mset(Map<String, byte[]> kv);

    long hset(String key, String field, byte[] value);

    byte[] hget(String key, String field);

    Map<String, byte[]> hgetAll(String key);

    long hdel(String key, String... fields);

    long lpush(String key, byte[]... values);

    List<byte[]> lrange(String key, long start, long stop);

    long sadd(String key, byte[]... members);

    Set<byte[]> smembers(String key);

    long zadd(String key, double score, byte[] member);

    List<byte[]> zrange(String key, long start, long stop);

    List<String> scan(String pattern, int count);

    boolean ping();

    /**
     * 将此连接标记为无效（Bug6 修复）。
     * <p>
     * 调用后，连接池在归还时会销毁该连接而非复用。
     * 默认实现为空操作（适用于不需要"标记失效"语义的实现，如内存 Provider）。
     */
    default void invalidate() {
        // 默认空操作；连接池的 PooledClient 会覆盖此方法
    }

    /**
     * 批量执行 Pipeline 命令（Feature4）。
     * <p>
     * 接收预编码的命令列表，每条命令为 {@code List<byte[]>}，
     * 格式与 Redis RESP 协议的数组命令一致（第 0 个元素为命令名称字节数组）。
     * <p>
     * 默认实现：逐条顺序执行（适用于内存 Provider，无网络 RTT 开销）。
     * Redis Provider 会覆盖此方法实现真正的批量发送。
     *
     * @param commands 命令列表，每条命令为 [{@code cmd}, {@code arg1}, {@code arg2}, ...]
     * @return 每条命令的返回值列表（顺序与输入一致），SET/DEL/EXPIRE 返回 null，INCRBY 返回 Long
     */
    default List<Object> executePipeline(List<List<byte[]>> commands) {
        List<Object> results = new ArrayList<>(commands.size());
        for (List<byte[]> cmd : commands) {
            results.add(executeOneCommand(cmd));
        }
        return results;
    }

    /**
     * 执行单条预编码命令（供默认 Pipeline 实现使用）。
     * <p>
     * 命令格式（byte[] 数组）：
     * <ul>
     *   <li>SET: [b"SET", keyBytes, valueBytes, ttlMsBytes]</li>
     *   <li>DEL: [b"DEL", keyBytes...]</li>
     *   <li>INCRBY: [b"INCRBY", keyBytes, deltaBytes]</li>
     *   <li>EXPIRE: [b"EXPIRE", keyBytes, ttlMsBytes]</li>
     * </ul>
     *
     * @param cmd 命令字节数组列表
     * @return 命令结果，INCRBY 返回 Long，其余返回 null
     */
    private Object executeOneCommand(List<byte[]> cmd) {
        if (cmd == null || cmd.isEmpty()) {
            return null;
        }
        String cmdName = new String(cmd.get(0), java.nio.charset.StandardCharsets.UTF_8).toUpperCase();
        switch (cmdName) {
            case "SET": {
                if (cmd.size() < 4) return null;
                String key = new String(cmd.get(1), java.nio.charset.StandardCharsets.UTF_8);
                byte[] value = cmd.get(2);
                long ttlMs = Long.parseLong(new String(cmd.get(3), java.nio.charset.StandardCharsets.UTF_8));
                set(key, value, ttlMs);
                return null;
            }
            case "DEL": {
                String[] keys = new String[cmd.size() - 1];
                for (int i = 1; i < cmd.size(); i++) {
                    keys[i - 1] = new String(cmd.get(i), java.nio.charset.StandardCharsets.UTF_8);
                }
                return del(keys);
            }
            case "INCRBY": {
                if (cmd.size() < 3) return null;
                String key = new String(cmd.get(1), java.nio.charset.StandardCharsets.UTF_8);
                long delta = Long.parseLong(new String(cmd.get(2), java.nio.charset.StandardCharsets.UTF_8));
                return incrBy(key, delta);
            }
            case "EXPIRE": {
                if (cmd.size() < 3) return null;
                String key = new String(cmd.get(1), java.nio.charset.StandardCharsets.UTF_8);
                long ttlMs = Long.parseLong(new String(cmd.get(2), java.nio.charset.StandardCharsets.UTF_8));
                expire(key, ttlMs);
                return null;
            }
            default:
                return null;
        }
    }

    @Override
    void close();
}
