package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKCacheProvider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
    private boolean closed;

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

    private void release(PooledState state) {
        synchronized (this) {
            borrowed.remove(state.id);
            if (active > 0) {
                active--;
            }
            state.borrowedAtMs = 0;
            state.lastUsedMs = System.currentTimeMillis();
            if (closed) {
                destroyClient(state, false);
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
                // keep evictor alive
            }
        }
    }

    private void evictIdleAndValidate() {
        synchronized (this) {
            if (closed) {
                return;
            }
            long now = System.currentTimeMillis();
            Deque<PooledState> kept = new ArrayDeque<>();
            while (!idle.isEmpty()) {
                PooledState state = idle.pollFirst();
                boolean remove = false;
                if (config.getIdleTimeoutMs() > 0 && now - state.lastUsedMs >= config.getIdleTimeoutMs()) {
                    remove = true;
                }
                if (!remove && config.getIdleValidationIntervalMs() > 0 && !provider.validate(state.client)) {
                    remove = true;
                }
                if (remove && total > config.getMinIdle()) {
                    destroyClient(state, true);
                } else {
                    kept.offerLast(state);
                }
            }
            idle.addAll(kept);
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

    private static final class PooledClient implements VKCacheClient {
        private final VKCacheConnectionPool pool;
        private final PooledState state;
        private boolean returned;

        private PooledClient(VKCacheConnectionPool pool, PooledState state) {
            this.pool = pool;
            this.state = state;
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
        public java.util.List<byte[]> mget(String... keys) {
            return state.client.mget(keys);
        }

        @Override
        public void mset(java.util.Map<String, byte[]> kv) {
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
        public java.util.Map<String, byte[]> hgetAll(String key) {
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
        public java.util.List<byte[]> lrange(String key, long start, long stop) {
            return state.client.lrange(key, start, stop);
        }

        @Override
        public long sadd(String key, byte[]... members) {
            return state.client.sadd(key, members);
        }

        @Override
        public java.util.Set<byte[]> smembers(String key) {
            return state.client.smembers(key);
        }

        @Override
        public long zadd(String key, double score, byte[] member) {
            return state.client.zadd(key, score, member);
        }

        @Override
        public java.util.List<byte[]> zrange(String key, long start, long stop) {
            return state.client.zrange(key, start, stop);
        }

        @Override
        public java.util.List<String> scan(String pattern, int count) {
            return state.client.scan(pattern, count);
        }

        @Override
        public boolean ping() {
            return state.client.ping();
        }

        @Override
        public void close() {
            if (returned) {
                return;
            }
            returned = true;
            pool.release(state);
        }
    }

    private static final class PooledState {
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
