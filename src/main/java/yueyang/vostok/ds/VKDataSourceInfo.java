package yueyang.vostok.ds;

import yueyang.vostok.config.DataSourceConfig;

/**
 * 数据源只读信息。
 */
public final class VKDataSourceInfo {
    private final String name;
    private final DataSourceConfig config;

    public VKDataSourceInfo(String name, DataSourceConfig config) {
        this.name = name;
        this.config = config;
    }

    
    public String getName() {
        return name;
    }

    
    public DataSourceConfig getConfig() {
        return config;
    }
}
