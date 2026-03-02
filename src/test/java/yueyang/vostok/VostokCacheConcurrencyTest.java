package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheDegradePolicy;
import yueyang.vostok.cache.VKCacheProviderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发压测：验证 Bug2/3/4 修复后的线程安全性。
 */
public class VostokCacheConcurrencyTest {

    @AfterEach
    void tearDown() {
        Vostok.Cache.close();
    }

    /**
     * Bug2 修复验证：100 个线程并发对同一个 key 做 incrBy(1)，
     * 最终值必须精确等于 100，不允许任何丢失。
     */
    @Test
    void testConcurrentIncrBy() throws Exception {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .maxActive(20)
                .minIdle(0));

        int threads = 100;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await(3, TimeUnit.SECONDS);
                    Vostok.Cache.incrBy("concurrent-counter", 1);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(10, TimeUnit.SECONDS), "All threads should complete");
        pool.shutdownNow();

        assertEquals(0, errors.get(), "No errors expected");
        // 最终值必须 = 100（每个线程各加 1）
        long finalValue = Vostok.Cache.incrBy("concurrent-counter", 0);
        assertEquals(100L, finalValue,
                "Expected counter=100 after 100 concurrent incrBy(1), got " + finalValue);
    }

    /**
     * Bug3 修复验证：多线程并发对同一 hash key 做 hset，
     * 所有 field 不应丢失。
     */
    @Test
    void testConcurrentHset() throws Exception {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .maxActive(20)
                .minIdle(0));

        int threads = 50;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await(3, TimeUnit.SECONDS);
                    // 每个线程写入自己的唯一 field
                    Vostok.Cache.hset("concurrent-hash", "field-" + idx, "val-" + idx);
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(done.await(10, TimeUnit.SECONDS), "All threads should complete");
        pool.shutdownNow();

        assertEquals(0, errors.get(), "No errors expected");

        // 验证所有 field 均存在（无丢失）
        Map<String, String> result = Vostok.Cache.hgetAll("concurrent-hash", String.class);
        assertEquals(threads, result.size(),
                "Expected " + threads + " fields, got " + result.size());
        for (int i = 0; i < threads; i++) {
            assertTrue(result.containsKey("field-" + i),
                    "Missing field-" + i);
            assertEquals("val-" + i, result.get("field-" + i),
                    "Wrong value for field-" + i);
        }
    }

    /**
     * Bug4 修复验证：多线程同时触发秒边界，不应出现超发（允许的误差范围内）。
     * <p>
     * 测试策略：等待秒边界，然后 qps+1 个线程同时触发，
     * 由于 QPS = 5，最多 5 个请求通过（超发会导致 > 5 个通过）。
     */
    @Test
    void testRateLimiterNewSecondBoundary() throws Exception {
        final int QPS = 5;
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .rateLimitQps(QPS)
                .degradePolicy(VKCacheDegradePolicy.RETURN_NULL)
                .maxActive(20)
                .minIdle(0)
                .maxWaitMs(50));

        // 先消耗掉当前秒的所有配额
        for (int i = 0; i < QPS * 2; i++) {
            try {
                Vostok.Cache.set("warmup", "v");
            } catch (Exception ignore) {}
        }

        // 等待下一秒边界（精确等待）
        long now = System.currentTimeMillis();
        long nextSec = ((now / 1000) + 1) * 1000;
        Thread.sleep(Math.max(1, nextSec - System.currentTimeMillis()));

        // 在秒边界后同时发起 QPS+3 个请求
        int threads = QPS + 3;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(2, TimeUnit.SECONDS);
                    Vostok.Cache.set("rl-key", "v");
                    allowed.incrementAndGet();
                } catch (Exception ignore) {
                    // 被限流（FAIL_FAST or RETURN_NULL）或超时
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown(); // 同时放行所有线程
        assertTrue(done.await(5, TimeUnit.SECONDS), "All threads should finish");
        pool.shutdownNow();

        // 允许通过的数量不超过 QPS（不超发）
        // 注意：SKIP_WRITE 降级策略下 set 被跳过但不抛异常，所以用 RETURN_NULL 只影响 read
        // 对于写操作 FAIL_FAST 会抛异常，被 catch 后 allowed 不增加
        // 因此只统计 set 成功的数量
        assertTrue(allowed.get() <= QPS,
                "Expected at most " + QPS + " requests allowed, but got " + allowed.get());
        // 至少 1 个请求通过（新秒的第一个请求不应被错误拒绝）
        assertTrue(allowed.get() >= 1,
                "Expected at least 1 request allowed in new second, got " + allowed.get());
    }
}
