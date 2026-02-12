package yueyang.vostok.ds;

import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.util.VKAssert;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 多数据源注册表。
 */
public final class VKDataSourceRegistry {
    private static final Map<String, VKDataSourceHolder> REGISTRY = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static volatile String defaultName = "default";

    private VKDataSourceRegistry() {
    }

    
    public static void register(String name, DataSourceConfig config) {
        VKAssert.notBlank(name, "DataSource name is blank");
        VKAssert.notNull(config, "DataSourceConfig is null");
        LOCK.writeLock().lock();
        try {
            VKDataSourceHolder holder = new VKDataSourceHolder(name, config);
            VKDataSourceHolder existing = REGISTRY.putIfAbsent(name, holder);
            if (existing != null) {
                holder.close();
                throw new yueyang.vostok.exception.VKConfigException("DataSource name already exists: " + name);
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    
    public static VKDataSourceHolder get(String name) {
        LOCK.readLock().lock();
        try {
            VKDataSourceHolder holder = REGISTRY.get(name);
            if (holder == null) {
                throw new yueyang.vostok.exception.VKConfigException("DataSource not found: " + name);
            }
            return holder;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    
    public static VKDataSourceHolder getDefault() {
        return get(defaultName);
    }

    
    public static String getDefaultName() {
        LOCK.readLock().lock();
        try {
            return defaultName;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    
    public static void setDefaultName(String name) {
        VKAssert.notBlank(name, "Default data source name is blank");
        LOCK.writeLock().lock();
        try {
            defaultName = name;
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * @return 数据源只读视图（不暴露 Holder）
     */
    public static Map<String, VKDataSourceInfo> all() {
        LOCK.readLock().lock();
        try {
            Map<String, VKDataSourceInfo> snapshot = new HashMap<>();
            for (Map.Entry<String, VKDataSourceHolder> entry : REGISTRY.entrySet()) {
                VKDataSourceHolder holder = entry.getValue();
                snapshot.put(entry.getKey(), new VKDataSourceInfo(holder.getName(), holder.getConfig()));
            }
            return Collections.unmodifiableMap(snapshot);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * @return 内部使用的 Holder 视图
     */
    public static Map<String, VKDataSourceHolder> allHolders() {
        LOCK.readLock().lock();
        try {
            return Collections.unmodifiableMap(new HashMap<>(REGISTRY));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    
    public static void clear() {
        LOCK.writeLock().lock();
        try {
            for (VKDataSourceHolder holder : REGISTRY.values()) {
                holder.close();
            }
            REGISTRY.clear();
        } finally {
            LOCK.writeLock().unlock();
        }
    }
}
