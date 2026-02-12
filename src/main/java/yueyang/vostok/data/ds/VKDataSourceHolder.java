package yueyang.vostok.data.ds;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialect;
import yueyang.vostok.data.dialect.VKDialectManager;
import yueyang.vostok.data.jdbc.JdbcExecutor;
import yueyang.vostok.data.jdbc.VKRetryPolicy;
import yueyang.vostok.data.jdbc.VKSqlLogger;
import yueyang.vostok.data.jdbc.VKSqlMetrics;
import yueyang.vostok.data.pool.VKDataSource;

/**
 * 数据源持有者。
 */
public class VKDataSourceHolder {
    private final String name;
    private final VKDataConfig config;
    private final VKDataSource dataSource;
    private final JdbcExecutor executor;
    private final VKSqlLogger sqlLogger;
    private final VKSqlMetrics sqlMetrics;
    private final VKRetryPolicy retryPolicy;
    private final VKDialect dialect;

    public VKDataSourceHolder(String name, VKDataConfig config) {
        this.name = name;
        this.config = config;
        this.dataSource = new VKDataSource(config);
        this.sqlLogger = new VKSqlLogger(config);
        this.sqlMetrics = new VKSqlMetrics(config);
        this.retryPolicy = new VKRetryPolicy(config);
        this.dialect = VKDialectManager.resolve(config);
        this.executor = new JdbcExecutor(dataSource, sqlLogger, sqlMetrics, retryPolicy);
    }

    
    public String getName() {
        return name;
    }

    
    public VKDataConfig getConfig() {
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

    public VKDialect getDialect() {
        return dialect;
    }
    
    public void close() {
        dataSource.close();
    }
}
