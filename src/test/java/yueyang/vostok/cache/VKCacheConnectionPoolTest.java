package yueyang.vostok.cache;

import org.junit.jupiter.api.Test;
import yueyang.vostok.cache.core.VKCacheConnectionPool;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKCacheProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VKCacheConnectionPoolTest {
    @Test
    void testBorrowReleaseAndTimeout() {
        DummyProvider provider = new DummyProvider();
        VKCacheConfig cfg = new VKCacheConfig()
                .maxActive(1)
                .minIdle(0)
                .maxWaitMs(80)
                .testOnBorrow(false);
        provider.init(cfg);

        VKCacheConnectionPool pool = new VKCacheConnectionPool(provider, cfg);
        VKCacheClient c1 = pool.borrow();
        assertEquals(1, pool.total());
        assertEquals(1, pool.active());

        VKCacheException ex = assertThrows(VKCacheException.class, pool::borrow);
        assertTrue(ex.getMessage().contains("timed out"));

        c1.close();
        assertEquals(0, pool.active());
        assertEquals(1, pool.idle());

        VKCacheClient c2 = pool.borrow();
        c2.close();
        pool.close();

        assertTrue(provider.destroyed.get() >= 1);
    }

    @Test
    void testTestOnBorrowDestroyInvalidConnection() {
        DummyProvider provider = new DummyProvider();
        provider.valid = false;
        VKCacheConfig cfg = new VKCacheConfig()
                .maxActive(2)
                .minIdle(1)
                .maxWaitMs(50)
                .testOnBorrow(true);
        provider.init(cfg);

        VKCacheConnectionPool pool = new VKCacheConnectionPool(provider, cfg);
        assertThrows(VKCacheException.class, pool::borrow);
        assertTrue(provider.destroyed.get() >= 1);
    }

    private static final class DummyProvider implements VKCacheProvider {
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger destroyed = new AtomicInteger();
        private volatile boolean valid = true;

        @Override
        public String type() {
            return "dummy";
        }

        @Override
        public void init(VKCacheConfig config) {
            // no-op
        }

        @Override
        public VKCacheClient createClient() {
            created.incrementAndGet();
            return new DummyClient();
        }

        @Override
        public boolean validate(VKCacheClient client) {
            return valid;
        }

        @Override
        public void destroy(VKCacheClient client) {
            destroyed.incrementAndGet();
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private static final class DummyClient implements VKCacheClient {
        @Override
        public byte[] get(String key) {
            return null;
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
        }

        @Override
        public long del(String... keys) {
            return 0;
        }

        @Override
        public boolean exists(String key) {
            return false;
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            return false;
        }

        @Override
        public long incrBy(String key, long delta) {
            return 0;
        }

        @Override
        public List<byte[]> mget(String... keys) {
            return List.of();
        }

        @Override
        public void mset(Map<String, byte[]> kv) {
        }

        @Override
        public void close() {
        }
    }
}
