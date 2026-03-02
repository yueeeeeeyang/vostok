package yueyang.vostok.cache;

/**
 * 内存缓存淘汰策略枚举。
 * <p>
 * 当内存 Provider 的条目数超过 {@code maxEntries} 时，后台线程按照此策略选取并淘汰最旧条目：
 * <ul>
 *   <li>{@link #LRU} — 最近最少使用（Least Recently Used），淘汰 accessSeq 最小的条目</li>
 *   <li>{@link #LFU} — 最不频繁使用（Least Frequently Used），淘汰访问次数最少的条目</li>
 *   <li>{@link #FIFO} — 先进先出，淘汰插入时间最早的条目</li>
 *   <li>{@link #NONE} — 不主动淘汰，仅依赖 TTL 过期和后台 expire 清理</li>
 * </ul>
 */
public enum VKEvictionPolicy {
    LRU,
    LFU,
    FIFO,
    NONE
}
