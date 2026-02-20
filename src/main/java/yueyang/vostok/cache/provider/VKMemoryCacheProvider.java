package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private record Entry(Object value, long expireAtMs) {
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
            Entry e = alive(key);
            if (e == null) {
                return null;
            }
            if (e.value() instanceof byte[] b) {
                return Arrays.copyOf(b, b.length);
            }
            return null;
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
            long exp = ttlMs > 0 ? (System.currentTimeMillis() + ttlMs) : 0;
            store.put(key, new Entry(copy(value), exp));
        }

        @Override
        public long del(String... keys) {
            if (keys == null) {
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
            return alive(key) != null;
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            if (ttlMs <= 0) {
                return false;
            }
            Entry e = alive(key);
            if (e == null) {
                return false;
            }
            store.put(key, new Entry(e.value(), System.currentTimeMillis() + ttlMs));
            return true;
        }

        @Override
        public long incrBy(String key, long delta) {
            Entry e = alive(key);
            long current = 0;
            long exp = 0;
            if (e != null) {
                exp = e.expireAtMs();
                if (e.value() instanceof byte[] b) {
                    current = Long.parseLong(new String(b, StandardCharsets.UTF_8));
                }
            }
            long next = current + delta;
            store.put(key, new Entry(String.valueOf(next).getBytes(StandardCharsets.UTF_8), exp));
            return next;
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
            if (kv == null) {
                return;
            }
            for (Map.Entry<String, byte[]> e : kv.entrySet()) {
                set(e.getKey(), e.getValue(), 0);
            }
        }

        @Override
        public long hset(String key, String field, byte[] value) {
            Entry e = alive(key);
            Map<String, byte[]> map;
            long exp = 0;
            if (e == null || !(e.value() instanceof Map<?, ?> old)) {
                map = new LinkedHashMap<>();
            } else {
                map = castMap(old);
                exp = e.expireAtMs();
            }
            boolean exists = map.containsKey(field);
            map.put(field, copy(value));
            store.put(key, new Entry(map, exp));
            return exists ? 0 : 1;
        }

        @Override
        public byte[] hget(String key, String field) {
            Entry e = alive(key);
            if (e == null || !(e.value() instanceof Map<?, ?> map)) {
                return null;
            }
            byte[] v = (byte[]) map.get(field);
            return copy(v);
        }

        @Override
        public Map<String, byte[]> hgetAll(String key) {
            Entry e = alive(key);
            if (e == null || !(e.value() instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, byte[]> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), copy((byte[]) entry.getValue()));
            }
            return out;
        }

        @Override
        public long hdel(String key, String... fields) {
            Entry e = alive(key);
            if (e == null || !(e.value() instanceof Map<?, ?> raw)) {
                return 0;
            }
            Map<String, byte[]> map = castMap(raw);
            long count = 0;
            for (String field : fields) {
                if (map.remove(field) != null) {
                    count++;
                }
            }
            store.put(key, new Entry(map, e.expireAtMs()));
            return count;
        }

        @Override
        public long lpush(String key, byte[]... values) {
            Entry e = alive(key);
            List<byte[]> list;
            long exp = 0;
            if (e == null || !(e.value() instanceof List<?> raw)) {
                list = new ArrayList<>();
            } else {
                list = castList(raw);
                exp = e.expireAtMs();
            }
            if (values != null) {
                for (byte[] value : values) {
                    list.add(0, copy(value));
                }
            }
            store.put(key, new Entry(list, exp));
            return list.size();
        }

        @Override
        public List<byte[]> lrange(String key, long start, long stop) {
            Entry e = alive(key);
            if (e == null || !(e.value() instanceof List<?> raw)) {
                return List.of();
            }
            List<byte[]> list = castList(raw);
            int from = normalizedIndex(start, list.size());
            int to = normalizedIndex(stop, list.size());
            if (from > to || from >= list.size()) {
                return List.of();
            }
            to = Math.min(to, list.size() - 1);
            List<byte[]> out = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                out.add(copy(list.get(i)));
            }
            return out;
        }

        @Override
        public long sadd(String key, byte[]... members) {
            Entry e = alive(key);
            Set<BytesKey> set;
            long exp = 0;
            if (e == null || !(e.value() instanceof Set<?> raw)) {
                set = new LinkedHashSet<>();
            } else {
                set = castSet(raw);
                exp = e.expireAtMs();
            }
            long added = 0;
            if (members != null) {
                for (byte[] member : members) {
                    if (set.add(new BytesKey(copy(member)))) {
                        added++;
                    }
                }
            }
            store.put(key, new Entry(set, exp));
            return added;
        }

        @Override
        public Set<byte[]> smembers(String key) {
            Entry e = alive(key);
            if (e == null || !(e.value() instanceof Set<?> raw)) {
                return Set.of();
            }
            Set<BytesKey> set = castSet(raw);
            Set<byte[]> out = new LinkedHashSet<>();
            for (BytesKey keyBytes : set) {
                out.add(copy(keyBytes.value));
            }
            return out;
        }

        @Override
        public long zadd(String key, double score, byte[] member) {
            Entry e = alive(key);
            List<ZEntry> list;
            long exp = 0;
            if (e == null || !(e.value() instanceof List<?> raw)) {
                list = new ArrayList<>();
            } else {
                list = castZList(raw);
                exp = e.expireAtMs();
            }
            BytesKey bytesKey = new BytesKey(copy(member));
            boolean exists = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).member.equals(bytesKey)) {
                    list.set(i, new ZEntry(score, bytesKey));
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                list.add(new ZEntry(score, bytesKey));
            }
            list.sort((a, b) -> {
                int c = Double.compare(a.score, b.score);
                if (c != 0) {
                    return c;
                }
                return compareBytes(a.member.value, b.member.value);
            });
            store.put(key, new Entry(list, exp));
            return exists ? 0 : 1;
        }

        @Override
        public List<byte[]> zrange(String key, long start, long stop) {
            Entry e = alive(key);
            if (e == null || !(e.value() instanceof List<?> raw)) {
                return List.of();
            }
            List<ZEntry> list = castZList(raw);
            int from = normalizedIndex(start, list.size());
            int to = normalizedIndex(stop, list.size());
            if (from > to || from >= list.size()) {
                return List.of();
            }
            to = Math.min(to, list.size() - 1);
            List<byte[]> out = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                out.add(copy(list.get(i).member.value));
            }
            return out;
        }

        @Override
        public List<String> scan(String pattern, int count) {
            String p = pattern == null || pattern.isBlank() ? "*" : pattern;
            int limit = Math.max(1, count);
            List<String> out = new ArrayList<>();
            for (String key : store.keySet()) {
                if (out.size() >= limit) {
                    break;
                }
                if (alive(key) == null) {
                    continue;
                }
                if (match(p, key)) {
                    out.add(key);
                }
            }
            return out;
        }

        @Override
        public boolean ping() {
            return true;
        }

        @Override
        public void close() {
            // no-op
        }

        private Entry alive(String key) {
            Entry e = store.get(key);
            if (e == null) {
                return null;
            }
            if (e.expired()) {
                store.remove(key, e);
                return null;
            }
            return e;
        }

        private int normalizedIndex(long i, int size) {
            if (size == 0) {
                return 0;
            }
            int idx = (int) i;
            if (idx < 0) {
                idx = size + idx;
            }
            if (idx < 0) {
                idx = 0;
            }
            return idx;
        }

        private boolean match(String pattern, String key) {
            if ("*".equals(pattern)) {
                return true;
            }
            if (!pattern.contains("*")) {
                return pattern.equals(key);
            }
            String[] parts = pattern.split("\\*", -1);
            int pos = 0;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) {
                    continue;
                }
                int found = key.indexOf(part, pos);
                if (found < 0) {
                    return false;
                }
                if (i == 0 && !pattern.startsWith("*") && found != 0) {
                    return false;
                }
                pos = found + part.length();
            }
            return pattern.endsWith("*") || pos == key.length();
        }

        @SuppressWarnings("unchecked")
        private Map<String, byte[]> castMap(Map<?, ?> map) {
            return (Map<String, byte[]>) map;
        }

        @SuppressWarnings("unchecked")
        private List<byte[]> castList(List<?> list) {
            return (List<byte[]>) list;
        }

        @SuppressWarnings("unchecked")
        private Set<BytesKey> castSet(Set<?> set) {
            return (Set<BytesKey>) set;
        }

        @SuppressWarnings("unchecked")
        private List<ZEntry> castZList(List<?> list) {
            return (List<ZEntry>) list;
        }

        private byte[] copy(byte[] v) {
            return v == null ? null : Arrays.copyOf(v, v.length);
        }

        private int compareBytes(byte[] a, byte[] b) {
            int n = Math.min(a.length, b.length);
            for (int i = 0; i < n; i++) {
                int d = (a[i] & 0xFF) - (b[i] & 0xFF);
                if (d != 0) {
                    return d;
                }
            }
            return Integer.compare(a.length, b.length);
        }
    }

    private static final class BytesKey {
        private final byte[] value;

        private BytesKey(byte[] value) {
            this.value = value == null ? new byte[0] : value;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof BytesKey other && Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    private static final class ZEntry {
        private final double score;
        private final BytesKey member;

        private ZEntry(double score, BytesKey member) {
            this.score = score;
            this.member = member;
        }
    }
}
