package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokCacheTieredRedisPoolSpiTest {
    @AfterEach
    void tearDown() {
        Vostok.Cache.close();
    }

    @Test
    void testTieredL2CanUseExternalRedisPool() {
        TestExternalRedisPoolSupport.Factory factory = new TestExternalRedisPoolSupport.Factory(true);
        VKCacheConfig config = new VKCacheConfig()
                .providerType(VKCacheProviderType.TIERED)
                .codec("string")
                .l1Config(new VKCacheConfig()
                        .providerType(VKCacheProviderType.MEMORY)
                        .codec("string")
                        .defaultTtlMs(60_000L))
                .l2Config(new VKCacheConfig()
                        .providerType(VKCacheProviderType.REDIS)
                        .codec("string")
                        .redisClientPoolFactory(factory));

        Vostok.Cache.init(config);
        Vostok.Cache.set("order:1", "A001");

        assertEquals("A001", Vostok.Cache.get("order:1"));
        assertEquals("A001", factory.pool().readString("order:1"));
        assertEquals(1, factory.createCount());
        assertTrue(factory.pool().borrowCount() > 0);

        Vostok.Cache.delete("order:1");
        assertNull(factory.pool().readString("order:1"));
    }
}
