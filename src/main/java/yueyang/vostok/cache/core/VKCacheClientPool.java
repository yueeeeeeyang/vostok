package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.provider.VKCacheClient;

/**
 * Cache 运行时内部统一使用的客户端池抽象。
 * <p>
 * 该接口把默认内建池与外部 Redis 连接池收敛到同一借还模型，确保运行时主路径
 * 仍然只依赖 borrow -> invalidate -> close 这组固定语义。
 */
public interface VKCacheClientPool extends AutoCloseable {
    VKCacheClient borrow();

    boolean allowCommand();

    VKCachePoolMetrics metrics(String cacheName);

    @Override
    void close();
}
