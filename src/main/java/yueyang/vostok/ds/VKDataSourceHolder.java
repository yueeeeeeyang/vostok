package yueyang.vostok.ds;

import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.jdbc.VKSqlLogger;
import yueyang.vostok.jdbc.VKSqlMetrics;
import yueyang.vostok.jdbc.JdbcExecutor;
import yueyang.vostok.jdbc.VKRetryPolicy;
import yueyang.vostok.pool.VKDataSource;
import yueyang.vostok.sql.SqlTemplateCache;

/**
 * 数据源持有者。
 */
public class VKDataSourceHolder {
    private final String name;
    private final DataSourceConfig config;
    private final VKDataSource dataSource;
    private final JdbcExecutor executor;
    private final VKSqlLogger sqlLogger;
    private final VKSqlMetrics sqlMetrics;
    private final VKRetryPolicy retryPolicy;
    private final SqlTemplateCache templateCache;

    public VKDataSourceHolder(String name, DataSourceConfig config) {
        this.name = name;
        this.config = config;
        this.dataSource = new VKDataSource(config);
        this.sqlLogger = new VKSqlLogger(config);
        this.sqlMetrics = new VKSqlMetrics(config);
        this.retryPolicy = new VKRetryPolicy(config);
        this.templateCache = new SqlTemplateCache(config.getSqlTemplateCacheSize());
        this.executor = new JdbcExecutor(dataSource, sqlLogger, sqlMetrics, retryPolicy);
    }

    
    public String getName() {
        return name;
    }

    
    public DataSourceConfig getConfig() {
        return config;
    }

    
    public VKDataSource getDataSource() {
        return dataSource;
    }

    
    public JdbcExecutor getExecutor() {
        return executor;
    }

    
    public VKSqlLogger getSqlLogger() {
        return sqlLogger;
    }

    
    public VKSqlMetrics getSqlMetrics() {
        return sqlMetrics;
    }

    
    public VKRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    
    public SqlTemplateCache getTemplateCache() {
        return templateCache;
    }

    
    public void close() {
        dataSource.close();
    }
}
