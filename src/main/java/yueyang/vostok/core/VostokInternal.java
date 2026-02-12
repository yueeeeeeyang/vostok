package yueyang.vostok.core;

import yueyang.vostok.config.VKBatchFailStrategy;
import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.dialect.VKDialect;
import yueyang.vostok.ds.VKDataSourceHolder;
import yueyang.vostok.ds.VKDataSourceRegistry;
import yueyang.vostok.exception.VKErrorCode;
import yueyang.vostok.exception.VKException;
import yueyang.vostok.exception.VKExceptionTranslator;
import yueyang.vostok.exception.VKStateException;
import yueyang.vostok.jdbc.JdbcExecutor;
import yueyang.vostok.meta.EntityMeta;
import yueyang.vostok.meta.FieldMeta;
import yueyang.vostok.meta.MetaRegistry;
import yueyang.vostok.sql.SqlAndParams;
import yueyang.vostok.sql.SqlTemplateCache;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.util.VKLog;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Vostok 内部公共逻辑（包内共享）。
 */
final class VostokInternal {
    private VostokInternal() {
    }

    static void validateConfig(DataSourceConfig cfg) {
        VKAssert.notNull(cfg, "DataSourceConfig is null");
        VKAssert.notBlank(cfg.getUrl(), "jdbc url is blank");
        VKAssert.notBlank(cfg.getUsername(), "username is blank");
        VKAssert.notBlank(cfg.getDriver(), "driver is blank");
        VKAssert.isTrue(cfg.getMinIdle() >= 0, "minIdle must be >= 0");
        VKAssert.isTrue(cfg.getMaxActive() > 0, "maxActive must be > 0");
        VKAssert.isTrue(cfg.getMinIdle() <= cfg.getMaxActive(), "minIdle must be <= maxActive");
        VKAssert.isTrue(cfg.getMaxWaitMs() > 0, "maxWaitMs must be > 0");
        VKAssert.isTrue(cfg.getBatchSize() > 0, "batchSize must be > 0");
        VKAssert.isTrue(cfg.getValidationTimeoutSec() > 0, "validationTimeoutSec must be > 0");
        VKAssert.isTrue(cfg.getIdleValidationIntervalMs() >= 0, "idleValidationIntervalMs must be >= 0");
        VKAssert.isTrue(cfg.getStatementCacheSize() >= 0, "statementCacheSize must be >= 0");
        VKAssert.isTrue(cfg.getSqlTemplateCacheSize() >= 0, "sqlTemplateCacheSize must be >= 0");
        VKAssert.isTrue(cfg.getSlowSqlTopN() >= 0, "slowSqlTopN must be >= 0");
        VKAssert.isTrue(cfg.getMaxRetries() >= 0, "maxRetries must be >= 0");
        VKAssert.isTrue(cfg.getRetryBackoffBaseMs() >= 0, "retryBackoffBaseMs must be >= 0");
        VKAssert.isTrue(cfg.getRetryBackoffMaxMs() >= 0, "retryBackoffMaxMs must be >= 0");
        VKAssert.isTrue(cfg.getTxTimeoutMs() >= 0, "txTimeoutMs must be >= 0");
        VKAssert.isTrue(cfg.getQueryTimeoutMs() >= 0, "queryTimeoutMs must be >= 0");
    }

    static void ensureInit() {
        if (!VostokRuntime.initialized) {
            throw new VKStateException(VKErrorCode.NOT_INITIALIZED, "Vostok is not initialized. Call Vostok.init(...) first.");
        }
    }

    static VKDataSourceHolder currentHolder() {
        String name = VostokRuntime.DS_CONTEXT.get();
        return (name == null) ? VKDataSourceRegistry.getDefault() : VKDataSourceRegistry.get(name);
    }

    static JdbcExecutor currentExecutor() {
        return currentHolder().getExecutor();
    }

    static SqlTemplateCache currentTemplateCache() {
        return MetaRegistry.getTemplateCache(currentDataSourceName());
    }

    static DataSourceConfig currentConfig() {
        return currentHolder().getConfig();
    }

    static VKDialect currentDialect() {
        return currentHolder().getDialect();
    }

    static String currentDataSourceName() {
        String name = VostokRuntime.DS_CONTEXT.get();
        return (name == null) ? VKDataSourceRegistry.getDefaultName() : name;
    }

    static int executeUpdate(SqlAndParams sp) {
        try {
            return currentExecutor().executeUpdate(sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    static Object executeInsert(SqlAndParams sp) {
        try {
            return currentExecutor().executeInsertReturnKey(sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T executeQueryOne(EntityMeta meta, SqlAndParams sp) {
        try {
            return (T) currentExecutor().queryOne(meta, sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> executeQueryList(EntityMeta meta, SqlAndParams sp) {
        try {
            return (List<T>) currentExecutor().queryList(meta, sp.getSql(), sp.getParams());
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sp.getSql(), e);
        }
    }

    static String buildDeleteInSql(EntityMeta meta, int size) {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ").append(meta.getTableName())
                .append(" WHERE ").append(meta.getIdField().getColumnName()).append(" IN (");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    static void handleBatchError(String message, SQLException e) {
        if (currentConfig().getBatchFailStrategy() == VKBatchFailStrategy.CONTINUE) {
            VKLog.warn(message + ", strategy=CONTINUE, err=" + e.getMessage());
            return;
        }
        throw VKExceptionTranslator.translate(message, e);
    }

    static String buildSqlMetricsReport(String dataSourceName) {
        VKDataSourceHolder holder = VKDataSourceRegistry.get(dataSourceName);
        var metrics = holder.getSqlMetrics();
        StringBuilder sb = new StringBuilder();
        long total = metrics.getTotalCount();
        long avg = total == 0 ? 0 : metrics.getTotalCost() / total;
        sb.append("  SqlMetrics total=").append(total)
                .append(" avgMs=").append(avg)
                .append(" maxMs=").append(metrics.getMaxCost())
                .append("\n");
        long[] buckets = metrics.getBuckets();
        long[] counts = metrics.getBucketCounts();
        sb.append("  Histogram: ");
        for (int i = 0; i < buckets.length; i++) {
            sb.append("<=").append(buckets[i]).append("ms=").append(counts[i]).append(" ");
        }
        sb.append(">").append(buckets[buckets.length - 1]).append("ms=").append(counts[counts.length - 1]).append("\n");
        var top = metrics.getSlowTopN();
        if (!top.isEmpty()) {
            sb.append("  SlowTopN:\n");
            for (var entry : top) {
                sb.append("    costMs=").append(entry.getCostMs())
                        .append(" sql=").append(entry.getSql());
                if (entry.getParams() != null) {
                    sb.append(" params=").append(entry.getParams());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    static <T> List<List<T>> split(List<T> list, int size) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<List<T>> chunks = new ArrayList<>();
        int total = list.size();
        for (int i = 0; i < total; i += size) {
            chunks.add(list.subList(i, Math.min(total, i + size)));
        }
        return chunks;
    }

    static void setGeneratedId(FieldMeta idField, Object entity, Object key) {
        Class<?> type = idField.getField().getType();
        Object converted = convertId(type, key);
        idField.setValue(entity, converted);
    }

    static void applyGeneratedKeys(FieldMeta idField, List<?> entities, List<Object> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        int size = Math.min(entities.size(), keys.size());
        for (int i = 0; i < size; i++) {
            Object key = keys.get(i);
            if (key != null) {
                setGeneratedId(idField, entities.get(i), key);
            }
        }
    }

    static Object convertId(Class<?> targetType, Object key) {
        if (key == null) {
            return null;
        }
        if (targetType.isAssignableFrom(key.getClass())) {
            return key;
        }
        if (targetType == Long.class || targetType == long.class) {
            return ((Number) key).longValue();
        }
        if (targetType == Integer.class || targetType == int.class) {
            return ((Number) key).intValue();
        }
        if (targetType == String.class) {
            return String.valueOf(key);
        }
        return key;
    }
}
