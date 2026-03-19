package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheProvider;
import yueyang.vostok.cache.provider.VKCacheProviderFactory;
import yueyang.vostok.cache.redis.spi.VKRedisClientPool;
import yueyang.vostok.cache.redis.spi.VKRedisClientPoolFactory;

/**
 * Cache 内部的连接池装配辅助工具。
 * <p>
 * 顶层运行时和 TIERED 的 L1/L2 都通过这里装配，避免默认内建池与外部 Redis 池
 * 在多个位置各自分叉实现。
 */
public final class VKCachePoolSupport {
    private VKCachePoolSupport() {
    }

    public static ManagedPool create(VKCacheConfig config) {
        if (config == null) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "VKCacheConfig is null");
        }
        if (config.getProviderType() == VKCacheProviderType.REDIS && config.getRedisClientPoolFactory() != null) {
            VKRedisClientPoolFactory factory = config.getRedisClientPoolFactory();
            VKRedisClientPool externalPool = factory.create(config.copy());
            if (externalPool == null) {
                throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR,
                        "Redis external client pool factory returned null");
            }
            return new ManagedPool(null, new VKRedisExternalClientPoolAdapter(externalPool, config));
        }

        VKCacheProvider provider = VKCacheProviderFactory.create(config.getProviderType());
        provider.init(config);
        return new ManagedPool(provider, new VKCacheConnectionPool(provider, config));
    }

    /**
     * 绑定一个内部池与其附属资源（provider 或外部池）的关闭句柄。
     */
    public static final class ManagedPool implements AutoCloseable {
        private final VKCacheProvider provider;
        private final VKCacheClientPool pool;

        ManagedPool(VKCacheProvider provider, VKCacheClientPool pool) {
            this.provider = provider;
            this.pool = pool;
        }

        public VKCacheClientPool pool() {
            return pool;
        }

        @Override
        public void close() {
            try {
                pool.close();
            } finally {
                if (provider != null) {
                    provider.close();
                }
            }
        }
    }
}
