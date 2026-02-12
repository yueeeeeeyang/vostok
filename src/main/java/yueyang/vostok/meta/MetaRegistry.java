package yueyang.vostok.meta;

import yueyang.vostok.annotation.VKEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MetaRegistry {
    private static volatile Map<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();
    private static volatile long lastRefreshAt = 0L;

    private MetaRegistry() {
    }

    public static void register(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(VKEntity.class)) {
            return;
        }
        Map<Class<?>, EntityMeta> cache = CACHE;
        cache.computeIfAbsent(clazz, MetaLoader::load);
    }

    public static EntityMeta get(Class<?> clazz) {
        Map<Class<?>, EntityMeta> cache = CACHE;
        EntityMeta meta = cache.get(clazz);
        if (meta == null) {
            meta = MetaLoader.load(clazz);
            cache.put(clazz, meta);
        }
        return meta;
    }

    public static void refresh(Class<?> clazz) {
        if (clazz == null || !clazz.isAnnotationPresent(VKEntity.class)) {
            return;
        }
        Map<Class<?>, EntityMeta> newCache = new ConcurrentHashMap<>(CACHE);
        newCache.put(clazz, MetaLoader.load(clazz));
        CACHE = newCache;
        lastRefreshAt = System.currentTimeMillis();
    }

    public static void refreshAll(Iterable<Class<?>> classes) {
        Map<Class<?>, EntityMeta> newCache = new ConcurrentHashMap<>();
        for (Class<?> clazz : classes) {
            if (clazz != null && clazz.isAnnotationPresent(VKEntity.class)) {
                newCache.put(clazz, MetaLoader.load(clazz));
            }
        }
        CACHE = newCache;
        lastRefreshAt = System.currentTimeMillis();
    }

    public static int size() {
        return CACHE.size();
    }

    public static List<EntityMeta> all() {
        return new ArrayList<>(CACHE.values());
    }

    public static long getLastRefreshAt() {
        return lastRefreshAt;
    }
}
