package yueyang.vostok.data.pool;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.util.VKAssert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class VKDataSource {
    private final VKConnectionPool pool;
    private final DataSource externalDataSource;
    private final VKDataConfig config;
    private final boolean closeExternalDataSource;

    public VKDataSource(VKDataConfig config) {
        VKAssert.notNull(config, "VKDataConfig is null");
        this.config = config;
        this.closeExternalDataSource = config.isCloseExternalDataSource();
        if (config.getExternalDataSource() != null) {
            this.externalDataSource = config.getExternalDataSource();
            this.pool = null;
        } else {
            this.pool = new VKConnectionPool(config);
            this.externalDataSource = null;
        }
    }

    public Connection getConnection() throws SQLException {
        if (pool != null) {
            Connection raw = pool.borrow();
            return VKPooledConnection.wrap(raw, pool, pool.getConfig().getStatementCacheSize());
        }
        return externalDataSource.getConnection();
    }

    public int getIdleCount() {
        if (pool == null) {
            return -1;
        }
        return pool.getIdleCount();
    }

    public int getActiveCount() {
        if (pool == null) {
            return -1;
        }
        return pool.getActiveCount();
    }

    public int getTotalCount() {
        if (pool == null) {
            return -1;
        }
        return pool.getTotalCount();
    }

    public void close() {
        if (pool != null) {
            pool.close();
            return;
        }
        if (!closeExternalDataSource || externalDataSource == null) {
            return;
        }
        try {
            if (externalDataSource instanceof AutoCloseable) {
                ((AutoCloseable) externalDataSource).close();
            }
        } catch (Exception e) {
            throw new yueyang.vostok.data.exception.VKStateException(
                    yueyang.vostok.data.exception.VKErrorCode.POOL_ERROR,
                    "Failed to close externalDataSource", e);
        }
    }

    public String getLastLeakStack() {
        if (pool == null) {
            return null;
        }
        return pool.getLastLeakStack();
    }

    public VKDataConfig getConfig() {
        return config;
    }
}
