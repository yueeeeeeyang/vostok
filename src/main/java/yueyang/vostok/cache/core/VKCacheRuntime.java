package yueyang.vostok.cache.core;

import yueyang.vostok.cache.VKBloomFilter;
import yueyang.vostok.cache.VKCacheCommandType;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheDegradePolicy;
import yueyang.vostok.cache.VKCachePoolMetrics;
import yueyang.vostok.cache.codec.VKCacheCodec;
import yueyang.vostok.cache.codec.VKCacheCodecs;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;
import yueyang.vostok.cache.provider.VKCacheClient;
import yueyang.vostok.cache.provider.VKCacheProvider;
import yueyang.vostok.cache.provider.VKCacheProviderFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class VKCacheRuntime {
    private static final Object LOCK = new Object();
    private static final VKCacheRuntime INSTANCE = new VKCacheRuntime();
    private static final byte[] NULL_MARKER = "__vostok_null__".getBytes(StandardCharsets.UTF_8);

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

    public List<VKCachePoolMetrics> poolMetrics() {
        List<VKCachePoolMetrics> out = new ArrayList<>();
        for (Map.Entry<String, CacheHolder> entry : holders.entrySet()) {
            out.add(entry.getValue().pool.metrics(entry.getKey()));
        }
        return out;
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

    public <T> T withKeyLock(String key, Supplier<T> supplier) {
        if (supplier == null) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Supplier is null");
        }
        CacheHolder holder = currentHolder();
        if (!holder.config.isKeyMutexEnabled()) {
            return supplier.get();
        }
        ReentrantLock lock = holder.keyLocks.computeIfAbsent(realKey(key), k -> new ReentrantLock());
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads() && !lock.isLocked() && holder.keyLocks.size() > holder.config.getKeyMutexMaxSize()) {
                holder.keyLocks.clear();
            }
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
        CacheHolder holder = currentHolder();
        String safeKey = realKey(key);
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return;
        }
        byte[] payload = value == null ? NULL_MARKER : holder.codec.encode(value);
        long expire = applyTtlWithJitter(holder.config, ttlMs == null ? holder.config.getDefaultTtlMs() : Math.max(0, ttlMs));
        execute(holder, VKCacheCommandType.WRITE, safeKey, client -> {
            client.set(safeKey, payload, expire);
            return null;
        });
        if (value != null) {
            holder.bloomFilter.put(safeKey);
        }
    }

    public <T> T get(String key, Class<T> type) {
        CacheHolder holder = currentHolder();
        String safeKey = realKey(key);
        if (!allow(holder, VKCacheCommandType.READ)) {
            return null;
        }
        if (!holder.bloomFilter.mightContain(safeKey)) {
            return null;
        }
        byte[] payload = readPayload(holder, safeKey);
        return decodeValue(holder.codec, payload, type);
    }

    public <T> T getOrLoad(String key, Class<T> type, long ttlMs, Supplier<T> loader) {
        CacheHolder holder = currentHolder();
        String safeKey = realKey(key);
        byte[] cachedPayload = null;
        if (allow(holder, VKCacheCommandType.READ) && holder.bloomFilter.mightContain(safeKey)) {
            cachedPayload = readPayload(holder, safeKey);
        }
        if (cachedPayload != null) {
            if (isNullMarker(cachedPayload)) {
                return null;
            }
            return decodeValue(holder.codec, cachedPayload, type);
        }
        if (loader == null) {
            return null;
        }

        if (!holder.config.isSingleFlightEnabled()) {
            return loadAndSet(holder, safeKey, type, ttlMs, loader);
        }

        CompletableFuture<Object> candidate = new CompletableFuture<>();
        CompletableFuture<Object> existing = holder.singleFlight.putIfAbsent(safeKey, candidate);
        CompletableFuture<Object> future = existing == null ? candidate : existing;
        boolean owner = existing == null;
        if (owner) {
            try {
                Object loaded = loadAndSet(holder, safeKey, type, ttlMs, loader);
                future.complete(loaded);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                holder.singleFlight.remove(safeKey);
            }
        }

        try {
            @SuppressWarnings("unchecked")
            T out = (T) future.get();
            return out;
        } catch (Exception e) {
            throw new VKCacheException(VKCacheErrorCode.COMMAND_ERROR, "getOrLoad failed", e);
        }
    }

    private <T> T loadAndSet(CacheHolder holder, String safeKey, Class<T> type, long ttlMs, Supplier<T> loader) {
        T loaded = withKeyLock(safeKey, loader);
        if (loaded == null) {
            if (holder.config.isNullCacheEnabled()) {
                long nttl = applyTtlWithJitter(holder.config, Math.max(1, holder.config.getNullCacheTtlMs()));
                execute(holder, VKCacheCommandType.WRITE, safeKey, client -> {
                    client.set(safeKey, NULL_MARKER, nttl);
                    return null;
                });
                holder.bloomFilter.put(safeKey);
            }
            return null;
        }
        long ttl = applyTtlWithJitter(holder.config, ttlMs > 0 ? ttlMs : holder.config.getDefaultTtlMs());
        byte[] payload = holder.codec.encode(loaded);
        execute(holder, VKCacheCommandType.WRITE, safeKey, client -> {
            client.set(safeKey, payload, ttl);
            return null;
        });
        holder.bloomFilter.put(safeKey);
        return decodeValue(holder.codec, payload, type);
    }

    private byte[] readPayload(CacheHolder holder, String safeKey) {
        return execute(holder, VKCacheCommandType.READ, safeKey, client -> client.get(safeKey));
    }

    public long delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0;
        }
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        String[] real = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            real[i] = realKey(keys[i]);
        }
        return execute(holder, VKCacheCommandType.WRITE, real[0], client -> client.del(real));
    }

    public boolean exists(String key) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return false;
        }
        return execute(holder, VKCacheCommandType.READ, realKey(key), client -> client.exists(realKey(key)));
    }

    public boolean expire(String key, long ttlMs) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return false;
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key), client -> client.expire(realKey(key), ttlMs));
    }

    public long incrBy(String key, long delta) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key), client -> client.incrBy(realKey(key), delta));
    }

    public <T> List<T> mget(Class<T> type, String... keys) {
        CacheHolder holder = currentHolder();
        if (keys == null || keys.length == 0) {
            return List.of();
        }
        if (!allow(holder, VKCacheCommandType.READ)) {
            return List.of();
        }
        String[] real = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            real[i] = realKey(keys[i]);
        }
        List<byte[]> values = execute(holder, VKCacheCommandType.READ, real[0], client -> client.mget(real));
        List<T> out = new ArrayList<>(values.size());
        for (byte[] value : values) {
            out.add(decodeValue(holder.codec, value, type));
        }
        return out;
    }

    public void mset(Map<String, ?> values, Long ttlMs) {
        CacheHolder holder = currentHolder();
        if (values == null || values.isEmpty() || !allow(holder, VKCacheCommandType.WRITE)) {
            return;
        }
        Map<String, byte[]> encoded = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = realKey(entry.getKey());
            Object value = entry.getValue();
            encoded.put(key, value == null ? NULL_MARKER : holder.codec.encode(value));
            if (value != null) {
                holder.bloomFilter.put(key);
            }
        }
        String keyHint = encoded.keySet().iterator().next();
        execute(holder, VKCacheCommandType.WRITE, keyHint, client -> {
            client.mset(encoded);
            long expire = ttlMs == null ? 0 : Math.max(0, ttlMs);
            if (expire > 0) {
                long jitterTtl = applyTtlWithJitter(holder.config, expire);
                for (String key : encoded.keySet()) {
                    client.expire(key, jitterTtl);
                }
            }
            return null;
        });
    }

    public long hset(String key, String field, Object value) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key),
                client -> client.hset(realKey(key), field, holder.codec.encode(value)));
    }

    public <T> T hget(String key, String field, Class<T> type) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return null;
        }
        byte[] payload = execute(holder, VKCacheCommandType.READ, realKey(key),
                client -> client.hget(realKey(key), field));
        return decodeValue(holder.codec, payload, type);
    }

    public <T> Map<String, T> hgetAll(String key, Class<T> type) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return Map.of();
        }
        Map<String, byte[]> raw = execute(holder, VKCacheCommandType.READ, realKey(key),
                client -> client.hgetAll(realKey(key)));
        Map<String, T> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : raw.entrySet()) {
            out.put(entry.getKey(), decodeValue(holder.codec, entry.getValue(), type));
        }
        return out;
    }

    public long hdel(String key, String... fields) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key),
                client -> client.hdel(realKey(key), fields));
    }

    public long lpush(String key, Object... values) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        byte[][] arr = new byte[values == null ? 0 : values.length][];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = holder.codec.encode(values[i]);
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key),
                client -> client.lpush(realKey(key), arr));
    }

    public <T> List<T> lrange(String key, long start, long stop, Class<T> type) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return List.of();
        }
        List<byte[]> raw = execute(holder, VKCacheCommandType.READ, realKey(key),
                client -> client.lrange(realKey(key), start, stop));
        List<T> out = new ArrayList<>();
        for (byte[] bytes : raw) {
            out.add(decodeValue(holder.codec, bytes, type));
        }
        return out;
    }

    public long sadd(String key, Object... members) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        byte[][] arr = new byte[members == null ? 0 : members.length][];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = holder.codec.encode(members[i]);
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key),
                client -> client.sadd(realKey(key), arr));
    }

    public <T> Set<T> smembers(String key, Class<T> type) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return Set.of();
        }
        Set<byte[]> raw = execute(holder, VKCacheCommandType.READ, realKey(key),
                client -> client.smembers(realKey(key)));
        Set<T> out = new LinkedHashSet<>();
        for (byte[] bytes : raw) {
            out.add(decodeValue(holder.codec, bytes, type));
        }
        return out;
    }

    public long zadd(String key, double score, Object member) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.WRITE)) {
            return 0;
        }
        return execute(holder, VKCacheCommandType.WRITE, realKey(key),
                client -> client.zadd(realKey(key), score, holder.codec.encode(member)));
    }

    public <T> List<T> zrange(String key, long start, long stop, Class<T> type) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return List.of();
        }
        List<byte[]> raw = execute(holder, VKCacheCommandType.READ, realKey(key),
                client -> client.zrange(realKey(key), start, stop));
        List<T> out = new ArrayList<>();
        for (byte[] bytes : raw) {
            out.add(decodeValue(holder.codec, bytes, type));
        }
        return out;
    }

    public List<String> scan(String pattern, int count) {
        CacheHolder holder = currentHolder();
        if (!allow(holder, VKCacheCommandType.READ)) {
            return List.of();
        }
        String p = pattern == null || pattern.isBlank() ? "*" : pattern;
        return execute(holder, VKCacheCommandType.READ, null,
                client -> client.scan(realKey(p), Math.max(1, count)));
    }

    private boolean allow(CacheHolder holder, VKCacheCommandType commandType) {
        if (holder.pool.allowCommand()) {
            return true;
        }
        VKCacheDegradePolicy policy = holder.config.getDegradePolicy();
        if (policy == VKCacheDegradePolicy.RETURN_NULL && commandType == VKCacheCommandType.READ) {
            return false;
        }
        if (policy == VKCacheDegradePolicy.SKIP_WRITE && commandType == VKCacheCommandType.WRITE) {
            return false;
        }
        throw new VKCacheException(VKCacheErrorCode.TIMEOUT,
                "Cache command rejected by rate limiter");
    }

    private long applyTtlWithJitter(VKCacheConfig config, long ttlMs) {
        if (ttlMs <= 0) {
            return 0;
        }
        long jitter = Math.max(0, config.getTtlJitterMs());
        if (jitter <= 0) {
            return ttlMs;
        }
        long delta = ThreadLocalRandom.current().nextLong(jitter + 1);
        return Math.max(1, ttlMs + delta);
    }

    private <T> T decodeValue(VKCacheCodec codec, byte[] payload, Class<T> type) {
        if (payload == null || isNullMarker(payload)) {
            return null;
        }
        return codec.decode(payload, type);
    }

    private boolean isNullMarker(byte[] bytes) {
        if (bytes.length != NULL_MARKER.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != NULL_MARKER[i]) {
                return false;
            }
        }
        return true;
    }

    private String realKey(String key) {
        if (key == null || key.isBlank()) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Cache key is blank");
        }
        CacheHolder holder = currentHolder();
        String prefix = holder.config.getKeyPrefix();
        if (prefix == null || prefix.isBlank() || key.startsWith(prefix)) {
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

    private <T> T execute(CacheHolder holder, VKCacheCommandType commandType, String keyHint, CacheAction<T> action) {
        int attempts = holder.config.isRetryEnabled() ? Math.max(1, holder.config.getMaxRetries() + 1) : 1;
        RuntimeException last = null;

        for (int i = 0; i < attempts; i++) {
            VKCacheClient client = holder.pool.borrow();
            try {
                return action.run(client);
            } catch (RuntimeException e) {
                last = e;
                if (i + 1 >= attempts || !allow(holder, commandType)) {
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
        if (config.isRetryJitterEnabled()) {
            delay += ThreadLocalRandom.current().nextLong(Math.max(1, delay / 2 + 1));
            delay = Math.min(max, delay);
        }
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
        VKBloomFilter bloomFilter = config.getBloomFilter() == null ? VKBloomFilter.noOp() : config.getBloomFilter();
        return new CacheHolder(config.copy(), provider, pool, codec, bloomFilter,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
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
                               VKCacheCodec codec,
                               VKBloomFilter bloomFilter,
                               ConcurrentHashMap<String, ReentrantLock> keyLocks,
                               ConcurrentHashMap<String, CompletableFuture<Object>> singleFlight) {
        private void close() {
            try {
                pool.close();
            } finally {
                provider.close();
            }
            keyLocks.clear();
            singleFlight.clear();
        }
    }
}
