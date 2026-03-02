package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L1（内存）+ L2（任意 Provider）两级缓存提供者（Feature1）。
 * <p>
 * 读写策略：
 * <table border="1">
 *   <tr><th>操作</th><th>L1</th><th>L2</th></tr>
 *   <tr><td>get</td><td>先读；命中直接返回</td><td>L1 miss 后读；命中回填 L1（min(ttl, l1DefaultTtl)）</td></tr>
 *   <tr><td>set</td><td>写（min(ttlMs, l1DefaultTtl)）</td><td>写（原始 ttlMs）</td></tr>
 *   <tr><td>del/incr 等</td><td>同步删 L1</td><td>L2 authoritative 执行</td></tr>
 * </table>
 * <p>
 * 配置示例：
 * <pre>{@code
 * new VKCacheConfig()
 *     .providerType(TIERED)
 *     .l1Config(new VKCacheConfig().maxEntries(5000).defaultTtlMs(60_000))
 *     .l2Config(new VKCacheConfig().providerType(REDIS).endpoints("host:6379"))
 * }</pre>
 */
public class VKTieredCacheProvider implements VKCacheProvider {
    private VKCacheProvider l1;
    private VKCacheProvider l2;
    private long l1DefaultTtlMs;

    @Override
    public String type() {
        return "tiered";
    }

    @Override
    public void init(VKCacheConfig config) {
        VKCacheConfig l1Cfg = config.getL1Config();
        VKCacheConfig l2Cfg = config.getL2Config();
        if (l2Cfg == null) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR,
                    "TIERED cache requires l2Config to be set");
        }
        // L1 默认使用内存 Provider
        if (l1Cfg == null) {
            l1Cfg = new VKCacheConfig();
        }
        this.l1DefaultTtlMs = l1Cfg.getDefaultTtlMs() > 0 ? l1Cfg.getDefaultTtlMs() : 60_000;

        this.l1 = new VKMemoryCacheProvider();
        l1.init(l1Cfg);

        this.l2 = VKCacheProviderFactory.create(l2Cfg.getProviderType());
        l2.init(l2Cfg);
    }

    @Override
    public VKCacheClient createClient() {
        ensureInit();
        return new TieredClient(l1.createClient(), l2.createClient(), l1DefaultTtlMs);
    }

    @Override
    public boolean validate(VKCacheClient client) {
        if (client instanceof TieredClient tc) {
            return l2.validate(tc.l2);
        }
        return true;
    }

    @Override
    public void destroy(VKCacheClient client) {
        if (client instanceof TieredClient tc) {
            try { l1.destroy(tc.l1); } catch (Exception ignore) {}
            try { l2.destroy(tc.l2); } catch (Exception ignore) {}
        }
    }

    @Override
    public void close() {
        if (l1 != null) l1.close();
        if (l2 != null) l2.close();
    }

    private void ensureInit() {
        if (l1 == null || l2 == null) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR,
                    "TieredCacheProvider is not initialized");
        }
    }

    // ---- TieredClient ----

    /**
     * 两级缓存客户端：读优先 L1，L1 miss 后读 L2 并回填；写同时写 L1 和 L2。
     */
    static final class TieredClient implements VKCacheClient {
        final VKCacheClient l1;
        final VKCacheClient l2;
        /** L1 缓存的最大 TTL 上限（ms）；回填时取 min(原始ttl, l1DefaultTtlMs)。 */
        private final long l1DefaultTtlMs;

        TieredClient(VKCacheClient l1, VKCacheClient l2, long l1DefaultTtlMs) {
            this.l1 = l1;
            this.l2 = l2;
            this.l1DefaultTtlMs = l1DefaultTtlMs;
        }

        @Override
        public byte[] get(String key) {
            // 先查 L1
            byte[] v = l1.get(key);
            if (v != null) return v;
            // L1 miss，查 L2
            v = l2.get(key);
            if (v != null) {
                // 回填 L1（使用 l1DefaultTtlMs，不设永久 TTL）
                l1.set(key, v, l1DefaultTtlMs);
            }
            return v;
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
            // L1 使用较短 TTL（保证数据不会在 L1 长期驻留而与 L2 不一致）
            long l1Ttl = ttlMs > 0 ? Math.min(ttlMs, l1DefaultTtlMs) : l1DefaultTtlMs;
            l1.set(key, value, l1Ttl);
            // L2 使用原始 TTL（authoritative）
            l2.set(key, value, ttlMs);
        }

        @Override
        public long del(String... keys) {
            // L1 同步删除，L2 authoritative 执行
            l1.del(keys);
            return l2.del(keys);
        }

        @Override
        public boolean exists(String key) {
            if (l1.exists(key)) return true;
            return l2.exists(key);
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            l1.expire(key, Math.min(ttlMs, l1DefaultTtlMs));
            return l2.expire(key, ttlMs);
        }

        @Override
        public long incrBy(String key, long delta) {
            // incrBy 以 L2 为权威，L1 删除（避免脏读）
            l1.del(key);
            return l2.incrBy(key, delta);
        }

        @Override
        public List<byte[]> mget(String... keys) {
            if (keys == null) return List.of();
            List<byte[]> results = new ArrayList<>(keys.length);
            List<Integer> missIdx = new ArrayList<>();
            List<String> missKeys = new ArrayList<>();
            // 第一步：查 L1
            for (int i = 0; i < keys.length; i++) {
                byte[] v = l1.get(keys[i]);
                results.add(v);
                if (v == null) {
                    missIdx.add(i);
                    missKeys.add(keys[i]);
                }
            }
            if (missKeys.isEmpty()) return results;
            // 第二步：L1 miss 的从 L2 批量查
            List<byte[]> l2vals = l2.mget(missKeys.toArray(String[]::new));
            for (int j = 0; j < missIdx.size(); j++) {
                byte[] v = l2vals.get(j);
                if (v != null) {
                    l1.set(missKeys.get(j), v, l1DefaultTtlMs);
                }
                results.set(missIdx.get(j), v);
            }
            return results;
        }

        @Override
        public void mset(Map<String, byte[]> kv) {
            if (kv == null) return;
            // 写 L2（authoritative）
            l2.mset(kv);
            // 写 L1（ttlMs = l1DefaultTtlMs）
            for (Map.Entry<String, byte[]> e : kv.entrySet()) {
                l1.set(e.getKey(), e.getValue(), l1DefaultTtlMs);
            }
        }

        @Override
        public long hset(String key, String field, byte[] value) {
            l1.del(key); // hash 结构写 L2，L1 失效
            return l2.hset(key, field, value);
        }

        @Override
        public byte[] hget(String key, String field) {
            return l2.hget(key, field);
        }

        @Override
        public Map<String, byte[]> hgetAll(String key) {
            return l2.hgetAll(key);
        }

        @Override
        public long hdel(String key, String... fields) {
            l1.del(key);
            return l2.hdel(key, fields);
        }

        @Override
        public long lpush(String key, byte[]... values) {
            l1.del(key);
            return l2.lpush(key, values);
        }

        @Override
        public List<byte[]> lrange(String key, long start, long stop) {
            return l2.lrange(key, start, stop);
        }

        @Override
        public long sadd(String key, byte[]... members) {
            l1.del(key);
            return l2.sadd(key, members);
        }

        @Override
        public Set<byte[]> smembers(String key) {
            return l2.smembers(key);
        }

        @Override
        public long zadd(String key, double score, byte[] member) {
            l1.del(key);
            return l2.zadd(key, score, member);
        }

        @Override
        public List<byte[]> zrange(String key, long start, long stop) {
            return l2.zrange(key, start, stop);
        }

        @Override
        public List<String> scan(String pattern, int count) {
            return l2.scan(pattern, count);
        }

        @Override
        public boolean ping() {
            return l2.ping();
        }

        @Override
        public void invalidate() {
            l2.invalidate();
        }

        @Override
        public void close() {
            try { l1.close(); } catch (Exception ignore) {}
            try { l2.close(); } catch (Exception ignore) {}
        }
    }
}
