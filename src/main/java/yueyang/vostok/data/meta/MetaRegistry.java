package yueyang.vostok.data.meta;

import yueyang.vostok.data.annotation.VKEntity;
import yueyang.vostok.data.config.DataSourceConfig;
import yueyang.vostok.data.sql.SqlTemplateCache;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class MetaRegistry {
    private static final AtomicReference<MetaSnapshot> SNAPSHOT = new AtomicReference<>(MetaSnapshot.empty());

    private MetaRegistry() {
    }

    public static void register(Class<?> clazz) {
        if (!clazz.isAnnotationPresent(VKEntity.class)) {
            return;
        }
        while (true) {
            MetaSnapshot snapshot = SNAPSHOT.get();
            if (snapshot.metas.containsKey(clazz)) {
                return;
            }
            Map<Class<?>, EntityMeta> metas = new ConcurrentHashMap<>(snapshot.metas);
            metas.put(clazz, MetaLoader.load(clazz));
            MetaSnapshot updated = snapshot.withMetas(metas);
            if (SNAPSHOT.compareAndSet(snapshot, updated)) {
                return;
            }
        }
    }

    public static EntityMeta get(Class<?> clazz) {
        MetaSnapshot snapshot = SNAPSHOT.get();
        EntityMeta meta = snapshot.metas.get(clazz);
        if (meta != null) {
            return meta;
        }
        meta = MetaLoader.load(clazz);
        while (true) {
            MetaSnapshot current = SNAPSHOT.get();
            if (current.metas.containsKey(clazz)) {
                return current.metas.get(clazz);
            }
            Map<Class<?>, EntityMeta> metas = new ConcurrentHashMap<>(current.metas);
            metas.put(clazz, meta);
            MetaSnapshot updated = current.withMetas(metas);
            if (SNAPSHOT.compareAndSet(current, updated)) {
                return meta;
            }
        }
    }

    public static void refresh(Class<?> clazz) {
        if (clazz == null || !clazz.isAnnotationPresent(VKEntity.class)) {
            return;
        }
        MetaSnapshot snapshot = SNAPSHOT.get();
        Map<Class<?>, EntityMeta> metas = new ConcurrentHashMap<>(snapshot.metas);
        metas.put(clazz, MetaLoader.load(clazz));
        Map<String, SqlTemplateCache> caches = buildTemplateCaches(snapshot.dataSourceConfigs, null);
        SNAPSHOT.set(new MetaSnapshot(metas, snapshot.dataSourceConfigs, caches, System.currentTimeMillis()));
    }

    public static void refreshAll(Iterable<Class<?>> classes, Map<String, DataSourceConfig> dataSourceConfigs) {
        VKAssert.notNull(dataSourceConfigs, "dataSourceConfigs is null");
        Map<Class<?>, EntityMeta> metas = new ConcurrentHashMap<>();
        for (Class<?> clazz : classes) {
            if (clazz != null && clazz.isAnnotationPresent(VKEntity.class)) {
                metas.put(clazz, MetaLoader.load(clazz));
            }
        }
        Map<String, DataSourceConfig> configs = Collections.unmodifiableMap(new HashMap<>(dataSourceConfigs));
        Map<String, SqlTemplateCache> caches = buildTemplateCaches(configs, null);
        SNAPSHOT.set(new MetaSnapshot(metas, configs, caches, System.currentTimeMillis()));
    }

    public static int size() {
        return SNAPSHOT.get().metas.size();
    }

    public static List<EntityMeta> all() {
        return new ArrayList<>(SNAPSHOT.get().metas.values());
    }

    public static long getLastRefreshAt() {
        return SNAPSHOT.get().lastRefreshAt;
    }

    public static SqlTemplateCache getTemplateCache(String dataSourceName) {
        MetaSnapshot snapshot = SNAPSHOT.get();
        SqlTemplateCache cache = snapshot.templateCaches.get(dataSourceName);
        if (cache == null) {
            VKLog.warn("Template cache not found for dataSource: " + dataSourceName);
            return new SqlTemplateCache(0);
        }
        return cache;
    }

    public static void registerDataSource(String name, DataSourceConfig config) {
        VKAssert.notBlank(name, "dataSourceName is blank");
        VKAssert.notNull(config, "DataSourceConfig is null");
        while (true) {
            MetaSnapshot snapshot = SNAPSHOT.get();
            Map<String, DataSourceConfig> configs = new HashMap<>(snapshot.dataSourceConfigs);
            configs.put(name, config);
            Map<String, SqlTemplateCache> caches = buildTemplateCaches(Collections.unmodifiableMap(configs), snapshot.templateCaches);
            MetaSnapshot updated = new MetaSnapshot(snapshot.metas, Collections.unmodifiableMap(configs), caches, snapshot.lastRefreshAt);
            if (SNAPSHOT.compareAndSet(snapshot, updated)) {
                return;
            }
        }
    }

    public static void clear() {
        SNAPSHOT.set(MetaSnapshot.empty());
    }

    private static Map<String, SqlTemplateCache> buildTemplateCaches(Map<String, DataSourceConfig> configs,
                                                                     Map<String, SqlTemplateCache> existing) {
        Map<String, SqlTemplateCache> caches = new HashMap<>();
        if (existing != null) {
            caches.putAll(existing);
        }
        for (Map.Entry<String, DataSourceConfig> entry : configs.entrySet()) {
            if (!caches.containsKey(entry.getKey())) {
                caches.put(entry.getKey(), new SqlTemplateCache(entry.getValue().getSqlTemplateCacheSize()));
            }
        }
        return Collections.unmodifiableMap(caches);
    }

    private static final class MetaSnapshot {
        private static final MetaSnapshot EMPTY = new MetaSnapshot(new ConcurrentHashMap<>(), Collections.emptyMap(), Collections.emptyMap(), 0L);
        private final Map<Class<?>, EntityMeta> metas;
        private final Map<String, DataSourceConfig> dataSourceConfigs;
        private final Map<String, SqlTemplateCache> templateCaches;
        private final long lastRefreshAt;

        private MetaSnapshot(Map<Class<?>, EntityMeta> metas,
                             Map<String, DataSourceConfig> dataSourceConfigs,
                             Map<String, SqlTemplateCache> templateCaches,
                             long lastRefreshAt) {
            this.metas = metas;
            this.dataSourceConfigs = dataSourceConfigs;
            this.templateCaches = templateCaches;
            this.lastRefreshAt = lastRefreshAt;
        }

        private static MetaSnapshot empty() {
            return EMPTY;
        }

        private MetaSnapshot withMetas(Map<Class<?>, EntityMeta> metas) {
            return new MetaSnapshot(metas, dataSourceConfigs, templateCaches, lastRefreshAt);
        }
    }
}
