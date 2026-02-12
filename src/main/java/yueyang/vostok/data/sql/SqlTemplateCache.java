package yueyang.vostok.data.sql;

import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.util.VKAssert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL 模板 LRU 缓存（按实体 + 类型）。
 */
public class SqlTemplateCache {
    private final int maxSize;
    private final ConcurrentHashMap<String, SqlTemplate> cache;
    private final ConcurrentLinkedQueue<String> accessQueue;
    private final AtomicInteger size;

    public SqlTemplateCache(int maxSize) {
        this.maxSize = Math.max(0, maxSize);
        this.cache = new ConcurrentHashMap<>(128);
        this.accessQueue = new ConcurrentLinkedQueue<>();
        this.size = new AtomicInteger(0);
    }

    
    public SqlTemplate get(EntityMeta meta, SqlTemplateType type) {
        VKAssert.notNull(meta, "EntityMeta is null");
        VKAssert.notNull(type, "SqlTemplateType is null");
        String key = meta.getEntityClass().getName() + ":" + type.name();
        SqlTemplate template = cache.get(key);
        if (template != null) {
            return template;
        }
        SqlTemplate built = SqlTemplateBuilder.build(meta, type);
        SqlTemplate existing = cache.putIfAbsent(key, built);
        if (existing != null) {
            return existing;
        }
        accessQueue.offer(key);
        size.incrementAndGet();
        evictIfNeeded();
        return built;
    }

    private void evictIfNeeded() {
        if (maxSize <= 0) {
            return;
        }
        while (size.get() > maxSize) {
            String key = accessQueue.poll();
            if (key == null) {
                return;
            }
            if (cache.remove(key) != null) {
                size.decrementAndGet();
            }
        }
    }

    
    public int size() {
        return cache.size();
    }

    
    public void clear() {
        cache.clear();
        accessQueue.clear();
        size.set(0);
    }

    
    public int getMaxSize() {
        return maxSize;
    }
}
