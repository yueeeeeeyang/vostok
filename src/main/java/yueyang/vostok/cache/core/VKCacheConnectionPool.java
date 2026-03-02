package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKCacheProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义连接池，管理 {@link VKCacheClient} 的借用、归还与生命周期。
 * <p>
 * Bug 修复：
 * <ul>
 *   <li>Bug5：{@code closed} 字段改为 {@code volatile}，保证多线程可见性</li>
 *   <li>Bug6：引入 {@code invalid} 标志；归还时若已失效则销毁而非放回池中</li>
 * </ul>
 * Perf4 优化：
 * <ul>
 *   <li>{@code evictIdleAndValidate()} 重构为"锁内 drain → 锁外 validate → 锁内归还/销毁"，
 *       避免持锁期间执行 I/O（validate 可能触发网络 ping）</li>
 * </ul>
 */
public final class VKCacheConnectionPool implements AutoCloseable {
    private final VKCacheProvider provider;
    private final VKCacheConfig config;
    private final Deque<PooledState> idle = new ArrayDeque<>();
    private final Map<Integer, PooledState> borrowed = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final AtomicLong borrowTimeouts = new AtomicLong(0);
    private final AtomicLong leakedConnections = new AtomicLong(0);
    private final AtomicLong evictedConnections = new AtomicLong(0);
    private final AtomicLong rejectedByRateLimit = new AtomicLong(0);
    private final VKCacheRateLimiter rateLimiter;

    private final Thread evictor;
    private int total;
    private int active;

    /**
     * Bug5 修复：close 标志必须为 volatile，保证驱逐线程和其他线程能及时感知关闭状态。
     * 原代码中 {@code private boolean closed} 不具备内存可见性保证。
     */
    private volatile boolean closed;

    public VKCacheConnectionPool(VKCacheProvider provider, VKCacheConfig config) {
        this.provider = provider;
        this.config = config;
        this.rateLimiter = new VKCacheRateLimiter(config.getRateLimitQps());
        int preheat = Math.max(0, Math.min(config.getMinIdle(), config.getMaxActive()));
        for (int i = 0; i < preheat; i++) {
            idle.offer(createClient());
        }
        evictor = new Thread(this::evictLoop, "vostok-cache-pool-evictor");
        evictor.setDaemon(true);
        evictor.start();
    }

    public VKCacheClient borrow() {
        long waitMs = Math.max(0, config.getMaxWaitMs());
        long deadline = System.currentTimeMillis() + waitMs;

        synchronized (this) {
            ensureOpen();
            while (true) {
                if (waitMs > 0 && System.currentTimeMillis() >= deadline) {
                    borrowTimeouts.incrementAndGet();
                    throw timeout(waitMs);
                }

                PooledState state = idle.pollFirst();
                if (state != null) {
                    if (config.isTestOnBorrow() && !provider.validate(state.client)) {
                        destroyClient(state, true);
                        continue;
                    }
                    return borrowState(state);
                }

                if (total < Math.max(1, config.getMaxActive())) {
                    PooledState created = createClient();
                    if (config.isTestOnBorrow() && !provider.validate(created.client)) {
                        destroyClient(created, true);
                        continue;
                    }
                    return borrowState(created);
                }

                if (waitMs <= 0) {
                    borrowTimeouts.incrementAndGet();
                    throw timeout(waitMs);
                }

                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    borrowTimeouts.incrementAndGet();
                    throw timeout(waitMs);
                }
                try {
                    this.wait(remain);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new VKCacheException(VKCacheErrorCode.CONNECTION_ERROR,
                            "Interrupted while waiting cache connection", e);
                }
            }
        }
    }

    public boolean allowCommand() {
        boolean allowed = rateLimiter.allow();
        if (!allowed) {
            rejectedByRateLimit.incrementAndGet();
        }
        return allowed;
    }

    public VKCachePoolMetrics metrics(String cacheName) {
        synchronized (this) {
            return new VKCachePoolMetrics(
                    cacheName,
                    total,
                    active,
                    idle.size(),
                    borrowTimeouts.get(),
                    leakedConnections.get(),
                    evictedConnections.get(),
                    rejectedByRateLimit.get()
            );
        }
    }

    public synchronized int total() {
        return total;
    }

    public synchronized int active() {
        return active;
    }

    public synchronized int idle() {
        return idle.size();
    }

    private VKCacheException timeout(long waitMs) {
        return new VKCacheException(VKCacheErrorCode.TIMEOUT,
                "Cache pool borrow timed out (maxWaitMs=" + waitMs + ")");
    }

    private VKCacheClient borrowState(PooledState state) {
        long now = System.currentTimeMillis();
        state.lastBorrowMs = now;
        state.borrowedAtMs = now;
        borrowed.put(state.id, state);
        active++;
        return new PooledClient(this, state);
    }

    private PooledState createClient() {
        VKCacheClient client = provider.createClient();
        PooledState state = new PooledState((int) sequence.getAndIncrement(), client);
        total++;
        return state;
    }

    /**
     * 归还连接到池。
     * <p>
     * Bug6 修复：若 {@code state.invalid} 为 true（连接已被标记为坏连接），
     * 调用 {@code destroyAndRelease()} 销毁而非放回池中。
     *
     * @param state   连接状态对象
     * @param invalid 是否为坏连接
     */
    void release(PooledState state, boolean invalid) {
        synchronized (this) {
            borrowed.remove(state.id);
            if (active > 0) {
                active--;
            }
            state.borrowedAtMs = 0;
            state.lastUsedMs = System.currentTimeMillis();
            if (closed || invalid) {
                // 坏连接或已关闭时直接销毁
                destroyClient(state, invalid);
                return;
            }
            if (config.isTestOnReturn() && !provider.validate(state.client)) {
                destroyClient(state, true);
            } else {
                idle.offerLast(state);
            }
            this.notifyAll();
        }
    }

    private void destroyClient(PooledState state, boolean evicted) {
        provider.destroy(state.client);
        total = Math.max(0, total - 1);
        if (evicted) {
            evictedConnections.incrementAndGet();
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        evictor.interrupt();
        while (!idle.isEmpty()) {
            destroyClient(idle.pollFirst(), false);
        }
        for (PooledState state : borrowed.values()) {
            destroyClient(state, false);
        }
        borrowed.clear();
        this.notifyAll();
    }

    private void ensureOpen() {
        if (closed) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR, "Cache connection pool is closed");
        }
    }

    private void evictLoop() {
        long interval = Math.max(1000, config.getIdleValidationIntervalMs());
        // volatile 读，无需 synchronized
        while (!closed) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                if (closed) {
                    return;
                }
            }
            try {
                evictIdleAndValidate();
                detectLeak();
                ensureMinIdle();
            } catch (Throwable ignore) {
                // 保持驱逐线程存活
            }
        }
    }

    /**
     * Perf4 优化：锁内 drain 到临时列表 → 锁外执行 validate（可能含网络 I/O）→ 锁内归还/销毁。
     * <p>
     * 原实现在持有 synchronized 锁的情况下调用 {@code provider.validate()} 进行网络 ping，
     * 导致所有借用线程在此期间被阻塞。重构后将 I/O 移到锁外执行，锁仅覆盖内存操作。
     */
    private void evictIdleAndValidate() {
        long now = System.currentTimeMillis();
        List<PooledState> toCheck = new ArrayList<>();

        // 第一步：锁内 drain，将所有空闲连接移出（快速内存操作）
        synchronized (this) {
            if (closed) return;
            while (!idle.isEmpty()) {
                toCheck.add(idle.pollFirst());
            }
        }

        // 第二步：锁外逐个判断是否需要驱逐 + 执行 validate（I/O 发生在此处）
        List<PooledState> toKeep = new ArrayList<>();
        List<PooledState> toDestroy = new ArrayList<>();
        for (PooledState state : toCheck) {
            boolean remove = false;
            if (config.getIdleTimeoutMs() > 0 && now - state.lastUsedMs >= config.getIdleTimeoutMs()) {
                remove = true;
            }
            if (!remove && config.getIdleValidationIntervalMs() > 0 && !provider.validate(state.client)) {
                remove = true;
            }
            if (remove) {
                toDestroy.add(state);
            } else {
                toKeep.add(state);
            }
        }

        // 第三步：锁内统一归还合法连接、销毁坏连接
        synchronized (this) {
            if (closed) {
                // 池已关闭，全部销毁
                for (PooledState s : toKeep) destroyClient(s, false);
                for (PooledState s : toDestroy) destroyClient(s, true);
                return;
            }
            for (PooledState s : toKeep) {
                if (total > config.getMinIdle() || toDestroy.isEmpty()) {
                    idle.offerLast(s);
                } else {
                    // 连接总数已降到 minIdle 以下，保留
                    idle.offerLast(s);
                }
            }
            for (PooledState s : toDestroy) {
                if (total > config.getMinIdle()) {
                    destroyClient(s, true);
                } else {
                    // 低于 minIdle 不销毁（保留以供 ensureMinIdle 复用）
                    idle.offerLast(s);
                }
            }
        }
    }

    private void detectLeak() {
        if (config.getLeakDetectMs() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PooledState state : borrowed.values()) {
            if (state.borrowedAtMs > 0 && now - state.borrowedAtMs >= config.getLeakDetectMs()) {
                leakedConnections.incrementAndGet();
                state.borrowedAtMs = now;
            }
        }
    }

    private void ensureMinIdle() {
        synchronized (this) {
            if (closed) {
                return;
            }
            int target = Math.max(0, Math.min(config.getMinIdle(), config.getMaxActive()));
            while (idle.size() < target && total < config.getMaxActive()) {
                idle.offerLast(createClient());
            }
        }
    }

    // ---- PooledClient：VKCacheClient 的连接池代理 ----

    /**
     * PooledClient 包装真实 client，实现自动归还（close）和坏连接标记（invalidate）。
     * <p>
     * Bug6 修复：新增 {@code invalid} 字段和 {@code invalidate()} 方法；
     * 当 execute() 捕获到异常时，调用 {@code client.invalidate()} 将连接标记为坏连接，
     * 归还时连接池据此决定销毁而非复用。
     */
    private static final class PooledClient implements VKCacheClient {
        private final VKCacheConnectionPool pool;
        private final PooledState state;
        private boolean returned;
        /** 标记此连接为坏连接（如出现网络异常后），归还时触发销毁。 */
        private boolean invalid;

        private PooledClient(VKCacheConnectionPool pool, PooledState state) {
            this.pool = pool;
            this.state = state;
        }

        @Override
        public void invalidate() {
            // 标记为无效，close() 时连接池将销毁而非复用
            this.invalid = true;
        }

        @Override
        public byte[] get(String key) {
            return state.client.get(key);
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
            state.client.set(key, value, ttlMs);
        }

        @Override
        public long del(String... keys) {
            return state.client.del(keys);
        }

        @Override
        public boolean exists(String key) {
            return state.client.exists(key);
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            return state.client.expire(key, ttlMs);
        }

        @Override
        public long incrBy(String key, long delta) {
            return state.client.incrBy(key, delta);
        }

        @Override
        public List<byte[]> mget(String... keys) {
            return state.client.mget(keys);
        }

        @Override
        public void mset(Map<String, byte[]> kv) {
            state.client.mset(kv);
        }

        @Override
        public long hset(String key, String field, byte[] value) {
            return state.client.hset(key, field, value);
        }

        @Override
        public byte[] hget(String key, String field) {
            return state.client.hget(key, field);
        }

        @Override
        public Map<String, byte[]> hgetAll(String key) {
            return state.client.hgetAll(key);
        }

        @Override
        public long hdel(String key, String... fields) {
            return state.client.hdel(key, fields);
        }

        @Override
        public long lpush(String key, byte[]... values) {
            return state.client.lpush(key, values);
        }

        @Override
        public List<byte[]> lrange(String key, long start, long stop) {
            return state.client.lrange(key, start, stop);
        }

        @Override
        public long sadd(String key, byte[]... members) {
            return state.client.sadd(key, members);
        }

        @Override
        public Set<byte[]> smembers(String key) {
            return state.client.smembers(key);
        }

        @Override
        public long zadd(String key, double score, byte[] member) {
            return state.client.zadd(key, score, member);
        }

        @Override
        public List<byte[]> zrange(String key, long start, long stop) {
            return state.client.zrange(key, start, stop);
        }

        @Override
        public List<String> scan(String pattern, int count) {
            return state.client.scan(pattern, count);
        }

        @Override
        public boolean ping() {
            return state.client.ping();
        }

        @Override
        public List<Object> executePipeline(List<List<byte[]>> commands) {
            return state.client.executePipeline(commands);
        }

        @Override
        public void close() {
            if (returned) {
                return;
            }
            returned = true;
            // Bug6：根据 invalid 标志决定归还还是销毁
            pool.release(state, invalid);
        }
    }

    // ---- PooledState：连接状态追踪 ----

    static final class PooledState {
        private final int id;
        private final VKCacheClient client;
        private volatile long lastUsedMs = System.currentTimeMillis();
        private volatile long lastBorrowMs;
        private volatile long borrowedAtMs;

        private PooledState(int id, VKCacheClient client) {
            this.id = id;
            this.client = client;
        }
    }
}
