package yueyang.vostok.cache.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于秒级窗口的 QPS 限流器（线程安全）。
 * <p>
 * Bug4 修复说明（原方案缺陷）：
 * 原实现使用两个独立原子变量（second + used）。在秒边界发生 CAS 切换后、
 * {@code used.set(0)} 执行前，其他线程可能已经 {@code incrementAndGet()} 到旧计数，
 * 导致新秒的第一个 bucket 超发。
 * <p>
 * 修复方案：将"秒数"和"计数"压缩进单个 {@link AtomicLong}：
 * <ul>
 *   <li>高 32 位：当前秒（int 强转，绕过负数问题）</li>
 *   <li>低 32 位：当前秒内已消耗的请求数</li>
 * </ul>
 * 通过单次 CAS 原子完成"秒切换 + 计数归零"或"计数递增"，彻底消除竞态窗口。
 */
final class VKCacheRateLimiter {
    private final int qps;

    /**
     * 单个 long 编码两个字段：
     * - 高 32 位 = (int) 当前秒（允许自然溢出，仅比较相等性）
     * - 低 32 位 = 当前秒已消耗计数
     */
    private final AtomicLong state;

    VKCacheRateLimiter(int qps) {
        this.qps = Math.max(0, qps);
        // 初始化：高 32 位为当前秒，低 32 位为 0
        int nowSec = (int) (System.currentTimeMillis() / 1000);
        this.state = new AtomicLong(((long) nowSec) << 32);
    }

    /**
     * 判断当前请求是否在 QPS 限额内。
     * <p>
     * 若 qps &le; 0 则无限制。
     * 使用 CAS 自旋保证秒边界切换与计数递增的原子性。
     *
     * @return true 表示允许；false 表示超出限额
     */
    boolean allow() {
        if (qps <= 0) {
            return true;
        }
        // 当前秒（int 截断，与高 32 位存储方式对齐）
        int nowSec = (int) (System.currentTimeMillis() / 1000);
        while (true) {
            long cur = state.get();
            // 高 32 位解码为秒（int）
            int curSec = (int) (cur >>> 32);
            // 低 32 位解码为计数（无符号，用 & 0xFFFFFFFFL）
            long curCount = cur & 0xFFFFFFFFL;

            if (curSec != nowSec) {
                // 秒已经切换：尝试原子归零计数并切换秒，同时记录本次请求（count=1）
                long next = ((long) nowSec << 32) | 1L;
                if (state.compareAndSet(cur, next)) {
                    return true; // CAS 成功，本次请求被记为新秒第一个
                }
                // CAS 失败（其他线程抢先），重试
            } else {
                // 同一秒内
                if (curCount >= qps) {
                    return false; // 已达上限
                }
                // 尝试递增计数
                if (state.compareAndSet(cur, cur + 1)) {
                    return true;
                }
                // CAS 失败，重试
            }
        }
    }
}
