package yueyang.vostok.sql;

import yueyang.vostok.meta.EntityMeta;
import yueyang.vostok.util.VKAssert;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL 模板 LRU 缓存（按实体 + 类型）。
 */
public class SqlTemplateCache {
    private final int maxSize;
    private final Map<String, SqlTemplate> cache;

    public SqlTemplateCache(int maxSize) {
        this.maxSize = Math.max(0, maxSize);
        this.cache = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            
            protected boolean removeEldestEntry(Map.Entry<String, SqlTemplate> eldest) {
                return SqlTemplateCache.this.maxSize > 0 && size() > SqlTemplateCache.this.maxSize;
            }
        };
    }

    
    public SqlTemplate get(EntityMeta meta, SqlTemplateType type) {
        VKAssert.notNull(meta, "EntityMeta is null");
        VKAssert.notNull(type, "SqlTemplateType is null");
        String key = meta.getEntityClass().getName() + ":" + type.name();
        synchronized (cache) {
            SqlTemplate template = cache.get(key);
            if (template != null) {
                return template;
            }
        }
        SqlTemplate built = SqlTemplateBuilder.build(meta, type);
        synchronized (cache) {
            cache.put(key, built);
        }
        return built;
    }

    
    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    
    public int getMaxSize() {
        return maxSize;
    }
}
