package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKBloomFilter;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheConfigFactory;
import yueyang.vostok.cache.VKCacheDegradePolicy;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.cache.VKEvictionPolicy;
import yueyang.vostok.cache.VKDefaultBloomFilter;
import yueyang.vostok.cache.event.VKCacheEventType;
import yueyang.vostok.cache.pipeline.VKCachePipelineResult;
import yueyang.vostok.cache.stats.VKCacheStats;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VostokCacheTest {
    @AfterEach
    void tearDown() {
        Vostok.Cache.close();
    }

    @Test
    void testMemorySetGetDeleteExists() {
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY));

        Vostok.Cache.set("k1", "v1");
        assertEquals("v1", Vostok.Cache.get("k1"));
        assertTrue(Vostok.Cache.exists("k1"));

        assertEquals(1, Vostok.Cache.delete("k1"));
        assertFalse(Vostok.Cache.exists("k1"));
        assertNull(Vostok.Cache.get("k1"));
    }

    @Test
    void testJsonCodecObjectRoundTrip() {
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY));

        User user = new User();
        user.name = "neo";
        user.age = 20;
        Vostok.Cache.set("user:1", user);

        User db = Vostok.Cache.get("user:1", User.class);
        assertNotNull(db);
        assertEquals("neo", db.name);
        assertEquals(20, db.age);
    }

    @Test
    void testTtlAndExpire() throws Exception {
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY));

        Vostok.Cache.set("ttl", "ok", 120);
        assertEquals("ok", Vostok.Cache.get("ttl"));
        Thread.sleep(160);
        assertNull(Vostok.Cache.get("ttl"));

        Vostok.Cache.set("ttl2", "ok2");
        assertTrue(Vostok.Cache.expire("ttl2", 100));
        Thread.sleep(140);
        assertNull(Vostok.Cache.get("ttl2"));
    }

    @Test
    void testIncrDecr() {
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY).codec("string"));

        assertEquals(1, Vostok.Cache.incr("counter"));
        assertEquals(6, Vostok.Cache.incrBy("counter", 5));
        assertEquals(5, Vostok.Cache.decr("counter"));
        assertEquals(3, Vostok.Cache.decrBy("counter", 2));
        assertEquals("3", Vostok.Cache.get("counter"));
    }

    @Test
    void testMsetMgetAndWithCache() {
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY).codec("string"));
        Vostok.Cache.registerCache("tenantB", new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .keyPrefix("b:"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "1");
        map.put("b", "2");
        Vostok.Cache.mset(map);
        List<String> vals = Vostok.Cache.mget(String.class, "a", "b", "x");
        assertEquals(Arrays.asList("1", "2", null), vals);

        Vostok.Cache.withCache("tenantB", () -> {
            assertEquals("tenantB", Vostok.Cache.currentCacheName());
            Vostok.Cache.set("a", "B1");
            assertEquals("B1", Vostok.Cache.get("a"));
        });

        assertEquals("1", Vostok.Cache.get("a"));
        assertTrue(Vostok.Cache.cacheNames().contains("tenantB"));
    }

    @Test
    void testDataStructuresAndScan() {
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY).codec("string"));

        Vostok.Cache.hset("h:1", "f1", "v1");
        Vostok.Cache.hset("h:1", "f2", "v2");
        assertEquals("v1", Vostok.Cache.hget("h:1", "f1", String.class));
        assertEquals(2, Vostok.Cache.hgetAll("h:1", String.class).size());
        assertEquals(1, Vostok.Cache.hdel("h:1", "f2"));

        assertEquals(2, Vostok.Cache.lpush("l:1", "a", "b"));
        assertEquals(List.of("b", "a"), Vostok.Cache.lrange("l:1", 0, -1, String.class));

        assertEquals(2, Vostok.Cache.sadd("s:1", "a", "b", "a"));
        assertEquals(Set.of("a", "b"), Vostok.Cache.smembers("s:1", String.class));

        assertEquals(1, Vostok.Cache.zadd("z:1", 2.0, "b"));
        assertEquals(1, Vostok.Cache.zadd("z:1", 1.0, "a"));
        assertEquals(List.of("a", "b"), Vostok.Cache.zrange("z:1", 0, -1, String.class));

        List<String> keys = Vostok.Cache.scan("*", 20);
        assertTrue(keys.stream().anyMatch(k -> k.contains("h:1")));
    }

    @Test
    void testAntiPenetrationSingleFlightAndBloomFilter() throws Exception {
        SimpleBloom bloom = new SimpleBloom();
        VKCacheConfig cfg = new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .nullCacheEnabled(true)
                .nullCacheTtlMs(500)
                .singleFlightEnabled(true)
                .bloomFilter(bloom);
        Vostok.Cache.init(cfg);

        assertNull(Vostok.Cache.get("missing", String.class));

        AtomicInteger nullLoadCalls = new AtomicInteger();
        assertNull(Vostok.Cache.getOrLoad("missing", String.class, 2000, () -> {
            nullLoadCalls.incrementAndGet();
            return null;
        }));
        assertNull(Vostok.Cache.getOrLoad("missing", String.class, 2000, () -> {
            nullLoadCalls.incrementAndGet();
            return null;
        }));
        assertEquals(1, nullLoadCalls.get());

        AtomicInteger loadCalls = new AtomicInteger();
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch done = new CountDownLatch(workers);
        var pool = Executors.newFixedThreadPool(workers);
        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await(2, TimeUnit.SECONDS);
                    String v = Vostok.Cache.getOrLoad("hot", String.class, 5000, () -> {
                        loadCalls.incrementAndGet();
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException ignored) {
                        }
                        return "ok";
                    });
                    assertEquals("ok", v);
                } catch (Exception e) {
                    fail(e);
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, loadCalls.get());
        assertTrue(bloom.mightContain("hot"));
    }

    @Test
    void testRateLimitAndDegradePolicy() throws Exception {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .rateLimitQps(2)
                .degradePolicy(VKCacheDegradePolicy.RETURN_NULL)
                .maxWaitMs(50));

        Vostok.Cache.set("k", "v");
        String v1 = Vostok.Cache.get("k", String.class);
        String v2 = Vostok.Cache.get("k", String.class);
        String v3 = Vostok.Cache.get("k", String.class);
        assertNotNull(v1);
        int nullCount = 0;
        if (v2 == null) {
            nullCount++;
        }
        if (v3 == null) {
            nullCount++;
        }
        assertTrue(nullCount >= 1);

        List<VKCachePoolMetrics> metrics = Vostok.Cache.poolMetrics();
        assertFalse(metrics.isEmpty());
        assertTrue(metrics.get(0).rejectedByRateLimit() >= 1);

        Thread.sleep(1100);
        assertNotNull(Vostok.Cache.get("k", String.class));
    }

    @Test
    void testConfigFactoryFromMapAndProperties() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("cache.provider", "memory");
        map.put("cache.keyPrefix", "demo:");
        map.put("cache.maxActive", "4");
        map.put("cache.redisMode", "cluster");
        map.put("cache.degradePolicy", "skip_write");

        VKCacheConfig fromMap = VKCacheConfigFactory.fromMap(map, "cache");
        assertEquals(VKCacheProviderType.MEMORY, fromMap.getProviderType());
        assertEquals("demo:", fromMap.getKeyPrefix());
        assertEquals(4, fromMap.getMaxActive());
        assertEquals(VKCacheDegradePolicy.SKIP_WRITE, fromMap.getDegradePolicy());

        Path file = Files.createTempFile("vostok-cache", ".properties");
        Files.writeString(file, "cache.provider=memory\ncache.codec=string\ncache.defaultTtlMs=123\ncache.rateLimitQps=10\n");
        VKCacheConfig fromFile = VKCacheConfigFactory.fromProperties(file, "cache");
        assertEquals(VKCacheProviderType.MEMORY, fromFile.getProviderType());
        assertEquals("string", fromFile.getCodec());
        assertEquals(123, fromFile.getDefaultTtlMs());
        assertEquals(10, fromFile.getRateLimitQps());
    }

    private static final class SimpleBloom implements VKBloomFilter {
        private final Set<String> set = java.util.concurrent.ConcurrentHashMap.newKeySet();

        @Override
        public boolean mightContain(String key) {
            return set.contains(key);
        }

        @Override
        public void put(String key) {
            set.add(key);
        }
    }

    private static final class User {
        public String name;
        public int age;
    }

    // ---- 新增测试：Feature6 内置布隆过滤器 ----

    @Test
    void testBuiltinBloomFilter() {
        VKBloomFilter bf = VKBloomFilter.create(1_000, 0.01);
        // 插入 1000 个 key
        for (int i = 0; i < 1000; i++) {
            bf.put("key-" + i);
        }
        // 已插入的 key 必须全部命中（无漏判）
        for (int i = 0; i < 1000; i++) {
            assertTrue(bf.mightContain("key-" + i), "should contain key-" + i);
        }
        // 未插入的 key 误判率应远低于 5%（理论 1%，放宽一些）
        int falsePositives = 0;
        for (int i = 1000; i < 2000; i++) {
            if (bf.mightContain("key-" + i)) {
                falsePositives++;
            }
        }
        assertTrue(falsePositives < 50, "false positive rate too high: " + falsePositives + "/1000");
    }

    // ---- 新增测试：Feature2 内存容量限制 + LRU 淘汰 ----

    @Test
    void testMemoryCapacityLRU() throws Exception {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .maxEntries(5)
                .evictionPolicy(VKEvictionPolicy.LRU)
                .memoryEvictionIntervalMs(200));

        // 依次写入 10 个 key（前 5 个更早写入，后 5 个更新），等待驱逐
        for (int i = 0; i < 10; i++) {
            Vostok.Cache.set("k" + i, "v" + i);
        }
        // 等待后台驱逐线程执行（间隔 200ms）
        Thread.sleep(500);

        // 总 key 数量应 <= maxEntries（可能小于，因为 10% 淘汰策略）
        List<String> keys = Vostok.Cache.scan("k*", 100);
        assertTrue(keys.size() <= 5, "expected <= 5 keys but got " + keys.size());
    }

    // ---- 新增测试：Perf2 后台过期清理 ----

    @Test
    void testMemoryEvictionBackground() throws Exception {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .memoryEvictionIntervalMs(100));

        // 写入 50 个短 TTL 的 key
        for (int i = 0; i < 50; i++) {
            Vostok.Cache.set("expire-k" + i, "v" + i, 80);
        }
        // TTL 过期
        Thread.sleep(120);
        // 后台驱逐线程执行
        Thread.sleep(200);

        // 验证所有过期 key 均已通过惰性删除或后台清理移除
        int remaining = 0;
        for (int i = 0; i < 50; i++) {
            if (Vostok.Cache.exists("expire-k" + i)) {
                remaining++;
            }
        }
        assertEquals(0, remaining, "expected all expired keys removed, but " + remaining + " remain");
    }

    // ---- 新增测试：Feature3 命中率统计 ----

    @Test
    void testCacheStats() {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .nullCacheEnabled(false)); // 关闭 null 缓存，让 miss 不被 null-marker 干扰

        VKCacheStats stats = Vostok.Cache.stats();
        // 初始全为 0
        assertEquals(0, stats.getHits());
        assertEquals(0, stats.getMisses());

        Vostok.Cache.set("s1", "v1");
        // 命中
        Vostok.Cache.get("s1");
        // 未命中
        Vostok.Cache.get("notexist");
        Vostok.Cache.get("notexist2");

        assertEquals(1, stats.getHits());
        assertEquals(2, stats.getMisses());
        assertTrue(stats.hitRate() > 0.3 && stats.hitRate() < 0.4);

        // 重置
        Vostok.Cache.resetStats();
        assertEquals(0, stats.getHits());
        assertEquals(0, stats.getMisses());
        assertEquals(0.0, stats.hitRate());
    }

    // ---- 新增测试：Feature5 事件监听器 ----

    @Test
    void testCacheEventListener() {
        List<VKCacheEventType> events = new CopyOnWriteArrayList<>();

        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .nullCacheEnabled(false)
                .eventListener(event -> events.add(event.type())));

        // SET 事件
        Vostok.Cache.set("ek1", "v1");
        // HIT 事件
        Vostok.Cache.get("ek1");
        // MISS 事件
        Vostok.Cache.get("notexist");
        // DELETE 事件
        Vostok.Cache.delete("ek1");

        assertTrue(events.contains(VKCacheEventType.SET), "expected SET event");
        assertTrue(events.contains(VKCacheEventType.HIT), "expected HIT event");
        assertTrue(events.contains(VKCacheEventType.MISS), "expected MISS event");
        assertTrue(events.contains(VKCacheEventType.DELETE), "expected DELETE event");

        // 事件顺序：SET → HIT → MISS → DELETE
        int setIdx = events.indexOf(VKCacheEventType.SET);
        int hitIdx = events.indexOf(VKCacheEventType.HIT);
        int missIdx = events.indexOf(VKCacheEventType.MISS);
        int delIdx = events.indexOf(VKCacheEventType.DELETE);
        assertTrue(setIdx < hitIdx, "SET should come before HIT");
        assertTrue(hitIdx < missIdx, "HIT should come before MISS");
        assertTrue(missIdx < delIdx, "MISS should come before DELETE");
    }

    // ---- 新增测试：Feature4 Pipeline ----

    @Test
    void testPipeline() {
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string"));

        // set + incr（incrBy(1)）+ expire 批量执行
        byte[] valBytes = "hello".getBytes(StandardCharsets.UTF_8);

        // 先预置 counter（string codec 的 incrBy 从 0 开始）
        VKCachePipelineResult result = Vostok.Cache.pipelineWithResult(pipe -> pipe
                .incrBy("counter", 5)
                .incrBy("counter", 3));

        // 两次 incrBy：第一次返回 5，第二次返回 8
        assertEquals(5L, result.getCount(0));
        assertEquals(8L, result.getCount(1));
        assertEquals(2, result.size());

        // pipeline set + expire，验证值存在
        Vostok.Cache.pipeline(pipe -> pipe
                .set("pipe-key", valBytes, 60_000)
                .expire("pipe-key", 120_000));

        assertEquals("hello", Vostok.Cache.get("pipe-key"));
    }

    // ---- 新增测试：Feature1 TIERED 两级缓存 L1 命中 ----

    @Test
    void testTieredCacheL1Hit() {
        // L1 = 内存（TTL 60s），L2 = 内存（作为后备，模拟 Redis 语义）
        VKCacheConfig l1Cfg = new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .defaultTtlMs(60_000);
        VKCacheConfig l2Cfg = new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY);
        VKCacheConfig tieredCfg = new VKCacheConfig()
                .providerType(VKCacheProviderType.TIERED)
                .l1Config(l1Cfg)
                .l2Config(l2Cfg)
                .codec("string");

        Vostok.Cache.init(tieredCfg);

        // 写入 → 同时写 L1+L2
        Vostok.Cache.set("tkey", "tval");
        // 第一次读，L1 命中
        String v1 = Vostok.Cache.get("tkey");
        assertEquals("tval", v1);
        // 再次读，仍然命中
        String v2 = Vostok.Cache.get("tkey");
        assertEquals("tval", v2);
    }

    // ---- 新增测试：Bug4 限流器秒边界 ----

    @Test
    void testRateLimiterBoundary() throws Exception {
        // 在新秒开始后，第一个请求不应被错误拒绝
        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.MEMORY)
                .codec("string")
                .rateLimitQps(5)
                .degradePolicy(VKCacheDegradePolicy.RETURN_NULL)
                .maxWaitMs(50));

        // 等待下一秒边界
        long now = System.currentTimeMillis();
        long nextSecStart = ((now / 1000) + 1) * 1000;
        Thread.sleep(Math.max(0, nextSecStart - System.currentTimeMillis()) + 5);

        // 新秒第一个请求必须通过
        Vostok.Cache.set("boundary-key", "v");
        String v = Vostok.Cache.get("boundary-key", String.class);
        assertNotNull(v, "First request of new second should not be rate-limited");
    }
}
