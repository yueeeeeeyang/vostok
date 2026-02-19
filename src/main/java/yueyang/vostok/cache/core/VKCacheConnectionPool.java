package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKCacheProvider;

import java.util.ArrayDeque;
import java.util.Deque;

public final class VKCacheConnectionPool implements AutoCloseable {
    private final VKCacheProvider provider;
    private final VKCacheConfig config;
    private final Deque<VKCacheClient> idle = new ArrayDeque<>();

    private int total;
    private int active;
    private boolean closed;

    public VKCacheConnectionPool(VKCacheProvider provider, VKCacheConfig config) {
        this.provider = provider;
        this.config = config;
        int preheat = Math.max(0, Math.min(config.getMinIdle(), config.getMaxActive()));
        for (int i = 0; i < preheat; i++) {
            idle.offer(createClient());
        }
    }

    public VKCacheClient borrow() {
        long waitMs = Math.max(0, config.getMaxWaitMs());
        long deadline = System.currentTimeMillis() + waitMs;

        synchronized (this) {
            ensureOpen();
            while (true) {
                if (waitMs > 0 && System.currentTimeMillis() >= deadline) {
                    throw new VKCacheException(VKCacheErrorCode.TIMEOUT,
                            "Cache pool borrow timed out (maxWaitMs=" + waitMs + ")");
                }
                VKCacheClient client = idle.pollFirst();
                if (client != null) {
                    if (config.isTestOnBorrow() && !provider.validate(client)) {
                        destroyClient(client);
                        continue;
                    }
                    active++;
                    return new PooledClient(this, client);
                }

                if (total < Math.max(1, config.getMaxActive())) {
                    VKCacheClient raw = createClient();
                    if (config.isTestOnBorrow() && !provider.validate(raw)) {
                        destroyClient(raw);
                        continue;
                    }
                    active++;
                    return new PooledClient(this, raw);
                }

                if (waitMs <= 0) {
                    throw new VKCacheException(VKCacheErrorCode.TIMEOUT,
                            "Cache pool borrow timed out (maxWaitMs=" + waitMs + ")");
                }

                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) {
                    throw new VKCacheException(VKCacheErrorCode.TIMEOUT,
                            "Cache pool borrow timed out (maxWaitMs=" + waitMs + ")");
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

    private VKCacheClient createClient() {
        VKCacheClient client = provider.createClient();
        total++;
        return client;
    }

    private void release(VKCacheClient client) {
        synchronized (this) {
            if (active > 0) {
                active--;
            }
            if (closed) {
                destroyClient(client);
                return;
            }
            if (config.isTestOnReturn() && !provider.validate(client)) {
                destroyClient(client);
            } else {
                idle.offerLast(client);
            }
            this.notifyAll();
        }
    }

    private void destroyClient(VKCacheClient client) {
        provider.destroy(client);
        total = Math.max(0, total - 1);
    }

    public synchronized int total() {
        return total;
    }

    public synchronized int idle() {
        return idle.size();
    }

    public synchronized int active() {
        return active;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        while (!idle.isEmpty()) {
            destroyClient(idle.pollFirst());
        }
        this.notifyAll();
    }

    private void ensureOpen() {
        if (closed) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR, "Cache connection pool is closed");
        }
    }

    private static final class PooledClient implements VKCacheClient {
        private final VKCacheConnectionPool pool;
        private final VKCacheClient delegate;
        private boolean returned;

        private PooledClient(VKCacheConnectionPool pool, VKCacheClient delegate) {
            this.pool = pool;
            this.delegate = delegate;
        }

        @Override
        public byte[] get(String key) {
            return delegate.get(key);
        }

        @Override
        public void set(String key, byte[] value, long ttlMs) {
            delegate.set(key, value, ttlMs);
        }

        @Override
        public long del(String... keys) {
            return delegate.del(keys);
        }

        @Override
        public boolean exists(String key) {
            return delegate.exists(key);
        }

        @Override
        public boolean expire(String key, long ttlMs) {
            return delegate.expire(key, ttlMs);
        }

        @Override
        public long incrBy(String key, long delta) {
            return delegate.incrBy(key, delta);
        }

        @Override
        public java.util.List<byte[]> mget(String... keys) {
            return delegate.mget(keys);
        }

        @Override
        public void mset(java.util.Map<String, byte[]> kv) {
            delegate.mset(kv);
        }

        @Override
        public void close() {
            if (returned) {
                return;
            }
            returned = true;
            pool.release(delegate);
        }
    }
}
