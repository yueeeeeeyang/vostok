package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKBloomFilter;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheConfigFactory;
import yueyang.vostok.cache.VKCacheDegradePolicy;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.VKCacheProviderType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
}
