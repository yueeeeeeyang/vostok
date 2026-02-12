package yueyang.vostok.data.ds;

import yueyang.vostok.data.VKDataConfig;

/**
 * 数据源只读信息。
 */
public final class VKDataSourceInfo {
    private final String name;
    private final VKDataConfig config;

    public VKDataSourceInfo(String name, VKDataConfig config) {
        this.name = name;
        this.config = config;
    }

    
    public String getName() {
        return name;
    }

    
    public VKDataConfig getConfig() {
        return config;
    }
}
