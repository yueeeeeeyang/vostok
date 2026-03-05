package yueyang.vostok.data.core;

import yueyang.vostok.data.DataResult;
import yueyang.vostok.data.config.VKBatchFailStrategy;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialect;
import yueyang.vostok.data.ds.VKDataSourceHolder;
import yueyang.vostok.data.ds.VKDataSourceRegistry;
import yueyang.vostok.data.exception.VKException;
import yueyang.vostok.data.exception.VKExceptionTranslator;
import yueyang.vostok.data.jdbc.JdbcExecutor;
import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.data.meta.MetaRegistry;
import yueyang.vostok.data.sql.SqlAndParams;
import yueyang.vostok.data.sql.SqlTemplateCache;
import yueyang.vostok.util.VKAssert;
import yueyang.vostok.Vostok;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Vostok 内部公共逻辑（包内共享）。
 */
final class VostokInternal {
    private VostokInternal() {
    }

    static void validateConfig(VKDataConfig cfg) {
        VKAssert.notNull(cfg, "VKDataConfig is null");
        boolean useExternalDataSource = cfg.getExternalDataSource() != null;
        if (!useExternalDataSource) {
            VKAssert.notBlank(cfg.getUrl(), "jdbc url is blank");
            VKAssert.notBlank(cfg.getUsername(), "username is blank");
            VKAssert.notBlank(cfg.getDriver(), "driver is blank");
        }
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
        if (cfg.isFieldEncryptionEnabled()) {
            VKAssert.notBlank(cfg.getDefaultEncryptionKeyId(), "defaultEncryptionKeyId is blank");
        }
    }

    static void ensureInit() {
        if (!VostokRuntime.initialized) {
            autoInitDefault();
        }
    }

    /**
     * 未显式初始化时，自动使用 H2 内存数据库初始化。
     * 扫描全 classpath 中的 @VKEntity 类，并自动建表（autoCreateTable=true）。
     * 生产环境应始终显式调用 Vostok.Data.init() 配置目标数据源。
     *
     * <p>线程安全：外层 synchronized(VostokRuntime.LOCK) 保证并发下只有一个线程执行初始化；
     * DCL（双重检查锁定）防止重复初始化。Java synchronized 对同一线程可重入，
     * 因此持锁调用 VostokBootstrap.init()（内部同样使用该锁）不会死锁。
     */
    private static void autoInitDefault() {
        if (VostokRuntime.initialized) {
            return;
        }
        synchronized (VostokRuntime.LOCK) {
            if (VostokRuntime.initialized) {
                return; // DCL：确保只初始化一次
            }
            Vostok.Log.warn("Vostok Data 未显式初始化，自动使用 H2 内存数据库启动 " +
                    "(jdbc:h2:mem:vostok_default;MODE=MySQL;DB_CLOSE_DELAY=-1)。" +
                    "生产环境请调用 Vostok.Data.init() 显式配置数据源。");
            VKDataConfig defaultConfig = new VKDataConfig()
                    .url("jdbc:h2:mem:vostok_default;MODE=MySQL;DB_CLOSE_DELAY=-1")
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .autoCreateTable(true)   // 自动建表，无需手工建表
                    .validationQuery("SELECT 1");
            // 复用上次显式初始化保留的包扫描范围（close() 不重置 initPackages）；
            // 若系统从未被显式初始化，initPackages 为空数组，回退到全 classpath 扫描。
            // VostokBootstrap.init() 持有同一把锁，synchronized 在同线程内可重入，安全
            VostokBootstrap.init(defaultConfig, VostokRuntime.initPackages);
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

    static VKDataConfig currentConfig() {
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

    static int executeUpdateRaw(String sql, Object[] params) {
        try {
            return currentExecutor().executeUpdate(sql, params);
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sql, e);
        }
    }

    static DataResult executeQueryResult(String sql, Object[] params) {
        try {
            return currentExecutor().queryResult(sql, params);
        } catch (SQLException e) {
            throw VKExceptionTranslator.translate(sql, e);
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

    static void handleBatchError(String message, SQLException e) {
        if (currentConfig().getBatchFailStrategy() == VKBatchFailStrategy.CONTINUE) {
            Vostok.Log.warn(message + ", strategy=CONTINUE, err=" + e.getMessage());
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
