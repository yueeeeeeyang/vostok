package yueyang.vostok.data.jdbc;

import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.data.plugin.VKInterceptor;
import yueyang.vostok.data.plugin.VKInterceptorRegistry;
import yueyang.vostok.data.pool.VKDataSource;
import yueyang.vostok.data.tx.VKTransactionManager;
import yueyang.vostok.data.type.VKTypeMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcExecutor {
    private final VKDataSource dataSource;
    private final VKSqlLogger sqlLogger;
    private final VKSqlMetrics sqlMetrics;
    private final VKRetryPolicy retryPolicy;

    public JdbcExecutor(VKDataSource dataSource, VKSqlLogger sqlLogger, VKSqlMetrics sqlMetrics, VKRetryPolicy retryPolicy) {
        this.dataSource = dataSource;
        this.sqlLogger = sqlLogger;
        this.sqlMetrics = sqlMetrics;
        this.retryPolicy = retryPolicy;
    }

    public int executeUpdate(String sql, Object[] params) throws SQLException {
        if (!isMonitoringEnabled()) {
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                applyQueryTimeout(ps);
                bindParams(ps, params);
                return ps.executeUpdate();
            } finally {
                holder.closeIfNeeded();
            }
        }
        long start = System.currentTimeMillis();
        sqlLogger.logSql(sql, params);
        before(sql, params);
        boolean success = false;
        Throwable error = null;
        ConnectionHolder holder = getConnection();
        try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
            applyQueryTimeout(ps);
            bindParams(ps, params);
            int count = ps.executeUpdate();
            success = true;
            return count;
        } catch (SQLException e) {
            error = e;
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            sqlLogger.logSlow(sql, params, cost);
            sqlMetrics.record(sql, params, cost);
            after(sql, params, cost, success, error);
            holder.closeIfNeeded();
        }
    }

    public VKBatchResult executeBatch(String sql, List<Object[]> paramsList, boolean returnKeys) throws SQLException {
        if (!isMonitoringEnabled()) {
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = returnKeys
                    ? holder.conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
                    : holder.conn.prepareStatement(sql)) {
                applyQueryTimeout(ps);
                if (paramsList != null) {
                    for (Object[] params : paramsList) {
                        bindParams(ps, params);
                        ps.addBatch();
                    }
                }
                int[] counts = ps.executeBatch();
                List<Object> keys = null;
                if (returnKeys) {
                    keys = new ArrayList<>();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        while (rs.next()) {
                            keys.add(rs.getObject(1));
                        }
                    }
                }
                return new VKBatchResult(counts, keys);
            } finally {
                holder.closeIfNeeded();
            }
        }
        long start = System.currentTimeMillis();
        sqlLogger.logSql(sql, null);
        before(sql, null);
        boolean success = false;
        Throwable error = null;
        ConnectionHolder holder = getConnection();
        try (PreparedStatement ps = returnKeys
                ? holder.conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
                : holder.conn.prepareStatement(sql)) {
            applyQueryTimeout(ps);
            if (paramsList != null) {
                for (Object[] params : paramsList) {
                    bindParams(ps, params);
                    ps.addBatch();
                }
            }
            int[] counts = ps.executeBatch();
            List<Object> keys = null;
            if (returnKeys) {
                keys = new ArrayList<>();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    while (rs.next()) {
                        keys.add(rs.getObject(1));
                    }
                }
            }
            success = true;
            return new VKBatchResult(counts, keys);
        } catch (SQLException e) {
            error = e;
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            sqlLogger.logSlow(sql, null, cost);
            sqlMetrics.record(sql, null, cost);
            after(sql, null, cost, success, error);
            holder.closeIfNeeded();
        }
    }

    public VKBatchDetailResult executeBatchDetailedFallback(String sql, List<Object[]> paramsList, boolean returnKeys) throws SQLException {
        List<VKBatchItemResult> items = new ArrayList<>();
        ConnectionHolder holder = getConnection();
        boolean monitor = isMonitoringEnabled();
        try {
            for (int i = 0; i < paramsList.size(); i++) {
                Object[] params = paramsList.get(i);
                final int rowIndex = i;
                long start = monitor ? System.currentTimeMillis() : 0L;
                if (monitor) {
                    before(sql, params);
                }
                boolean success = false;
                Throwable error = null;
                try (PreparedStatement ps = returnKeys
                        ? holder.conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
                        : holder.conn.prepareStatement(sql)) {
                    applyQueryTimeout(ps);
                    bindParams(ps, params);
                    int count = ps.executeUpdate();
                    Object key = null;
                    if (returnKeys) {
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                key = rs.getObject(1);
                            }
                        }
                    }
                    items.add(new VKBatchItemResult(rowIndex, true, count, key, null));
                    success = true;
                } catch (SQLException e) {
                    error = e;
                    items.add(new VKBatchItemResult(rowIndex, false, 0, null, e.getMessage()));
                } finally {
                    if (monitor) {
                        long cost = System.currentTimeMillis() - start;
                        sqlMetrics.record(sql, params, cost);
                        after(sql, params, cost, success, error);
                    }
                }
            }
        } finally {
            holder.closeIfNeeded();
        }
        return new VKBatchDetailResult(items);
    }

    public Object executeInsertReturnKey(String sql, Object[] params) throws SQLException {
        if (!isMonitoringEnabled()) {
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = holder.conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                applyQueryTimeout(ps);
                bindParams(ps, params);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    return rs.next() ? rs.getObject(1) : null;
                }
            } finally {
                holder.closeIfNeeded();
            }
        }
        long start = System.currentTimeMillis();
        sqlLogger.logSql(sql, params);
        before(sql, params);
        boolean success = false;
        Throwable error = null;
        ConnectionHolder holder = getConnection();
        try (PreparedStatement ps = holder.conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            applyQueryTimeout(ps);
            bindParams(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    success = true;
                    return rs.getObject(1);
                }
                success = true;
                return null;
            }
        } catch (SQLException e) {
            error = e;
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            sqlLogger.logSlow(sql, params, cost);
            sqlMetrics.record(sql, params, cost);
            after(sql, params, cost, success, error);
            holder.closeIfNeeded();
        }
    }

    public <T> T queryOne(EntityMeta meta, String sql, Object[] params) throws SQLException {
        return withRetry(sql, params, () -> {
            if (!isMonitoringEnabled()) {
                ConnectionHolder holder = getConnection();
                try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                    applyQueryTimeout(ps);
                    bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        int[] indexes = resolveColumnIndexes(rs, meta.getFields());
                        return mapRow(meta, meta.getFields(), rs, indexes);
                    }
                } finally {
                    holder.closeIfNeeded();
                }
            }
            long start = System.currentTimeMillis();
            sqlLogger.logSql(sql, params);
            before(sql, params);
            boolean success = false;
            Throwable error = null;
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                applyQueryTimeout(ps);
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        success = true;
                        return null;
                    }
                    int[] indexes = resolveColumnIndexes(rs, meta.getFields());
                    T row = mapRow(meta, meta.getFields(), rs, indexes);
                    success = true;
                    return row;
                }
            } catch (SQLException e) {
                error = e;
                throw e;
            } finally {
                long cost = System.currentTimeMillis() - start;
                sqlLogger.logSlow(sql, params, cost);
                sqlMetrics.record(sql, params, cost);
                after(sql, params, cost, success, error);
                holder.closeIfNeeded();
            }
        });
    }

    public <T> List<T> queryList(EntityMeta meta, String sql, Object[] params) throws SQLException {
        return queryList(meta, meta.getFields(), sql, params);
    }

    public <T> List<T> queryList(EntityMeta meta, List<FieldMeta> projection, String sql, Object[] params) throws SQLException {
        return withRetry(sql, params, () -> {
            if (!isMonitoringEnabled()) {
                ConnectionHolder holder = getConnection();
                try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                    applyQueryTimeout(ps);
                    bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        return mapRows(meta, projection, rs);
                    }
                } finally {
                    holder.closeIfNeeded();
                }
            }
            long start = System.currentTimeMillis();
            sqlLogger.logSql(sql, params);
            before(sql, params);
            boolean success = false;
            Throwable error = null;
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                applyQueryTimeout(ps);
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> list = mapRows(meta, projection, rs);
                    success = true;
                    return list;
                }
            } catch (SQLException e) {
                error = e;
                throw e;
            } finally {
                long cost = System.currentTimeMillis() - start;
                sqlLogger.logSlow(sql, params, cost);
                sqlMetrics.record(sql, params, cost);
                after(sql, params, cost, success, error);
                holder.closeIfNeeded();
            }
        });
    }

    public List<Object[]> queryRows(String sql, Object[] params) throws SQLException {
        return withRetry(sql, params, () -> {
            if (!isMonitoringEnabled()) {
                ConnectionHolder holder = getConnection();
                try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                    applyQueryTimeout(ps);
                    bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<Object[]> rows = new ArrayList<>();
                        int cols = rs.getMetaData().getColumnCount();
                        while (rs.next()) {
                            Object[] row = new Object[cols];
                            for (int i = 0; i < cols; i++) {
                                row[i] = rs.getObject(i + 1);
                            }
                            rows.add(row);
                        }
                        return rows;
                    }
                } finally {
                    holder.closeIfNeeded();
                }
            }
            long start = System.currentTimeMillis();
            sqlLogger.logSql(sql, params);
            before(sql, params);
            boolean success = false;
            Throwable error = null;
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                applyQueryTimeout(ps);
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Object[]> rows = new ArrayList<>();
                    int cols = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        Object[] row = new Object[cols];
                        for (int i = 0; i < cols; i++) {
                            row[i] = rs.getObject(i + 1);
                        }
                        rows.add(row);
                    }
                    success = true;
                    return rows;
                }
            } catch (SQLException e) {
                error = e;
                throw e;
            } finally {
                long cost = System.currentTimeMillis() - start;
                sqlLogger.logSlow(sql, params, cost);
                sqlMetrics.record(sql, params, cost);
                after(sql, params, cost, success, error);
                holder.closeIfNeeded();
            }
        });
    }

    public Object queryScalar(String sql, Object[] params) throws SQLException {
        return withRetry(sql, params, () -> {
            if (!isMonitoringEnabled()) {
                ConnectionHolder holder = getConnection();
                try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                    applyQueryTimeout(ps);
                    bindParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getObject(1) : null;
                    }
                } finally {
                    holder.closeIfNeeded();
                }
            }
            long start = System.currentTimeMillis();
            sqlLogger.logSql(sql, params);
            before(sql, params);
            boolean success = false;
            Throwable error = null;
            ConnectionHolder holder = getConnection();
            try (PreparedStatement ps = holder.conn.prepareStatement(sql)) {
                applyQueryTimeout(ps);
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        success = true;
                        return rs.getObject(1);
                    }
                    success = true;
                    return null;
                }
            } catch (SQLException e) {
                error = e;
                throw e;
            } finally {
                long cost = System.currentTimeMillis() - start;
                sqlLogger.logSlow(sql, params, cost);
                sqlMetrics.record(sql, params, cost);
                after(sql, params, cost, success, error);
                holder.closeIfNeeded();
            }
        });
    }

    private boolean isMonitoringEnabled() {
        return sqlLogger.isLogEnabled() || sqlLogger.isSlowEnabled() || sqlMetrics.isEnabled() || VKInterceptorRegistry.hasAny();
    }

    private <T> T withRetry(String sql, Object[] params, SqlCallable<T> callable) throws SQLException {
        if (retryPolicy == null || !retryPolicy.isEnabled()) {
            return callable.call();
        }
        int attempt = 0;
        while (true) {
            try {
                return callable.call();
            } catch (SQLException e) {
                if (!retryPolicy.shouldRetry(e) || attempt >= retryPolicy.getMaxRetries()) {
                    throw e;
                }
                attempt++;
                long backoff = retryPolicy.backoffMs(attempt);
                if (backoff > 0) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
    }

    private void bindParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object v = VKTypeMapper.toJdbc(params[i]);
            int idx = i + 1;
            if (v == null) {
                ps.setObject(idx, null);
            } else if (v instanceof Integer) {
                ps.setInt(idx, (Integer) v);
            } else if (v instanceof Long) {
                ps.setLong(idx, (Long) v);
            } else if (v instanceof String) {
                ps.setString(idx, (String) v);
            } else if (v instanceof Boolean) {
                ps.setBoolean(idx, (Boolean) v);
            } else if (v instanceof Short) {
                ps.setShort(idx, (Short) v);
            } else if (v instanceof Byte) {
                ps.setByte(idx, (Byte) v);
            } else if (v instanceof Float) {
                ps.setFloat(idx, (Float) v);
            } else if (v instanceof Double) {
                ps.setDouble(idx, (Double) v);
            } else if (v instanceof java.math.BigDecimal) {
                ps.setBigDecimal(idx, (java.math.BigDecimal) v);
            } else if (v instanceof java.sql.Timestamp) {
                ps.setTimestamp(idx, (java.sql.Timestamp) v);
            } else if (v instanceof java.sql.Date) {
                ps.setDate(idx, (java.sql.Date) v);
            } else {
                ps.setObject(idx, v);
            }
        }
    }

    private void applyQueryTimeout(PreparedStatement ps) throws SQLException {
        long timeoutMs = 0L;
        if (VKTransactionManager.inTransaction()) {
            timeoutMs = VKTransactionManager.remainingTimeoutMs();
        }
        if (timeoutMs <= 0) {
            timeoutMs = dataSource.getConfig().getQueryTimeoutMs();
        }
        if (timeoutMs > 0) {
            int sec = (int) Math.max(1, (timeoutMs + 999) / 1000);
            ps.setQueryTimeout(sec);
        }
    }

    /**
     * 将 ResultSet 映射为实体列表。
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> mapRows(EntityMeta meta, List<FieldMeta> projection, ResultSet rs) throws SQLException {
        List<T> list = new ArrayList<>();
        try {
            int[] indexes = resolveColumnIndexes(rs, projection);
            while (rs.next()) {
                list.add(mapRow(meta, projection, rs, indexes));
            }
        } catch (Exception e) {
            throw new SQLException("Failed to map result set", e);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private <T> T mapRow(EntityMeta meta, List<FieldMeta> projection, ResultSet rs, int[] indexes) throws SQLException {
        try {
            Object obj = meta.getEntityClass().getDeclaredConstructor().newInstance();
            for (int i = 0; i < projection.size(); i++) {
                FieldMeta field = projection.get(i);
                Object value = rs.getObject(indexes[i]);
                Object mapped = VKTypeMapper.fromJdbc(value, field.getField().getType());
                field.setValue(obj, mapped);
            }
            return (T) obj;
        } catch (Exception e) {
            throw new SQLException("Failed to map result set", e);
        }
    }

    private int[] resolveColumnIndexes(ResultSet rs, List<FieldMeta> projection) throws SQLException {
        int size = projection.size();
        int[] indexes = new int[size];
        for (int i = 0; i < size; i++) {
            String column = projection.get(i).getColumnName();
            try {
                indexes[i] = rs.findColumn(column);
            } catch (SQLException ignore) {
                indexes[i] = i + 1;
            }
        }
        return indexes;
    }

    private void before(String sql, Object[] params) {
        for (VKInterceptor interceptor : VKInterceptorRegistry.all()) {
            interceptor.beforeExecute(sql, params);
        }
    }

    private void after(String sql, Object[] params, long costMs, boolean success, Throwable error) {
        for (VKInterceptor interceptor : VKInterceptorRegistry.all()) {
            interceptor.afterExecute(sql, params, costMs, success, error);
        }
    }

    private ConnectionHolder getConnection() throws SQLException {
        if (VKTransactionManager.inTransaction()) {
            return new ConnectionHolder(VKTransactionManager.getConnection(), true);
        }
        return new ConnectionHolder(dataSource.getConnection(), false);
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws SQLException;
    }

    private static class ConnectionHolder {
        private final Connection conn;
        private final boolean inTx;

        private ConnectionHolder(Connection conn, boolean inTx) {
            this.conn = conn;
            this.inTx = inTx;
        }

        private void closeIfNeeded() throws SQLException {
            if (!inTx && conn != null) {
                conn.close();
            }
        }
    }
}
