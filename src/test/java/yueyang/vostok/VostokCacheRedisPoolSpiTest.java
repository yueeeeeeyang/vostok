package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.cache.exception.VKCacheException;

import static org.junit.jupiter.api.Assertions.*;

public class VostokCacheRedisPoolSpiTest {
    @AfterEach
    void tearDown() {
        Vostok.Cache.close();
    }

    @Test
    void testRedisExternalPoolCanServeCommandsAndExposeMetrics() {
        TestExternalRedisPoolSupport.Factory factory = new TestExternalRedisPoolSupport.Factory(true);
        VKCacheConfig config = new VKCacheConfig()
                .providerType(VKCacheProviderType.REDIS)
                .codec("string")
                .redisClientPoolFactory(factory);

        Vostok.Cache.init(config);
        Vostok.Cache.set("user:1", "tom");

        assertEquals("tom", Vostok.Cache.get("user:1"));
        assertEquals(1, factory.createCount());
        assertNotNull(factory.pool());
        assertTrue(factory.pool().borrowCount() > 0);
        assertEquals("tom", factory.pool().readString("user:1"));

        VKCachePoolMetrics metrics = Vostok.Cache.poolMetrics().stream()
                .filter(it -> "default".equals(it.cacheName()))
                .findFirst()
                .orElseThrow();
        assertEquals(12, metrics.total());
        assertEquals(4, metrics.active());
        assertEquals(8, metrics.idle());
        assertEquals(2, metrics.borrowTimeouts());
        assertEquals(1, metrics.leakedConnections());
        assertEquals(3, metrics.evictedConnections());
        assertEquals(0, metrics.rejectedByRateLimit());

        Vostok.Cache.close();
        assertTrue(factory.pool().isClosed());
    }

    @Test
    void testRedisExternalPoolInvalidatedClientAndUnknownMetrics() {
        TestExternalRedisPoolSupport.Factory factory = new TestExternalRedisPoolSupport.Factory(false);
        VKCacheConfig config = new VKCacheConfig()
                .providerType(VKCacheProviderType.REDIS)
                .codec("string")
                .retryEnabled(false)
                .redisClientPoolFactory(factory);

        Vostok.Cache.init(config);
        factory.pool().failOnGetKey("boom");

        assertThrows(VKCacheException.class, () -> Vostok.Cache.get("boom"));
        assertEquals(1, factory.pool().invalidCloseCount());
        assertEquals(0, factory.pool().normalCloseCount());

        VKCachePoolMetrics metrics = Vostok.Cache.poolMetrics().stream()
                .filter(it -> "default".equals(it.cacheName()))
                .findFirst()
                .orElseThrow();
        assertEquals(-1, metrics.total());
        assertEquals(-1, metrics.active());
        assertEquals(-1, metrics.idle());
        assertEquals(0, metrics.borrowTimeouts());
        assertEquals(0, metrics.leakedConnections());
        assertEquals(0, metrics.evictedConnections());
    }
}
