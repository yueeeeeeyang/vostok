package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.redis.spi.VKRedisClientPool;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 把公共 Redis 外部连接池 SPI 适配为 Cache 运行时内部池接口。
 * <p>
 * 这里仍然复用 Vostok 自己的限流统计，避免业务切到 Jedis/Lettuce 后，
 * 运行时在 metrics 和退化策略上出现行为断层。
 */
final class VKRedisExternalClientPoolAdapter implements VKCacheClientPool {
    private final VKRedisClientPool delegate;
    private final VKCacheRateLimiter rateLimiter;
    private final AtomicLong rejectedByRateLimit = new AtomicLong(0);
    private volatile boolean closed;

    VKRedisExternalClientPoolAdapter(VKRedisClientPool delegate, VKCacheConfig config) {
        this.delegate = delegate;
        this.rateLimiter = new VKCacheRateLimiter(config.getRateLimitQps());
    }

    @Override
    public VKCacheClient borrow() {
        ensureOpen();
        VKCacheClient client = delegate.borrow();
        if (client == null) {
            throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR,
                    "External redis client pool returned null client");
        }
        return client;
    }

    @Override
    public boolean allowCommand() {
        boolean allowed = rateLimiter.allow();
        if (!allowed) {
            rejectedByRateLimit.incrementAndGet();
        }
        return allowed;
    }

    @Override
    public VKCachePoolMetrics metrics(String cacheName) {
        VKCachePoolMetrics raw = delegate.metrics(cacheName);
        if (raw == null) {
            raw = new VKCachePoolMetrics(cacheName, -1, -1, -1, 0, 0, 0, 0);
        }
        return new VKCachePoolMetrics(
                raw.cacheName(),
                raw.total(),
                raw.active(),
                raw.idle(),
                raw.borrowTimeouts(),
                raw.leakedConnections(),
                raw.evictedConnections(),
                rejectedByRateLimit.get()
        );
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        delegate.close();
    }

    private void ensureOpen() {
        if (closed) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR, "External redis client pool is closed");
        }
    }
}
