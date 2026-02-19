package yueyang.vostok.cache;

import yueyang.vostok.cache.codec.VKCacheCodec;
import yueyang.vostok.cache.codec.VKCacheCodecs;
import yueyang.vostok.cache.core.VKCacheRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class VostokCache {
    private static final VKCacheRuntime RUNTIME = VKCacheRuntime.getInstance();

    protected VostokCache() {
    }

    public static void init(VKCacheConfig config) {
        RUNTIME.init(config);
    }

    public static void init(VKCacheConfigLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("VKCacheConfigLoader is null");
        }
        RUNTIME.init(loader.load());
    }

    public static void initFromEnv(String prefix) {
        init(() -> VKCacheConfigFactory.fromEnv(prefix));
    }

    public static void initFromProperties(Path path, String prefix) {
        init(() -> VKCacheConfigFactory.fromProperties(path, prefix));
    }

    public static void reinit(VKCacheConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKCacheConfig config() {
        return RUNTIME.config();
    }

    public static void close() {
        RUNTIME.close();
    }

    public static void registerCache(String name, VKCacheConfig config) {
        RUNTIME.registerCache(name, config);
    }

    public static void withCache(String name, Runnable action) {
        RUNTIME.withCache(name, action);
    }

    public static <T> T withCache(String name, Supplier<T> supplier) {
        return RUNTIME.withCache(name, supplier);
    }

    public static String currentCacheName() {
        return RUNTIME.currentCacheName();
    }

    public static Set<String> cacheNames() {
        return RUNTIME.cacheNames();
    }

    public static void registerCodec(VKCacheCodec codec) {
        VKCacheCodecs.register(codec);
    }

    public static void set(String key, Object value) {
        RUNTIME.set(key, value, null);
    }

    public static void set(String key, Object value, long ttlMs) {
        RUNTIME.set(key, value, ttlMs);
    }

    public static String get(String key) {
        return RUNTIME.get(key, String.class);
    }

    public static <T> T get(String key, Class<T> type) {
        return RUNTIME.get(key, type);
    }

    public static long delete(String... keys) {
        return RUNTIME.delete(keys);
    }

    public static boolean exists(String key) {
        return RUNTIME.exists(key);
    }

    public static boolean expire(String key, long ttlMs) {
        return RUNTIME.expire(key, ttlMs);
    }

    public static long incr(String key) {
        return RUNTIME.incrBy(key, 1);
    }

    public static long incrBy(String key, long delta) {
        return RUNTIME.incrBy(key, delta);
    }

    public static long decr(String key) {
        return RUNTIME.incrBy(key, -1);
    }

    public static long decrBy(String key, long delta) {
        return RUNTIME.incrBy(key, -Math.abs(delta));
    }

    public static <T> List<T> mget(Class<T> type, String... keys) {
        return RUNTIME.mget(type, keys);
    }

    public static void mset(Map<String, ?> values) {
        RUNTIME.mset(values, null);
    }

    public static void mset(Map<String, ?> values, long ttlMs) {
        RUNTIME.mset(values, ttlMs);
    }
}
