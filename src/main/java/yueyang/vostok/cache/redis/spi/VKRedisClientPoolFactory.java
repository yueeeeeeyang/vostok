package yueyang.vostok.cache.redis.spi;

import yueyang.vostok.cache.VKCacheConfig;

/**
 * Redis 外部连接池工厂 SPI。
 * <p>
 * Vostok 核心不直接依赖 Jedis/Lettuce；业务项目可在自己的工程中实现该工厂并通过
 * {@link yueyang.vostok.cache.VKCacheConfig#redisClientPoolFactory(VKRedisClientPoolFactory)} 注入。
 */
@FunctionalInterface
public interface VKRedisClientPoolFactory {
    /**
     * 根据当前缓存配置创建一个 Redis 客户端池。
     */
    VKRedisClientPool create(VKCacheConfig config);
}
