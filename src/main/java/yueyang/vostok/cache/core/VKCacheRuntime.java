package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.codec.VKCacheCodec;
import yueyang.vostok.cache.codec.VKCacheCodecs;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKCacheProvider;
import yueyang.vostok.cache.provider.VKCacheProviderFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class VKCacheRuntime {
    private static final Object LOCK = new Object();
    private static final VKCacheRuntime INSTANCE = new VKCacheRuntime();

    private final ThreadLocal<String> contextName = new ThreadLocal<>();
    private final Map<String, CacheHolder> holders = new ConcurrentHashMap<>();

    private volatile String defaultName = "default";
    private volatile boolean initialized;

    private VKCacheRuntime() {
    }

    public static VKCacheRuntime getInstance() {
        return INSTANCE;
    }

    public boolean started() {
        return initialized;
    }

    public Set<String> cacheNames() {
        return Set.copyOf(holders.keySet());
    }

    public void init(VKCacheConfig config) {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            ensureConfig(config);
            holders.put(defaultName, createHolder(config));
            initialized = true;
        }
    }

    public void reinit(VKCacheConfig config) {
        synchronized (LOCK) {
            ensureConfig(config);
            closeHolders();
            holders.put(defaultName, createHolder(config));
            initialized = true;
            contextName.remove();
        }
    }

    public void registerCache(String name, VKCacheConfig config) {
        ensureInit();
        String cacheName = normalizedName(name);
        ensureConfig(config);

        synchronized (LOCK) {
            CacheHolder old = holders.remove(cacheName);
            if (old != null) {
                old.close();
            }
            holders.put(cacheName, createHolder(config));
        }
    }

    public void withCache(String name, Runnable action) {
        if (action == null) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Runnable is null");
        }
        String prev = contextName.get();
        contextName.set(normalizedName(name));
        try {
            action.run();
        } finally {
            restoreContext(prev);
        }
    }

    public <T> T withCache(String name, Supplier<T> supplier) {
        if (supplier == null) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Supplier is null");
        }
        String prev = contextName.get();
        contextName.set(normalizedName(name));
        try {
            return supplier.get();
        } finally {
            restoreContext(prev);
        }
    }

    public String currentCacheName() {
        ensureInit();
        String current = contextName.get();
        return (current == null || current.isBlank()) ? defaultName : current;
    }

    public VKCacheConfig config() {
        return currentHolder().config.copy();
    }

    public void close() {
        synchronized (LOCK) {
            closeHolders();
            initialized = false;
            contextName.remove();
        }
    }

    public void set(String key, Object value, Long ttlMs) {
        String safeKey = realKey(key);
        CacheHolder holder = currentHolder();
        byte[] payload = holder.codec.encode(value);
        long expire = ttlMs == null ? holder.config.getDefaultTtlMs() : Math.max(0, ttlMs);
        execute(holder, client -> {
            client.set(safeKey, payload, expire);
            return null;
        });
    }

    public <T> T get(String key, Class<T> type) {
        String safeKey = realKey(key);
        CacheHolder holder = currentHolder();
        byte[] payload = execute(holder, client -> client.get(safeKey));
        return holder.codec.decode(payload, type);
    }

    public long delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0;
        }
        String[] real = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            real[i] = realKey(keys[i]);
        }
        return execute(currentHolder(), client -> client.del(real));
    }

    public boolean exists(String key) {
        return execute(currentHolder(), client -> client.exists(realKey(key)));
    }

    public boolean expire(String key, long ttlMs) {
        return execute(currentHolder(), client -> client.expire(realKey(key), ttlMs));
    }

    public long incrBy(String key, long delta) {
        return execute(currentHolder(), client -> client.incrBy(realKey(key), delta));
    }

    public <T> List<T> mget(Class<T> type, String... keys) {
        CacheHolder holder = currentHolder();
        if (keys == null || keys.length == 0) {
            return List.of();
        }
        String[] real = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            real[i] = realKey(keys[i]);
        }
        List<byte[]> values = execute(holder, client -> client.mget(real));
        List<T> out = new ArrayList<>(values.size());
        for (byte[] value : values) {
            out.add(holder.codec.decode(value, type));
        }
        return out;
    }

    public void mset(Map<String, ?> values, Long ttlMs) {
        if (values == null || values.isEmpty()) {
            return;
        }
        CacheHolder holder = currentHolder();
        Map<String, byte[]> encoded = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            encoded.put(realKey(entry.getKey()), holder.codec.encode(entry.getValue()));
        }
        execute(holder, client -> {
            client.mset(encoded);
            long expire = ttlMs == null ? 0 : Math.max(0, ttlMs);
            if (expire > 0) {
                for (String key : encoded.keySet()) {
                    client.expire(key, expire);
                }
            }
            return null;
        });
    }

    private String realKey(String key) {
        if (key == null || key.isBlank()) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Cache key is blank");
        }
        CacheHolder holder = currentHolder();
        String prefix = holder.config.getKeyPrefix();
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        if (key.startsWith(prefix)) {
            return key;
        }
        return prefix + key;
    }

    private CacheHolder currentHolder() {
        ensureInit();
        String name = currentCacheName();
        CacheHolder holder = holders.get(name);
        if (holder == null) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR,
                    "Cache is not registered: " + name);
        }
        return holder;
    }

    private <T> T execute(CacheHolder holder, CacheAction<T> action) {
        int attempts = holder.config.isRetryEnabled() ? Math.max(1, holder.config.getMaxRetries() + 1) : 1;
        RuntimeException last = null;

        for (int i = 0; i < attempts; i++) {
            VKCacheClient client = holder.pool.borrow();
            try {
                return action.run(client);
            } catch (RuntimeException e) {
                last = e;
                if (i + 1 >= attempts) {
                    break;
                }
                backoff(holder.config, i);
            } finally {
                client.close();
            }
        }

        throw last == null
                ? new VKCacheException(VKCacheErrorCode.COMMAND_ERROR, "Cache command failed")
                : last;
    }

    private void backoff(VKCacheConfig config, int attempt) {
        long base = Math.max(1, config.getRetryBackoffBaseMs());
        long max = Math.max(base, config.getRetryBackoffMaxMs());
        long delay = Math.min(max, base << Math.min(10, attempt));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureInit() {
        if (!initialized) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR,
                    "Vostok.Cache is not initialized. Call init() first.");
        }
    }

    private String normalizedName(String name) {
        if (name == null || name.isBlank()) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Cache name is blank");
        }
        return name.trim();
    }

    private void restoreContext(String prev) {
        if (prev == null || prev.isBlank()) {
            contextName.remove();
            return;
        }
        contextName.set(prev);
    }

    private void closeHolders() {
        for (CacheHolder holder : holders.values()) {
            holder.close();
        }
        holders.clear();
    }

    private CacheHolder createHolder(VKCacheConfig config) {
        VKCacheProvider provider = VKCacheProviderFactory.create(config.getProviderType());
        provider.init(config);
        VKCacheConnectionPool pool = new VKCacheConnectionPool(provider, config);
        VKCacheCodec codec = VKCacheCodecs.get(config.getCodec());
        return new CacheHolder(config.copy(), provider, pool, codec);
    }

    private void ensureConfig(VKCacheConfig config) {
        if (config == null) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "VKCacheConfig is null");
        }
        if (config.getMaxActive() <= 0) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "maxActive must be > 0");
        }
        if (config.getMinIdle() < 0) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "minIdle must be >= 0");
        }
        if (config.getMinIdle() > config.getMaxActive()) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "minIdle cannot exceed maxActive");
        }
    }

    @FunctionalInterface
    private interface CacheAction<T> {
        T run(VKCacheClient client);
    }

    private record CacheHolder(VKCacheConfig config,
                               VKCacheProvider provider,
                               VKCacheConnectionPool pool,
                               VKCacheCodec codec) {
        private void close() {
            try {
                pool.close();
            } finally {
                provider.close();
            }
        }
    }
}
