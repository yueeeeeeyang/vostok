package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKEvictionPolicy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 ConcurrentHashMap 的内存缓存提供者。
 * <p>
 * 功能特性：
 * <ul>
 *   <li>Bug2/3 修复：incrBy / hset / hdel / lpush / sadd / zadd 改为 {@code compute()} 单步原子，消除读改写竞态</li>
 *   <li>Perf2：后台 daemon 线程定期随机采样过期 key，仿 Redis 策略主动清理</li>
 *   <li>Feature2：maxEntries + evictionPolicy（LRU/LFU/FIFO）容量限制与淘汰</li>
 * </ul>
 */
public class VKMemoryCacheProvider implements VKCacheProvider {
    /** 共享存储，所有 Client 均访问同一 Map（内存隔离由缓存分区上层 holder 保证）。 */
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /** 全局单调递增序号，LRU/FIFO 使用：插入/访问时记录当前序号。 */
    private final AtomicLong seqCounter = new AtomicLong(0);

    /** 后台过期清理线程（Perf2）。 */
    private volatile Thread evictionThread;

    /** 关闭标志，驱逐线程检测此标志以退出循环。 */
    private volatile boolean closed = false;

    /** 缓存配置（init 后可读）。 */
    private volatile VKCacheConfig config;

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public void init(VKCacheConfig cfg) {
        this.config = cfg;
        startEvictionThread(cfg);
    }

    @Override
    public VKCacheClient createClient() {
        return new Client(store, seqCounter, () -> config);
    }

    @Override
    public boolean validate(VKCacheClient client) {
        return true;
    }

    @Override
    public void destroy(VKCacheClient client) {
        // 内存客户端无资源需要销毁
    }

    @Override
    public void close() {
        closed = true;
        Thread t = evictionThread;
        if (t != null) {
            t.interrupt();
        }
        store.clear();
    }

    // ---- 后台驱逐线程（Perf2 + Feature2） ----

    /**
     * 启动后台 daemon 驱逐线程。
     * <p>
     * 线程每隔 {@code memoryEvictionIntervalMs}（默认 5s）执行一次随机采样驱逐：
     * 随机采样 20 个 key，若过期比例 &gt; 25% 则继续循环，直到比例降至 25% 以下（仿 Redis 策略）。
     * 若配置了 {@code maxEntries}，在采样后还会触发容量淘汰。
     */
    private void startEvictionThread(VKCacheConfig cfg) {
        long interval = cfg.getMemoryEvictionIntervalMs();
        if (interval <= 0) {
            interval = 5000;
        }
        final long finalInterval = interval;
        Thread t = new Thread(() -> {
            while (!closed) {
                try {
                    Thread.sleep(finalInterval);
                } catch (InterruptedException e) {
                    if (closed) return;
                }
                try {
                    expireSample();
                    evictByCapacity();
                } catch (Throwable ignore) {
                    // 保持驱逐线程存活
                }
            }
        }, "vostok-cache-memory-evictor");
        t.setDaemon(true);
        t.start();
        evictionThread = t;
    }

    /**
     * 随机采样过期清理（仿 Redis active expiry 策略）。
     * <p>
     * 每轮随机取 20 个 key 检测过期，若过期比例 &gt; 25% 则继续循环，否则退出。
     * 保证在大量 key 批量过期时能快速回收内存，同时避免全量扫描的 CPU 开销。
     */
    private void expireSample() {
        final int SAMPLE_SIZE = 20;
        final double THRESHOLD = 0.25;

        List<String> keys = new ArrayList<>(store.keySet());
        if (keys.isEmpty()) return;

        while (true) {
            int expired = 0;
            int sample = Math.min(SAMPLE_SIZE, keys.size());
            for (int i = 0; i < sample; i++) {
                // 随机选取一个 key
                int idx = ThreadLocalRandom.current().nextInt(keys.size());
                String key = keys.get(idx);
                Entry e = store.get(key);
                if (e != null && e.expired()) {
                    // remove 时同时传入 e，避免删除其他线程刚写入的新值
                    store.remove(key, e);
                    keys.remove(idx); // 从候选列表也移除，避免重复采样
                    expired++;
                    if (keys.isEmpty()) return;
                }
            }
            // 若过期比例 <= 25%，退出循环
            if (sample == 0 || (double) expired / sample <= THRESHOLD) {
                break;
            }
        }
    }

    /**
     * 容量淘汰（Feature2）：当 store.size() &gt; maxEntries 时，
     * 对 entrySet 快照按策略排序，淘汰最旧 10% 条目。
     */
    private void evictByCapacity() {
        VKCacheConfig cfg = this.config;
        if (cfg == null) return;
        int max = cfg.getMaxEntries();
        if (max <= 0) return;

        int current = store.size();
        if (current <= max) return;

        VKEvictionPolicy policy = cfg.getEvictionPolicy();
        if (policy == null || policy == VKEvictionPolicy.NONE) return;

        // 快照 entrySet（O(n)，低频触发可接受）
        List<Map.Entry<String, Entry>> snapshot = new ArrayList<>(store.entrySet());

        // 按策略排序（越"旧"的排在前面 → 先被淘汰）
        switch (policy) {
            case LRU ->
                // accessSeq 越小 → 最近最少使用
                snapshot.sort((a, b) -> Long.compare(a.getValue().accessSeq(), b.getValue().accessSeq()));
            case LFU ->
                // accessCount 越小 → 最不频繁使用
                snapshot.sort((a, b) -> Long.compare(a.getValue().accessCount(), b.getValue().accessCount()));
            case FIFO ->
                // insertSeq 越小 → 最先插入
                snapshot.sort((a, b) -> Long.compare(a.getValue().insertSeq(), b.getValue().insertSeq()));
            default -> {
                return;
            }
        }

        // 淘汰足够多的条目使 size 降至 maxEntries 以下（至少淘汰超出的部分，再多 10% 作为缓冲）
        int excess = current - max;
        int evictCount = excess + Math.max(1, current / 10); // 超出量 + 10% 缓冲
        evictCount = Math.min(evictCount, snapshot.size());
        for (int i = 0; i < evictCount; i++) {
            String key = snapshot.get(i).getKey();
            store.remove(key, snapshot.get(i).getValue());
        }
    }

    // ---- Entry 内部记录 ----

    /**
     * 缓存条目，携带过期时间、LRU 访问序号、LFU 访问计数和插入序号（FIFO）。
     *
     * @param value      实际存储值（byte[] 或集合类型）
     * @param expireAtMs 绝对过期时间戳（ms），0 表示永不过期
     * @param insertSeq  插入时的全局序号（FIFO 使用）
     * @param accessSeq  最近一次访问的全局序号（LRU 使用）；set 时初始化为 insertSeq
     * @param accessCount 访问次数（LFU 使用）
     */
    record Entry(Object value, long expireAtMs, long insertSeq, long accessSeq, long accessCount) {
        boolean expired() {
            return expireAtMs > 0 && System.currentTimeMillis() >= expireAtMs;
        }

        /** 返回更新了 accessSeq 和 accessCount 的新 Entry（用于 LRU/LFU 更新）。 */
        Entry accessed(long newSeq) {
            return new Entry(value, expireAtMs, insertSeq, newSeq, accessCount + 1);
        }
    }

    // ---- Client 内部实现 ----

    private static final class Client implements VKCacheClient {
        private final ConcurrentHashMap<String, Entry> store;
        private final AtomicLong seqCounter;
        /** 通过 Supplier 延迟获取 config，避免 init 前 NPE。 */
        private final java.util.function.Supplier<VKCacheConfig> configSupplier;

        private Client(ConcurrentHashMap<String, Entry> store,
                       AtomicLong seqCounter,
                       java.util.function.Supplier<VKCacheConfig> configSupplier) {
            this.store = store;
            this.seqCounter = seqCounter;
            this.configSupplier = configSupplier;
        }

        @Override
        public byte[] get(String key) {
            Entry e = aliveAndTouch(key);
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
            long seq = seqCounter.incrementAndGet();
            store.put(key, new Entry(copy(value), exp, seq, seq, 0L));
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
            // 使用 compute 原子地更新 expireAtMs，避免与并发 set 产生竞态
            boolean[] updated = {false};
            store.computeIfPresent(key, (k, e) -> {
                if (e.expired()) {
                    return null; // 已过期，触发移除
                }
                updated[0] = true;
                return new Entry(e.value(), System.currentTimeMillis() + ttlMs,
                        e.insertSeq(), e.accessSeq(), e.accessCount());
            });
            return updated[0];
        }

        /**
         * Bug2 修复：使用 compute() 原子完成"读-改-写"，消除非原子读改写竞态。
         * 原实现先 alive() 读旧值，再 put() 写新值，两步之间其他线程可能插入写操作。
         */
        @Override
        public long incrBy(String key, long delta) {
            long[] result = {0};
            long newSeq = seqCounter.incrementAndGet();
            store.compute(key, (k, e) -> {
                long cur = 0;
                long exp = 0;
                if (e != null && !e.expired()) {
                    exp = e.expireAtMs();
                    if (e.value() instanceof byte[] b) {
                        try {
                            cur = Long.parseLong(new String(b, StandardCharsets.UTF_8).trim());
                        } catch (NumberFormatException ignore) {
                            // 非数字值视为 0
                        }
                    }
                }
                result[0] = cur + delta;
                String val = String.valueOf(result[0]);
                return new Entry(val.getBytes(StandardCharsets.UTF_8), exp, newSeq, newSeq, 0L);
            });
            return result[0];
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

        /**
         * Bug3 修复：使用 compute() 原子完成 hash 字段的读取和更新，避免并发 hset 同一 key 时丢失字段。
         */
        @Override
        public long hset(String key, String field, byte[] value) {
            long[] result = {0};
            long newSeq = seqCounter.incrementAndGet();
            store.compute(key, (k, e) -> {
                Map<String, byte[]> map;
                long exp = 0;
                if (e == null || e.expired() || !(e.value() instanceof Map<?, ?> old)) {
                    map = new LinkedHashMap<>();
                } else {
                    // 防御性拷贝，避免修改共享 Map 影响正在读取的线程
                    map = new LinkedHashMap<>(castMap(old));
                    exp = e.expireAtMs();
                }
                boolean exists = map.containsKey(field);
                map.put(field, copy(value));
                result[0] = exists ? 0 : 1;
                return new Entry(map, exp, newSeq, newSeq, 0L);
            });
            return result[0];
        }

        @Override
        public byte[] hget(String key, String field) {
            Entry e = aliveAndTouch(key);
            if (e == null || !(e.value() instanceof Map<?, ?> map)) {
                return null;
            }
            byte[] v = (byte[]) map.get(field);
            return copy(v);
        }

        @Override
        public Map<String, byte[]> hgetAll(String key) {
            Entry e = aliveAndTouch(key);
            if (e == null || !(e.value() instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, byte[]> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), copy((byte[]) entry.getValue()));
            }
            return out;
        }

        /**
         * Bug3 修复：使用 compute() 原子完成 hash 字段删除。
         */
        @Override
        public long hdel(String key, String... fields) {
            long[] result = {0};
            long newSeq = seqCounter.incrementAndGet();
            store.compute(key, (k, e) -> {
                if (e == null || e.expired() || !(e.value() instanceof Map<?, ?> raw)) {
                    return e; // 不存在或已过期，保持原状
                }
                Map<String, byte[]> map = new LinkedHashMap<>(castMap(raw));
                for (String field : fields) {
                    if (map.remove(field) != null) {
                        result[0]++;
                    }
                }
                return new Entry(map, e.expireAtMs(), e.insertSeq(), newSeq, e.accessCount());
            });
            return result[0];
        }

        /**
         * Bug3 修复：使用 compute() 原子完成 list 头部插入。
         */
        @Override
        public long lpush(String key, byte[]... values) {
            long[] result = {0};
            long newSeq = seqCounter.incrementAndGet();
            store.compute(key, (k, e) -> {
                List<byte[]> list;
                long exp = 0;
                if (e == null || e.expired() || !(e.value() instanceof List<?> raw)) {
                    list = new ArrayList<>();
                } else {
                    list = new ArrayList<>(castList(raw));
                    exp = e.expireAtMs();
                }
                if (values != null) {
                    for (byte[] value : values) {
                        list.add(0, copy(value));
                    }
                }
                result[0] = list.size();
                return new Entry(list, exp, newSeq, newSeq, 0L);
            });
            return result[0];
        }

        @Override
        public List<byte[]> lrange(String key, long start, long stop) {
            Entry e = aliveAndTouch(key);
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

        /**
         * Bug3 修复：使用 compute() 原子完成 set 成员添加。
         */
        @Override
        public long sadd(String key, byte[]... members) {
            long[] result = {0};
            long newSeq = seqCounter.incrementAndGet();
            store.compute(key, (k, e) -> {
                Set<BytesKey> set;
                long exp = 0;
                if (e == null || e.expired() || !(e.value() instanceof Set<?> raw)) {
                    set = new LinkedHashSet<>();
                } else {
                    set = new LinkedHashSet<>(castSet(raw));
                    exp = e.expireAtMs();
                }
                if (members != null) {
                    for (byte[] member : members) {
                        if (set.add(new BytesKey(copy(member)))) {
                            result[0]++;
                        }
                    }
                }
                return new Entry(set, exp, newSeq, newSeq, 0L);
            });
            return result[0];
        }

        @Override
        public Set<byte[]> smembers(String key) {
            Entry e = aliveAndTouch(key);
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

        /**
         * Bug3 修复：使用 compute() 原子完成 sorted set 成员添加和排序。
         */
        @Override
        public long zadd(String key, double score, byte[] member) {
            long[] result = {0};
            long newSeq = seqCounter.incrementAndGet();
            store.compute(key, (k, e) -> {
                List<ZEntry> list;
                long exp = 0;
                if (e == null || e.expired() || !(e.value() instanceof List<?> raw)) {
                    list = new ArrayList<>();
                } else {
                    list = new ArrayList<>(castZList(raw));
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
                    result[0] = 1;
                }
                list.sort((a, b) -> {
                    int c = Double.compare(a.score, b.score);
                    return c != 0 ? c : compareBytes(a.member.value, b.member.value);
                });
                return new Entry(list, exp, newSeq, newSeq, 0L);
            });
            return result[0];
        }

        @Override
        public List<byte[]> zrange(String key, long start, long stop) {
            Entry e = aliveAndTouch(key);
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
            // 内存客户端无连接资源，空操作
        }

        // ---- 内部辅助 ----

        /**
         * 返回存活的 Entry，惰性删除已过期 key。
         * 不更新 accessSeq（用于写操作前读检查）。
         */
        private Entry alive(String key) {
            Entry e = store.get(key);
            if (e == null) return null;
            if (e.expired()) {
                store.remove(key, e);
                return null;
            }
            return e;
        }

        /**
         * 返回存活 Entry，同时更新 accessSeq（LRU 用）和 accessCount（LFU 用）。
         * 用于所有读操作（get/hget/lrange 等）。
         */
        private Entry aliveAndTouch(String key) {
            Entry e = store.get(key);
            if (e == null) return null;
            if (e.expired()) {
                store.remove(key, e);
                return null;
            }
            // 更新访问序号（LRU/LFU 统计），使用 compute 保证原子性
            long newSeq = seqCounter.incrementAndGet();
            // 使用 computeIfPresent 避免空指针，同时对已过期者返回 null 触发移除
            final Entry[] result = {e};
            store.computeIfPresent(key, (k, cur) -> {
                if (cur.expired()) {
                    result[0] = null;
                    return null; // 触发移除
                }
                Entry updated = cur.accessed(newSeq);
                result[0] = updated;
                return updated;
            });
            return result[0];
        }

        private int normalizedIndex(long i, int size) {
            if (size == 0) return 0;
            int idx = (int) i;
            if (idx < 0) idx = size + idx;
            if (idx < 0) idx = 0;
            return idx;
        }

        private boolean match(String pattern, String key) {
            if ("*".equals(pattern)) return true;
            if (!pattern.contains("*")) return pattern.equals(key);
            String[] parts = pattern.split("\\*", -1);
            int pos = 0;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) continue;
                int found = key.indexOf(part, pos);
                if (found < 0) return false;
                if (i == 0 && !pattern.startsWith("*") && found != 0) return false;
                pos = found + part.length();
            }
            return pattern.endsWith("*") || pos == key.length();
        }

        @SuppressWarnings("unchecked")
        private static Map<String, byte[]> castMap(Map<?, ?> map) {
            return (Map<String, byte[]>) map;
        }

        @SuppressWarnings("unchecked")
        private static List<byte[]> castList(List<?> list) {
            return (List<byte[]>) list;
        }

        @SuppressWarnings("unchecked")
        private static Set<BytesKey> castSet(Set<?> set) {
            return (Set<BytesKey>) set;
        }

        @SuppressWarnings("unchecked")
        private static List<ZEntry> castZList(List<?> list) {
            return (List<ZEntry>) list;
        }

        private static byte[] copy(byte[] v) {
            return v == null ? null : Arrays.copyOf(v, v.length);
        }

        private static int compareBytes(byte[] a, byte[] b) {
            int n = Math.min(a.length, b.length);
            for (int i = 0; i < n; i++) {
                int d = (a[i] & 0xFF) - (b[i] & 0xFF);
                if (d != 0) return d;
            }
            return Integer.compare(a.length, b.length);
        }
    }

    // ---- 内部数据结构 ----

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
