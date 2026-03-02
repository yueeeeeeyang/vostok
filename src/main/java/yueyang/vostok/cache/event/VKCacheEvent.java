package yueyang.vostok.cache.event;

/**
 * 缓存事件数据对象（不可变 record）。
 * <p>
 * 每次触发事件时由 {@link yueyang.vostok.cache.core.VKCacheRuntime} 构造并传递给
 * {@link VKCacheEventListener}。
 *
 * @param cacheName 当前操作所在的缓存分区名称
 * @param type      事件类型
 * @param key       操作的缓存 key（含 prefix）
 * @param extraMs   附加耗时信息（毫秒）；对 LOAD 事件为 loader 执行时长，其余事件为 0
 */
public record VKCacheEvent(String cacheName, VKCacheEventType type, String key, long extraMs) {

    /**
     * 构造一个不携带附加耗时信息的事件（extraMs = 0）。
     */
    public VKCacheEvent(String cacheName, VKCacheEventType type, String key) {
        this(cacheName, type, key, 0L);
    }
}
