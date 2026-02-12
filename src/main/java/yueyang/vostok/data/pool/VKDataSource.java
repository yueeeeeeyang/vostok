package yueyang.vostok.data.pool;

import yueyang.vostok.data.config.DataSourceConfig;
import yueyang.vostok.util.VKAssert;

import java.sql.Connection;
import java.sql.SQLException;

public class VKDataSource {
    private final VKConnectionPool pool;

    public VKDataSource(DataSourceConfig config) {
        VKAssert.notNull(config, "DataSourceConfig is null");
        this.pool = new VKConnectionPool(config);
    }

    public Connection getConnection() throws SQLException {
        Connection raw = pool.borrow();
        return VKPooledConnection.wrap(raw, pool, pool.getConfig().getStatementCacheSize());
    }

    public int getIdleCount() {
        return pool.getIdleCount();
    }

    public int getActiveCount() {
        return pool.getActiveCount();
    }

    public int getTotalCount() {
        return pool.getTotalCount();
    }

    public void close() {
        pool.close();
    }

    public String getLastLeakStack() {
        return pool.getLastLeakStack();
    }

    public DataSourceConfig getConfig() {
        return pool.getConfig();
    }
}
