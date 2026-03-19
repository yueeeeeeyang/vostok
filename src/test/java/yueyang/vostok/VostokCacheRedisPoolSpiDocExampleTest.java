package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokCacheRedisPoolSpiDocExampleTest {
    @AfterEach
    void tearDown() {
        Vostok.Cache.close();
    }

    @Test
    void testReadmeStyleRedisExternalPoolExample() {
        TestExternalRedisPoolSupport.Factory factory = new TestExternalRedisPoolSupport.Factory(false);

        Vostok.Cache.init(new VKCacheConfig()
                .providerType(VKCacheProviderType.REDIS)
                .codec("string")
                .option("jedis.maxTotal", "32")
                .redisClientPoolFactory(factory));

        Vostok.Cache.set("session:1", "ok", 60_000L);
        assertEquals("ok", Vostok.Cache.get("session:1"));
        assertTrue(factory.lastConfig().getOptions().containsKey("jedis.maxTotal"));
    }
}
