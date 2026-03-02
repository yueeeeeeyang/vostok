package yueyang.vostok.cache.stats;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存命中率统计器（线程安全）。
 * <p>
 * 统计点：
 * <ul>
 *   <li>{@code get()} → HIT（命中） / MISS（未命中）</li>
 *   <li>{@code getOrLoad()} → LOAD（触发 loader 回源） + loadTimeNs（加载耗时纳秒）</li>
 *   <li>null marker 命中 → NULL_HIT（命中空值占位）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * VKCacheStats stats = VostokCache.stats();
 * double hitRate = stats.hitRate();
 * }</pre>
 */
public final class VKCacheStats {
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong nullHits = new AtomicLong(0);
    private final AtomicLong loads = new AtomicLong(0);
    private final AtomicLong loadTimeNs = new AtomicLong(0);

    /** 记录一次命中（普通 HIT）。 */
    public void recordHit() {
        hits.incrementAndGet();
    }

    /** 记录一次未命中（MISS）。 */
    public void recordMiss() {
        misses.incrementAndGet();
    }

    /**
     * 记录一次 null 标记命中（NULL_HIT）。
     * null 命中也算作一种 HIT（防穿透成功）。
     */
    public void recordNullHit() {
        nullHits.incrementAndGet();
        hits.incrementAndGet();
    }

    /**
     * 记录一次 loader 回源加载。
     *
     * @param elapsedNs loader 执行耗时（纳秒）
     */
    public void recordLoad(long elapsedNs) {
        loads.incrementAndGet();
        loadTimeNs.addAndGet(elapsedNs);
    }

    /**
     * 命中次数（含 NULL_HIT）。
     */
    public long getHits() {
        return hits.get();
    }

    /**
     * 未命中次数。
     */
    public long getMisses() {
        return misses.get();
    }

    /**
     * null 标记命中次数。
     */
    public long getNullHits() {
        return nullHits.get();
    }

    /**
     * loader 被调用次数（回源加载次数）。
     */
    public long getLoads() {
        return loads.get();
    }

    /**
     * 所有 loader 执行总耗时（纳秒）。
     */
    public long getLoadTimeNs() {
        return loadTimeNs.get();
    }

    /**
     * 命中率（0.0 ~ 1.0）。若尚无请求则返回 0.0。
     * <p>
     * 计算公式：{@code hits / (hits + misses)}
     */
    public double hitRate() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? 0.0 : (double) h / total;
    }

    /**
     * 重置所有统计数据。
     */
    public void reset() {
        hits.set(0);
        misses.set(0);
        nullHits.set(0);
        loads.set(0);
        loadTimeNs.set(0);
    }

    /**
     * 生成快照字符串，便于日志输出。
     */
    @Override
    public String toString() {
        return "VKCacheStats{hits=" + hits.get() +
                ", misses=" + misses.get() +
                ", nullHits=" + nullHits.get() +
                ", loads=" + loads.get() +
                ", loadTimeNs=" + loadTimeNs.get() +
                ", hitRate=" + String.format("%.4f", hitRate()) +
                "}";
    }
}
