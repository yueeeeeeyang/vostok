package yueyang.vostok;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKMemoryCacheProvider;
import yueyang.vostok.cache.redis.spi.VKRedisClientPool;
import yueyang.vostok.cache.redis.spi.VKRedisClientPoolFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试专用的外部 Redis 连接池支持类。
 * <p>
 * 这里不依赖真实 Jedis/Lettuce，而是用内存 provider 模拟“第三方池 + 第三方客户端”的生命周期，
 * 重点验证 Vostok 对外部池的借还、失效和统计接线是否正确。
 */
final class TestExternalRedisPoolSupport {
    private TestExternalRedisPoolSupport() {
    }

    static final class Factory implements VKRedisClientPoolFactory {
        private final boolean reportMetrics;
        private final AtomicInteger createCount = new AtomicInteger();
        private volatile Pool pool;
        private volatile VKCacheConfig lastConfig;

        Factory(boolean reportMetrics) {
            this.reportMetrics = reportMetrics;
        }

        @Override
        public VKRedisClientPool create(VKCacheConfig config) {
            createCount.incrementAndGet();
            lastConfig = config;
            pool = new Pool(reportMetrics);
            return pool;
        }

        int createCount() {
            return createCount.get();
        }

        Pool pool() {
            return pool;
        }

        VKCacheConfig lastConfig() {
            return lastConfig;
        }
    }

    static final class Pool implements VKRedisClientPool {
        private final VKMemoryCacheProvider provider = new VKMemoryCacheProvider();
        private final boolean reportMetrics;
        private final AtomicInteger borrowCount = new AtomicInteger();
        private final AtomicInteger normalCloseCount = new AtomicInteger();
        private final AtomicInteger invalidCloseCount = new AtomicInteger();
        private volatile boolean closed;
        private volatile String failOnGetKey;

        Pool(boolean reportMetrics) {
            this.reportMetrics = reportMetrics;
            provider.init(new VKCacheConfig());
        }

        @Override
        public VKCacheClient borrow() {
            if (closed) {
                throw new IllegalStateException("pool already closed");
            }
            borrowCount.incrementAndGet();
            return new TrackingClient(this, provider.createClient());
        }

        @Override
        public VKCachePoolMetrics metrics(String cacheName) {
            if (!reportMetrics) {
                return VKRedisClientPool.super.metrics(cacheName);
            }
            return new VKCachePoolMetrics(cacheName, 12, 4, 8, 2, 1, 3, 0);
        }

        @Override
        public void close() {
            closed = true;
            provider.close();
        }

        int borrowCount() {
            return borrowCount.get();
        }

        int normalCloseCount() {
            return normalCloseCount.get();
        }

        int invalidCloseCount() {
            return invalidCloseCount.get();
        }

        boolean isClosed() {
            return closed;
        }

        void failOnGetKey(String key) {
            this.failOnGetKey = key;
        }

        String readString(String key) {
            try (VKCacheClient client = provider.createClient()) {
                byte[] value = client.get(key);
                return value == null ? null : new String(value, StandardCharsets.UTF_8);
            }
        }
    }

    private static final class TrackingClient implements VKCacheClient {
        private final Pool owner;
        private final VKCacheClient delegate;
        private boolean invalid;
        private boolean closed;

        private TrackingClient(Pool owner, VKCacheClient delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        @Override
        public byte[] get(String key) {
            if (owner.failOnGetKey != null && owner.failOnGetKey.equals(key)) {
                throw new VKCacheException(VKCacheErrorCode.COMMAND_ERROR, "forced external pool failure");
            }
            return delegate.get(key);
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
            delegate.set(key, value, ttlMs);
        }

        @Override
        public long del(String... keys) {
            return delegate.del(keys);
        }

        @Override
        public boolean exists(String key) {
            return delegate.exists(key);
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            return delegate.expire(key, ttlMs);
        }

        @Override
        public long incrBy(String key, long delta) {
            return delegate.incrBy(key, delta);
        }

        @Override
        public List<byte[]> mget(String... keys) {
            return delegate.mget(keys);
        }

        @Override
        public void mset(Map<String, byte[]> kv) {
            delegate.mset(kv);
        }

        @Override
        public long hset(String key, String field, byte[] value) {
            return delegate.hset(key, field, value);
        }

        @Override
        public byte[] hget(String key, String field) {
            return delegate.hget(key, field);
        }

        @Override
        public Map<String, byte[]> hgetAll(String key) {
            return delegate.hgetAll(key);
        }

        @Override
        public long hdel(String key, String... fields) {
            return delegate.hdel(key, fields);
        }

        @Override
        public long lpush(String key, byte[]... values) {
            return delegate.lpush(key, values);
        }

        @Override
        public List<byte[]> lrange(String key, long start, long stop) {
            return delegate.lrange(key, start, stop);
        }

        @Override
        public long sadd(String key, byte[]... members) {
            return delegate.sadd(key, members);
        }

        @Override
        public Set<byte[]> smembers(String key) {
            return delegate.smembers(key);
        }

        @Override
        public long zadd(String key, double score, byte[] member) {
            return delegate.zadd(key, score, member);
        }

        @Override
        public List<byte[]> zrange(String key, long start, long stop) {
            return delegate.zrange(key, start, stop);
        }

        @Override
        public List<String> scan(String pattern, int count) {
            return delegate.scan(pattern, count);
        }

        @Override
        public boolean ping() {
            return delegate.ping();
        }

        @Override
        public void invalidate() {
            invalid = true;
            delegate.invalidate();
        }

        @Override
        public List<Object> executePipeline(List<List<byte[]>> commands) {
            return delegate.executePipeline(commands);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (invalid) {
                owner.invalidCloseCount.incrementAndGet();
            } else {
                owner.normalCloseCount.incrementAndGet();
            }
            delegate.close();
        }
    }
}
