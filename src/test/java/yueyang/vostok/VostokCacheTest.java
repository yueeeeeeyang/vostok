package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheConfigFactory;
import yueyang.vostok.cache.VKCacheProviderType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    void testConfigFactoryFromMapAndProperties() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("cache.provider", "memory");
        map.put("cache.keyPrefix", "demo:");
        map.put("cache.maxActive", "4");

        VKCacheConfig fromMap = VKCacheConfigFactory.fromMap(map, "cache");
        assertEquals(VKCacheProviderType.MEMORY, fromMap.getProviderType());
        assertEquals("demo:", fromMap.getKeyPrefix());
        assertEquals(4, fromMap.getMaxActive());

        Path file = Files.createTempFile("vostok-cache", ".properties");
        Files.writeString(file, "cache.provider=memory\ncache.codec=string\ncache.defaultTtlMs=123\n");
        VKCacheConfig fromFile = VKCacheConfigFactory.fromProperties(file, "cache");
        assertEquals(VKCacheProviderType.MEMORY, fromFile.getProviderType());
        assertEquals("string", fromFile.getCodec());
        assertEquals(123, fromFile.getDefaultTtlMs());
    }

    private static final class User {
        public String name;
        public int age;
    }
}
