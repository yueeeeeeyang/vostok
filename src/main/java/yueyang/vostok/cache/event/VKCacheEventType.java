package yueyang.vostok.cache.event;

/**
 * 缓存事件类型枚举。
 * <p>
 * 在 {@link yueyang.vostok.cache.core.VKCacheRuntime} 的各操作路径中同步触发，
 * 通过 {@link VKCacheEventListener} 传递给用户注册的监听器。
 */
public enum VKCacheEventType {
    /** key 写入（set / mset / getOrLoad 写回） */
    SET,
    /** 缓存命中（get/getOrLoad 直接读到有效值） */
    HIT,
    /** 缓存未命中（get/getOrLoad 在缓存中未找到值） */
    MISS,
    /** key 被删除（delete 操作） */
    DELETE,
    /** getOrLoad 触发了 loader 函数（回源加载） */
    LOAD,
    /** 条目因容量限制或 TTL 过期被后台驱逐 */
    EVICTED,
    /** 命中了 null 占位标记（防穿透缓存的 null 缓存） */
    NULL_HIT
}
