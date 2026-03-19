package yueyang.vostok.cache.redis.spi;

import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.provider.VKCacheClient;

/**
 * Redis 外部连接池 SPI。
 * <p>
 * 业务项目可通过实现此接口，把 Jedis/Lettuce 等第三方连接池适配为 Vostok 可消费的借还模型。
 * 返回的 {@link VKCacheClient} 需要自行处理 close()/invalidate() 的生命周期语义。
 */
public interface VKRedisClientPool extends AutoCloseable {
    /**
     * 借出一个 Redis 客户端。
     */
    VKCacheClient borrow();

    /**
     * 返回连接池指标快照。
     * <p>
     * 若第三方池无法提供 total/active/idle 等原生统计，可直接使用默认实现，
     * 此时这些字段按 -1 返回，表示“未知但可用”。
     */
    default VKCachePoolMetrics metrics(String cacheName) {
        return new VKCachePoolMetrics(cacheName, -1, -1, -1, 0, 0, 0, 0);
    }

    @Override
    void close();
}
