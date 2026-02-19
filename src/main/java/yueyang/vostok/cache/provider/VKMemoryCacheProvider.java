package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VKMemoryCacheProvider implements VKCacheProvider {
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public void init(VKCacheConfig config) {
        // no-op
    }

    @Override
    public VKCacheClient createClient() {
        return new Client(store);
    }

    @Override
    public boolean validate(VKCacheClient client) {
        return true;
    }

    @Override
    public void destroy(VKCacheClient client) {
        // no-op
    }

    @Override
    public void close() {
        store.clear();
    }

    private record Entry(byte[] value, long expireAtMs) {
        boolean expired() {
            return expireAtMs > 0 && System.currentTimeMillis() >= expireAtMs;
        }
    }

    private static final class Client implements VKCacheClient {
        private final ConcurrentHashMap<String, Entry> store;

        private Client(ConcurrentHashMap<String, Entry> store) {
            this.store = store;
        }

        @Override
        public byte[] get(String key) {
            Entry e = store.get(key);
            if (e == null) {
                return null;
            }
            if (e.expired()) {
                store.remove(key, e);
                return null;
            }
            return e.value();
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
            long exp = ttlMs > 0 ? (System.currentTimeMillis() + ttlMs) : 0;
            store.put(key, new Entry(value == null ? null : Arrays.copyOf(value, value.length), exp));
        }

        @Override
        public long del(String... keys) {
            if (keys == null || keys.length == 0) {
                return 0;
            }
            long count = 0;
            for (String key : keys) {
                if (store.remove(key) != null) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public boolean exists(String key) {
            Entry e = store.get(key);
            if (e == null) {
                return false;
            }
            if (e.expired()) {
                store.remove(key, e);
                return false;
            }
            return true;
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            if (ttlMs <= 0) {
                return false;
            }
            Entry e = store.get(key);
            if (e == null || e.expired()) {
                if (e != null) {
                    store.remove(key, e);
                }
                return false;
            }
            store.put(key, new Entry(e.value(), System.currentTimeMillis() + ttlMs));
            return true;
        }

        @Override
        public long incrBy(String key, long delta) {
            return store.compute(key, (k, old) -> {
                long current = 0L;
                if (old != null && !old.expired() && old.value() != null) {
                    current = Long.parseLong(new String(old.value()));
                }
                long next = current + delta;
                return new Entry(String.valueOf(next).getBytes(), old == null ? 0 : old.expireAtMs());
            }) == null ? 0 : Long.parseLong(new String(get(key)));
        }

        @Override
        public List<byte[]> mget(String... keys) {
            List<byte[]> out = new ArrayList<>();
            if (keys == null) {
                return out;
            }
            for (String key : keys) {
                out.add(get(key));
            }
            return out;
        }

        @Override
        public void mset(Map<String, byte[]> kv) {
            if (kv == null || kv.isEmpty()) {
                return;
            }
            for (Map.Entry<String, byte[]> e : kv.entrySet()) {
                set(e.getKey(), e.getValue(), 0);
            }
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
