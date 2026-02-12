package yueyang.vostok.ds;

import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.util.VKAssert;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多数据源注册表。
 */
public final class VKDataSourceRegistry {
    private static final Map<String, VKDataSourceHolder> REGISTRY = new ConcurrentHashMap<>();
    private static volatile String defaultName = "default";

    private VKDataSourceRegistry() {
    }

    
    public static void register(String name, DataSourceConfig config) {
        VKAssert.notBlank(name, "DataSource name is blank");
        VKAssert.notNull(config, "DataSourceConfig is null");
        VKDataSourceHolder holder = new VKDataSourceHolder(name, config);
        VKDataSourceHolder existing = REGISTRY.putIfAbsent(name, holder);
        if (existing != null) {
            holder.close();
            throw new yueyang.vostok.exception.VKConfigException("DataSource name already exists: " + name);
        }
    }

    
    public static VKDataSourceHolder get(String name) {
        VKDataSourceHolder holder = REGISTRY.get(name);
        if (holder == null) {
            throw new yueyang.vostok.exception.VKConfigException("DataSource not found: " + name);
        }
        return holder;
    }

    
    public static VKDataSourceHolder getDefault() {
        return get(defaultName);
    }

    
    public static String getDefaultName() {
        return defaultName;
    }

    
    public static void setDefaultName(String name) {
        VKAssert.notBlank(name, "Default data source name is blank");
        defaultName = name;
    }

    /**
     * @return 数据源只读视图（不暴露 Holder）
     */
    public static Map<String, VKDataSourceInfo> all() {
        Map<String, VKDataSourceInfo> snapshot = new HashMap<>();
        for (Map.Entry<String, VKDataSourceHolder> entry : REGISTRY.entrySet()) {
            VKDataSourceHolder holder = entry.getValue();
            snapshot.put(entry.getKey(), new VKDataSourceInfo(holder.getName(), holder.getConfig()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * @return 内部使用的 Holder 视图
     */
    public static Map<String, VKDataSourceHolder> allHolders() {
        return REGISTRY;
    }

    
    public static void clear() {
        for (VKDataSourceHolder holder : REGISTRY.values()) {
            holder.close();
        }
        REGISTRY.clear();
    }
}
