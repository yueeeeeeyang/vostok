package yueyang.vostok.data;

import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.config.VKTxIsolation;
import yueyang.vostok.data.config.VKTxPropagation;
import yueyang.vostok.data.core.VostokAdminOps;
import yueyang.vostok.data.core.VostokBootstrap;
import yueyang.vostok.data.core.VostokCryptoMigrateOps;
import yueyang.vostok.data.core.VostokContext;
import yueyang.vostok.data.core.VostokCrudOps;
import yueyang.vostok.data.core.VostokTxOps;
import yueyang.vostok.data.jdbc.VKBatchDetailResult;
import yueyang.vostok.data.migrate.VKCryptoMigrateOptions;
import yueyang.vostok.data.migrate.VKCryptoMigratePlan;
import yueyang.vostok.data.migrate.VKCryptoMigrateResult;
import yueyang.vostok.data.pool.VKPoolMetrics;
import yueyang.vostok.data.plugin.VKInterceptor;
import yueyang.vostok.data.query.VKAggregate;
import yueyang.vostok.data.query.VKQuery;
import yueyang.vostok.common.scan.VKScanner;
import yueyang.vostok.util.VKAssert;

import java.util.List;
import java.util.function.Supplier;

/**
 * Vostok 数据入口。
 */
public class VostokData {
    protected VostokData() {
    }

    // 初始化 / 配置

    public static void init(VKDataConfig config, String... basePackages) {
        VostokBootstrap.init(config, basePackages);
    }

    public static void registerDataSource(String name, VKDataConfig config) {
        VostokBootstrap.registerDataSource(name, config);
    }

    public static void refreshMeta() {
        VostokBootstrap.refreshMeta();
    }

    public static void refreshMeta(String... basePackages) {
        VostokBootstrap.refreshMeta(basePackages);
    }

    public static void setScanner(VKScanner.EntityScanner scanner) {
        VostokBootstrap.setScanner(scanner);
    }

    public static void withDataSource(String name, Runnable action) {
        VostokBootstrap.withDataSource(name, action);
    }

    public static <T> T withDataSource(String name, Supplier<T> supplier) {
        return VostokBootstrap.withDataSource(name, supplier);
    }

    public static VostokContext captureContext() {
        return VostokContext.capture();
    }

    public static Runnable wrap(Runnable action) {
        VKAssert.notNull(action, "Runnable is null");
        VostokContext captured = VostokContext.capture();
        return () -> captured.run(action);
    }

    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        VKAssert.notNull(supplier, "Supplier is null");
        VostokContext captured = VostokContext.capture();
        return () -> captured.call(supplier);
    }

    public static Runnable wrap(VostokContext context, Runnable action) {
        VKAssert.notNull(context, "VostokContext is null");
        VKAssert.notNull(action, "Runnable is null");
        return () -> context.run(action);
    }

    public static <T> Supplier<T> wrap(VostokContext context, Supplier<T> supplier) {
        VKAssert.notNull(context, "VostokContext is null");
        VKAssert.notNull(supplier, "Supplier is null");
        return () -> context.call(supplier);
    }

    public static void registerInterceptor(VKInterceptor interceptor) {
        VostokBootstrap.registerInterceptor(interceptor);
    }

    public static void registerRawSql(String... sqls) {
        VostokBootstrap.registerRawSql(sqls);
    }

    public static void registerRawSql(String dataSourceName, String[] sqls) {
        VostokBootstrap.registerRawSql(dataSourceName, sqls);
    }

    public static void registerSubquery(String... sqls) {
        VostokBootstrap.registerSubquery(sqls);
    }

    public static void registerSubquery(String dataSourceName, String[] sqls) {
        VostokBootstrap.registerSubquery(dataSourceName, sqls);
    }

    public static void clearInterceptors() {
        VostokBootstrap.clearInterceptors();
    }

    public static void close() {
        VostokBootstrap.close();
    }

    // 事务

    public static void tx(Runnable action) {
        VostokTxOps.tx(action);
    }

    public static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation) {
        VostokTxOps.tx(action, propagation, isolation);
    }

    public static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly) {
        VostokTxOps.tx(action, propagation, isolation, readOnly);
    }

    public static <T> T tx(Supplier<T> supplier) {
        return VostokTxOps.tx(supplier);
    }

    public static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation) {
        return VostokTxOps.tx(supplier, propagation, isolation);
    }

    public static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly) {
        return VostokTxOps.tx(supplier, propagation, isolation, readOnly);
    }

    public static void beginTx() {
        VostokTxOps.beginTx();
    }

    public static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation) {
        VostokTxOps.beginTx(propagation, isolation);
    }

    public static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly) {
        VostokTxOps.beginTx(propagation, isolation, readOnly);
    }

    public static void commitTx() {
        VostokTxOps.commitTx();
    }

    public static void rollbackTx() {
        VostokTxOps.rollbackTx();
    }

    // CRUD / 查询

    public static int insert(Object entity) {
        return VostokCrudOps.insert(entity);
    }

    public static int batchInsert(List<?> entities) {
        return VostokCrudOps.batchInsert(entities);
    }

    public static VKBatchDetailResult batchInsertDetail(List<?> entities) {
        return VostokCrudOps.batchInsertDetail(entities);
    }

    public static int update(Object entity) {
        return VostokCrudOps.update(entity);
    }

    public static int batchUpdate(List<?> entities) {
        return VostokCrudOps.batchUpdate(entities);
    }

    public static VKBatchDetailResult batchUpdateDetail(List<?> entities) {
        return VostokCrudOps.batchUpdateDetail(entities);
    }

    public static int delete(Class<?> entityClass, Object idValue) {
        return VostokCrudOps.delete(entityClass, idValue);
    }

    public static int batchDelete(Class<?> entityClass, List<?> idValues) {
        return VostokCrudOps.batchDelete(entityClass, idValues);
    }

    public static VKBatchDetailResult batchDeleteDetail(Class<?> entityClass, List<?> idValues) {
        return VostokCrudOps.batchDeleteDetail(entityClass, idValues);
    }

    public static <T> T findById(Class<T> entityClass, Object idValue) {
        return VostokCrudOps.findById(entityClass, idValue);
    }

    public static <T> List<T> findAll(Class<T> entityClass) {
        return VostokCrudOps.findAll(entityClass);
    }

    public static <T> List<T> query(Class<T> entityClass, VKQuery query) {
        return VostokCrudOps.query(entityClass, query);
    }

    public static <T> List<T> queryColumns(Class<T> entityClass, VKQuery query, String... fields) {
        return VostokCrudOps.queryColumns(entityClass, query, fields);
    }

    public static List<Object[]> aggregate(Class<?> entityClass, VKQuery query, VKAggregate... aggregates) {
        return VostokCrudOps.aggregate(entityClass, query, aggregates);
    }

    public static long count(Class<?> entityClass, VKQuery query) {
        return VostokCrudOps.count(entityClass, query);
    }

    // 字段迁移

    public static VKCryptoMigrateResult encryptColumn(VKCryptoMigrateOptions options) {
        return VostokCryptoMigrateOps.encryptColumn(options);
    }

    public static VKCryptoMigrateResult decryptColumn(VKCryptoMigrateOptions options) {
        return VostokCryptoMigrateOps.decryptColumn(options);
    }

    public static VKCryptoMigratePlan previewEncrypt(VKCryptoMigrateOptions options) {
        return VostokCryptoMigrateOps.previewEncrypt(options);
    }

    public static VKCryptoMigratePlan previewDecrypt(VKCryptoMigrateOptions options) {
        return VostokCryptoMigrateOps.previewDecrypt(options);
    }

    // 监控 / 诊断

    public static List<VKPoolMetrics> poolMetrics() {
        return VostokAdminOps.poolMetrics();
    }

    public static String report() {
        return VostokAdminOps.report();
    }
}
