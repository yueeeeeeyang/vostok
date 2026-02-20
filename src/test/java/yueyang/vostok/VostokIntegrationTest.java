package yueyang.vostok;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import yueyang.vostok.data.config.VKBatchFailStrategy;
import yueyang.vostok.data.config.VKTxIsolation;
import yueyang.vostok.data.config.VKTxPropagation;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.Vostok;
import yueyang.vostok.data.dialect.VKDialectManager;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.exception.VKException;
import yueyang.vostok.data.exception.VKSqlException;
import yueyang.vostok.data.exception.VKStateException;
import yueyang.vostok.data.jdbc.VKSqlLogger;
import yueyang.vostok.data.meta.MetaLoader;
import yueyang.vostok.data.meta.MetaRegistry;
import yueyang.vostok.data.migrate.VKCryptoMigrateOptions;
import yueyang.vostok.data.migrate.VKCryptoMigratePlan;
import yueyang.vostok.data.migrate.VKCryptoMigrateResult;
import yueyang.vostok.data.plugin.VKInterceptor;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;
import yueyang.vostokbad.BadColumnEntity;
import yueyang.vostokbad.BadEntity;
import yueyang.vostokbad.BadEncryptedTypeEntity;
import yueyang.vostok.data.query.VKAggregate;
import yueyang.vostok.data.query.VKCondition;
import yueyang.vostok.data.query.VKOrder;
import yueyang.vostok.data.query.VKOperator;
import yueyang.vostok.data.query.VKQuery;
import yueyang.vostok.data.sql.SqlBuilder;
import yueyang.vostok.data.ds.VKDataSourceRegistry;
import yueyang.vostok.data.ds.VKDataSourceHolder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class VostokIntegrationTest {
    private static final String JDBC_URL = "jdbc:h2:mem:devkit;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @BeforeAll
    static void setUp() {
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1")
                .validationTimeoutSec(2)
                .statementCacheSize(10)
                .sqlTemplateCacheSize(50)
                .sqlMetricsEnabled(true)
                .slowSqlTopN(5);

        Vostok.Data.init(cfg, "yueyang.vostok");
        Vostok.Data.registerRawSql("COUNT(1)", "SLEEP(1200)", "SLEEP(600)", "SLEEP(10)");
        Vostok.Data.registerSubquery(
                "SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?",
                "SELECT id FROM t_user WHERE age >= ?"
        );

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64) NOT NULL, age INT)");
            stmt.execute("CREATE TABLE t_task (id BIGINT AUTO_INCREMENT PRIMARY KEY, start_date DATE, finish_time TIMESTAMP, amount DECIMAL(18,2), status VARCHAR(20))");
            stmt.execute("CREATE TABLE t_crypto_migrate (id BIGINT AUTO_INCREMENT PRIMARY KEY, secret_val VARCHAR(1024), tag VARCHAR(32))");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void cleanTables() {
        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_user");
            stmt.execute("DELETE FROM t_task");
            stmt.execute("DELETE FROM t_crypto_migrate");
            try {
                stmt.execute("DELETE FROM t_user_secret");
            } catch (Exception ignore) {
                // ignored
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void tearDown() {
        Vostok.Data.close();
    }

    @Test
    void testInsertFindUpdateDelete() {
        UserEntity u = new UserEntity();
        u.setName("Tom");
        u.setAge(20);
        int inserted = Vostok.Data.insert(u);
        assertEquals(1, inserted);
        assertNotNull(u.getId());

        UserEntity db = Vostok.Data.findById(UserEntity.class, u.getId());
        assertNotNull(db);
        assertEquals("Tom", db.getName());
        assertEquals(20, db.getAge());

        db.setName("Tom-2");
        int updated = Vostok.Data.update(db);
        assertEquals(1, updated);

        UserEntity db2 = Vostok.Data.findById(UserEntity.class, db.getId());
        assertEquals("Tom-2", db2.getName());

        int deleted = Vostok.Data.delete(UserEntity.class, db.getId());
        assertEquals(1, deleted);

        UserEntity none = Vostok.Data.findById(UserEntity.class, db.getId());
        assertNull(none);
    }

    @Test
    void testQueryOrderLimitOffset() {
        Vostok.Data.batchInsert(List.of(user("A", 10), user("B", 20), user("C", 30), user("D", 40)));

        VKQuery q = VKQuery.create()
                .where(VKCondition.of("age", VKOperator.GE, 20))
                .orderBy(VKOrder.desc("age"))
                .limit(2)
                .offset(0);

        List<UserEntity> list = Vostok.Data.query(UserEntity.class, q);
        assertEquals(2, list.size());
        assertEquals(40, list.get(0).getAge());
        assertEquals(30, list.get(1).getAge());

        long count = Vostok.Data.count(UserEntity.class, q);
        assertEquals(3, count);
    }

    @Test
    void testOrBetweenNotIn() {
        Vostok.Data.batchInsert(List.of(user("Tom", 18), user("Jack", 25), user("Lucy", 35)));

        VKQuery q = VKQuery.create()
                .or(
                        VKCondition.of("name", VKOperator.LIKE, "%Tom%"),
                        VKCondition.of("name", VKOperator.LIKE, "%Jack%")
                )
                .where(VKCondition.of("age", VKOperator.BETWEEN, 18, 30))
                .where(VKCondition.of("id", VKOperator.NOT_IN, 999, 1000));

        List<UserEntity> list = Vostok.Data.query(UserEntity.class, q);
        assertEquals(2, list.size());
    }

    @Test
    void testExistsAndInSubquery() {
        Vostok.Data.batchInsert(List.of(user("A", 10), user("B", 20), user("C", 30)));

        VKQuery q1 = VKQuery.create()
                .where(VKCondition.exists("SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?", 20));
        List<UserEntity> list1 = Vostok.Data.query(UserEntity.class, q1);
        assertEquals(2, list1.size());

        VKQuery q2 = VKQuery.create()
                .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 20));
        List<UserEntity> list2 = Vostok.Data.query(UserEntity.class, q2);
        assertEquals(2, list2.size());

        VKQuery q3 = VKQuery.create()
                .where(VKCondition.notInSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 20));
        List<UserEntity> list3 = Vostok.Data.query(UserEntity.class, q3);
        assertEquals(1, list3.size());
    }

    @Test
    void testBatchUpdateDelete() {
        List<UserEntity> users = List.of(user("U1", 11), user("U2", 12), user("U3", 13));
        int inserted = Vostok.Data.batchInsert(users);
        assertEquals(3, inserted);

        users.get(0).setAge(21);
        users.get(1).setAge(22);
        users.get(2).setAge(23);
        int updated = Vostok.Data.batchUpdate(users);
        assertEquals(3, updated);

        int deleted = Vostok.Data.batchDelete(UserEntity.class, List.of(users.get(0).getId(), users.get(1).getId(), users.get(2).getId()));
        assertEquals(3, deleted);
    }

    @Test
    void testBatchDetailResult() {
        List<UserEntity> users = List.of(user("D1", 1), user("D2", 2), user("D3", 3));
        var detail = Vostok.Data.batchInsertDetail(users);
        assertEquals(3, detail.getItems().size());
        assertEquals(3, detail.totalSuccess());
        assertEquals(0, detail.totalFail());
    }

    @Test
    void testTypeMapping() {
        TaskEntity task = new TaskEntity();
        task.setStartDate(LocalDate.of(2025, 1, 1));
        task.setFinishTime(LocalDateTime.of(2025, 1, 2, 10, 30));
        task.setAmount(new BigDecimal("123.45"));
        task.setStatus(Status.DONE);

        Vostok.Data.insert(task);
        assertNotNull(task.getId());

        TaskEntity db = Vostok.Data.findById(TaskEntity.class, task.getId());
        assertNotNull(db);
        assertEquals(LocalDate.of(2025, 1, 1), db.getStartDate());
        assertEquals(LocalDateTime.of(2025, 1, 2, 10, 30), db.getFinishTime());
        assertEquals(new BigDecimal("123.45"), db.getAmount());
        assertEquals(Status.DONE, db.getStatus());
    }

    @Test
    void testTransactionRollback() {
        int before = Vostok.Data.findAll(UserEntity.class).size();
        try {
            Vostok.Data.tx(() -> {
                Vostok.Data.insert(user("Rollback", 99));
                throw new RuntimeException("boom");
            });
            fail("Expected exception");
        } catch (RuntimeException e) {
            // expected
        }
        int after = Vostok.Data.findAll(UserEntity.class).size();
        assertEquals(before, after);
    }

    @Test
    void testTxRequiresNewIsolation() {
        Vostok.Data.tx(() -> {
            Vostok.Data.insert(user("Outer", 1));
            try {
                Vostok.Data.tx(() -> {
                    Vostok.Data.insert(user("Inner", 2));
                    throw new RuntimeException("inner");
                }, VKTxPropagation.REQUIRES_NEW, VKTxIsolation.READ_COMMITTED);
            } catch (RuntimeException e) {
                // expected for inner tx
            }
        });
        List<UserEntity> all = Vostok.Data.findAll(UserEntity.class);
        assertEquals(1, all.size());
        assertEquals("Outer", all.get(0).getName());
    }

    @Test
    void testSavepointNestedRequired() {
        Vostok.Data.tx(() -> {
            Vostok.Data.insert(user("A", 1));
            try {
                Vostok.Data.tx(() -> {
                    Vostok.Data.insert(user("B", 2));
                    throw new RuntimeException("inner");
                }, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT);
            } catch (RuntimeException e) {
                // ignore
            }
            Vostok.Data.insert(user("C", 3));
        });
        List<UserEntity> all = Vostok.Data.findAll(UserEntity.class);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(u -> "A".equals(u.getName())));
        assertTrue(all.stream().anyMatch(u -> "C".equals(u.getName())));
    }

    @Test
    void testProjectionQuery() {
        Vostok.Data.batchInsert(List.of(user("A", 10), user("B", 20)));
        VKQuery q = VKQuery.create().orderBy(VKOrder.asc("id"));
        List<UserEntity> list = Vostok.Data.queryColumns(UserEntity.class, q, "name");
        assertEquals(2, list.size());
        assertNotNull(list.get(0).getName());
        assertNull(list.get(0).getAge());
    }

    @Test
    void testAggregateGroupByHaving() {
        Vostok.Data.batchInsert(List.of(user("A", 10), user("B", 10), user("C", 20)));
        VKQuery q = VKQuery.create()
                .groupBy("age")
                .having(VKCondition.raw("COUNT(1)", VKOperator.GT, 1))
                .selectAggregates(VKAggregate.countAll("cnt"));
        List<Object[]> rows = Vostok.Data.aggregate(UserEntity.class, q);
        assertEquals(1, rows.size());
    }

    @Test
    void testMultiDataSource() {
        String url2 = "jdbc:h2:mem:devkit2;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = java.sql.DriverManager.getConnection(url2, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), age INT)");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        VKDataConfig cfg2 = new VKDataConfig()
                .url(url2)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL);
        Vostok.Data.registerDataSource("ds2", cfg2);

        Vostok.Data.withDataSource("ds2", () -> Vostok.Data.insert(user("DS2", 1)));
        assertEquals(0, Vostok.Data.findAll(UserEntity.class).size());
        Vostok.Data.withDataSource("ds2", () -> assertEquals(1, Vostok.Data.findAll(UserEntity.class).size()));
    }

    @Test
    void testWrapPropagatesDataSource() throws Exception {
        String url = "jdbc:h2:mem:devkit_wrap;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = java.sql.DriverManager.getConnection(url, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), age INT)");
        }

        VKDataConfig cfg = new VKDataConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL);
        Vostok.Data.registerDataSource("ds_wrap", cfg);

        var executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Integer> future = Vostok.Data.withDataSource("ds_wrap", () -> {
                Vostok.Data.insert(user("W1", 1));
                return CompletableFuture.supplyAsync(
                        Vostok.Data.wrap(() -> Vostok.Data.findAll(UserEntity.class).size()), executor
                );
            });
            assertEquals(1, future.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testWrapWithCapturedContext() throws Exception {
        String url = "jdbc:h2:mem:devkit_ctx;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = java.sql.DriverManager.getConnection(url, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), age INT)");
        }
        VKDataConfig cfg = new VKDataConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL);
        Vostok.Data.registerDataSource("ds_ctx", cfg);

        var executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Integer> future = Vostok.Data.withDataSource("ds_ctx", () -> {
                Vostok.Data.insert(user("C1", 1));
                var ctx = Vostok.Data.captureContext();
                return CompletableFuture.supplyAsync(
                        Vostok.Data.wrap(ctx, () -> Vostok.Data.findAll(UserEntity.class).size()), executor
                );
            });
            assertEquals(1, future.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testInterceptor() {
        class CounterInterceptor implements VKInterceptor {
            int before = 0;
            int after = 0;

            @Override
            public void beforeExecute(String sql, Object[] params) {
                before++;
            }

            @Override
            public void afterExecute(String sql, Object[] params, long costMs, boolean success, Throwable error) {
                after++;
            }
        }
        CounterInterceptor it = new CounterInterceptor();
        Vostok.Data.registerInterceptor(it);
        Vostok.Data.insert(user("Hook", 1));
        assertTrue(it.before > 0);
        assertTrue(it.after > 0);
        Vostok.Data.clearInterceptors();
    }

    @Test
    void testPoolMetricsAndReport() {
        var metrics = Vostok.Data.poolMetrics();
        assertFalse(metrics.isEmpty());
        String report = Vostok.Data.report();
        assertTrue(report.contains("Vostok Report"));
        assertTrue(report.contains("SqlMetrics"));
        assertTrue(report.contains("Histogram"));
        assertTrue(report.contains("SqlTemplateCacheSize"));
    }

    @Test
    void testNameValidation() {
        assertThrows(VKException.class, () -> MetaLoader.load(BadEntity.class));
        assertThrows(VKException.class, () -> MetaLoader.load(BadColumnEntity.class));
    }

    @Test
    void testDialectSql() {
        var meta = MetaLoader.load(UserEntity.class);
        VKDialectManager.init(new VKDataConfig().dialect(VKDialectType.SQLSERVER));
        String sql1 = SqlBuilder.buildSelect(meta, VKQuery.create().limit(10).offset(5)).getSql();
        assertTrue(sql1.contains("OFFSET 5 ROWS"));
        assertTrue(sql1.contains("FETCH NEXT 10 ROWS ONLY"));

        VKDialectManager.init(new VKDataConfig().dialect(VKDialectType.DB2));
        String sql2 = SqlBuilder.buildSelect(meta, VKQuery.create().limit(10).offset(5)).getSql();
        assertTrue(sql2.contains("OFFSET 5 ROWS"));
        assertTrue(sql2.contains("FETCH FIRST 10 ROWS ONLY"));

        VKDialectManager.init(new VKDataConfig().dialect(VKDialectType.MYSQL));
    }

    @Test
    void testHolderDialectIsolation() {
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.SQLSERVER);
        var meta = MetaLoader.load(UserEntity.class);
        var holder = new VKDataSourceHolder("dialect_tmp", cfg);
        try {
            String sql = SqlBuilder.buildSelect(meta, VKQuery.create().limit(10).offset(5), holder.getDialect()).getSql();
            assertTrue(sql.contains("OFFSET 5 ROWS"));
            assertTrue(sql.contains("FETCH NEXT 10 ROWS ONLY"));
        } finally {
            holder.close();
        }
    }

    @Test
    @Order(96)
    void testIdleValidationEvict() throws Exception {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(0)
                .maxActive(2)
                .maxWaitMs(10000)
                .idleTimeoutMs(5)
                .idleValidationIntervalMs(20)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        Vostok.Data.findAll(UserEntity.class);

        long deadline = System.currentTimeMillis() + 500;
        int total;
        do {
            Thread.sleep(20);
            total = Vostok.Data.poolMetrics().get(0).getTotal();
        } while (total > 0 && System.currentTimeMillis() < deadline);
        assertEquals(0, total);

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(97)
    void testPreheatAndLeakStack() throws Exception {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(2)
                .maxActive(5)
                .maxWaitMs(10000)
                .preheatEnabled(true)
                .leakDetectMs(10)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        var metrics = Vostok.Data.poolMetrics().get(0);
        assertTrue(metrics.getIdle() >= 2);

        var holder = yueyang.vostok.data.ds.VKDataSourceRegistry.getDefault();
        try (var conn = holder.getDataSource().getConnection()) {
            Thread.sleep(20);
        }
        String report = Vostok.Data.report();
        assertTrue(report.contains("LeakStack"));

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(98)
    void testTxTimeout() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .txTimeoutMs(30)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        assertThrows(yueyang.vostok.data.exception.VKTxException.class, () -> {
            Vostok.Data.tx(() -> {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Vostok.Data.findAll(UserEntity.class);
            });
        });

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(94)
    void testConnectionStateResetOnReturn() throws Exception {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(1)
                .maxWaitMs(10000)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        var holder = yueyang.vostok.data.ds.VKDataSourceRegistry.getDefault();
        int defaultIsolation;
        boolean defaultReadOnly;
        boolean defaultAutoCommit;
        try (Connection conn = holder.getDataSource().getConnection()) {
            defaultIsolation = conn.getTransactionIsolation();
            defaultReadOnly = conn.isReadOnly();
            defaultAutoCommit = conn.getAutoCommit();
        }

        try (Connection conn = holder.getDataSource().getConnection()) {
            conn.setAutoCommit(false);
            conn.setReadOnly(true);
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        }

        try (Connection conn = holder.getDataSource().getConnection()) {
            assertEquals(defaultAutoCommit, conn.getAutoCommit());
            assertEquals(defaultReadOnly, conn.isReadOnly());
            assertEquals(defaultIsolation, conn.getTransactionIsolation());
        }

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(93)
    void testStatementTimeoutInTransaction() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .txTimeoutMs(900)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        assertThrows(VKException.class, () -> {
            Vostok.Data.tx(() -> {
                Vostok.Data.insert(user("Sleep", 1));
                VKQuery q = VKQuery.create()
                        .where(VKCondition.raw("SLEEP(1200)", VKOperator.GE, 0));
                Vostok.Data.query(UserEntity.class, q);
            });
        });

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(95)
    void testTxIsolationRestoredAfterCommit() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(1)
                .maxWaitMs(10000)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        var holder = yueyang.vostok.data.ds.VKDataSourceRegistry.getDefault();
        int defaultIsolation;
        try (Connection conn = holder.getDataSource().getConnection()) {
            defaultIsolation = conn.getTransactionIsolation();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Vostok.Data.tx(() -> Vostok.Data.findAll(UserEntity.class), VKTxPropagation.REQUIRED, VKTxIsolation.SERIALIZABLE, false);

        try (Connection conn = holder.getDataSource().getConnection()) {
            assertEquals(defaultIsolation, conn.getTransactionIsolation());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(96)
    void testStatementCacheClosedOnReturn() throws Exception {
        var holder = yueyang.vostok.data.ds.VKDataSourceRegistry.getDefault();
        PreparedStatement ps;
        try (Connection conn = holder.getDataSource().getConnection()) {
            ps = conn.prepareStatement("SELECT 1");
        }
        assertTrue(ps.isClosed());
    }

    @Test
    @Order(97)
    void testConcurrentInitSafety() throws Exception {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .validationQuery("SELECT 1");

        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    Vostok.Data.init(cfg, "yueyang.vostok");
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertTrue(errors.isEmpty());
        assertEquals(1, yueyang.vostok.data.ds.VKDataSourceRegistry.all().size());

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(92)
    void testConcurrentRefreshMeta() throws Exception {
        int threads = 4;
        int loops = 30;
        var pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        for (int j = 0; j < loops; j++) {
                            Vostok.Data.refreshMeta("yueyang.vostok");
                            Vostok.Data.findAll(UserEntity.class);
                        }
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add(t);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(20, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(93)
    void testTemplateCacheSnapshotOnRefresh() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        var cache1 = MetaRegistry.getTemplateCache(VKDataSourceRegistry.getDefaultName());
        Vostok.Data.findAll(UserEntity.class);
        assertTrue(cache1.size() > 0);

        Vostok.Data.refreshMeta("yueyang.vostok");
        var cache2 = MetaRegistry.getTemplateCache(VKDataSourceRegistry.getDefaultName());
        assertNotSame(cache1, cache2);
        assertEquals(0, cache2.size());
    }

    @Test
    @Order(91)
    void testQueryTimeoutNonTx() {
        try {
            Vostok.Data.close();
            VKDataConfig cfg = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .queryTimeoutMs(200)
                    .validationQuery("SELECT 1");
            Vostok.Data.init(cfg, "yueyang.vostok");

            Vostok.Data.insert(user("Sleep2", 2));
            assertThrows(VKException.class, () -> {
                VKQuery q = VKQuery.create()
                        .where(VKCondition.raw("SLEEP(600)", VKOperator.GE, 0));
                Vostok.Data.query(UserEntity.class, q);
            });
        } finally {
            // restore default config for remaining tests
            Vostok.Data.close();
            VKDataConfig cfg2 = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .minIdle(1)
                    .maxActive(5)
                    .maxWaitMs(10000)
                    .batchSize(2)
                    .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                    .validationQuery("SELECT 1");
            Vostok.Data.init(cfg2, "yueyang.vostok");
        }
    }

    @Test
    @Order(90)
    void testRawAndSubqueryWhitelist() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        Vostok.Data.registerRawSql("1");
        Vostok.Data.registerSubquery("SELECT id FROM t_user WHERE age >= ?");

        Vostok.Data.insert(user("W1", 10));

        VKQuery okRaw = VKQuery.create()
                .where(VKCondition.raw("1", VKOperator.GE, 0));
        List<UserEntity> okRawList = Vostok.Data.query(UserEntity.class, okRaw);
        assertEquals(1, okRawList.size());

        VKQuery okSub = VKQuery.create()
                .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 0));
        List<UserEntity> list = Vostok.Data.query(UserEntity.class, okSub);
        assertEquals(1, list.size());

        VKQuery badRaw = VKQuery.create()
                .where(VKCondition.raw("2", VKOperator.GE, 0));
        assertThrows(VKException.class, () -> Vostok.Data.query(UserEntity.class, badRaw));

        VKQuery badSub = VKQuery.create()
                .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 0, 1));
        assertThrows(VKException.class, () -> Vostok.Data.query(UserEntity.class, badSub));

        // restore default config for remaining tests
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(91)
    void testFieldEncryptionRoundTrip() throws Exception {
        try {
            Vostok.Data.close();
            Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                    .baseDir(Files.createTempDirectory("vostok-ks-roundtrip").toString())
                    .masterKey("vostok-test-master-key-roundtrip-001"));

            VKDataConfig cfg = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .validationQuery("SELECT 1")
                    .autoCreateTable(true)
                    .fieldEncryptionEnabled(true)
                    .defaultEncryptionKeyId("enc-k1");
            Vostok.Data.init(cfg, "yueyang.vostok");

            EncryptedUserEntity user = new EncryptedUserEntity();
            user.setSecretName("alice");
            user.setAge(18);
            assertEquals(1, Vostok.Data.insert(user));
            assertNotNull(user.getId());

            EncryptedUserEntity loaded = Vostok.Data.findById(EncryptedUserEntity.class, user.getId());
            assertNotNull(loaded);
            assertEquals("alice", loaded.getSecretName());
            assertEquals(18, loaded.getAge());

            try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
                 var ps = conn.prepareStatement("SELECT secret_name FROM t_user_secret WHERE id = ?")) {
                ps.setLong(1, user.getId());
                try (var rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String dbValue = rs.getString(1);
                    assertNotEquals("alice", dbValue);
                    assertTrue(dbValue.startsWith("vk1:aes:enc-k1:"));
                }
            }
        } finally {
            restoreDefaultDataConfig();
        }
    }

    @Test
    @Order(92)
    void testFieldEncryptionPlaintextCompatibility() throws Exception {
        try {
            Vostok.Data.close();
            Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                    .baseDir(Files.createTempDirectory("vostok-ks-plain").toString())
                    .masterKey("vostok-test-master-key-plain-001"));

            VKDataConfig cfg = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .validationQuery("SELECT 1")
                    .autoCreateTable(true)
                    .fieldEncryptionEnabled(true)
                    .defaultEncryptionKeyId("enc-k1")
                    .allowPlaintextRead(false);
            Vostok.Data.init(cfg, "yueyang.vostok");

            try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
                 var stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO t_user_secret(secret_name, age) VALUES ('legacy-plaintext', 20)");
            }

            VKException ex = assertThrows(VKException.class,
                    () -> Vostok.Data.findAll(EncryptedUserEntity.class));
            assertNotNull(ex.getMessage());

            Vostok.Data.close();
            cfg.allowPlaintextRead(true);
            Vostok.Data.init(cfg, "yueyang.vostok");

            List<EncryptedUserEntity> list = Vostok.Data.findAll(EncryptedUserEntity.class);
            assertEquals(1, list.size());
            assertEquals("legacy-plaintext", list.get(0).getSecretName());
        } finally {
            restoreDefaultDataConfig();
        }
    }

    @Test
    @Order(93)
    void testFieldEncryptionQueryRestrictions() throws Exception {
        try {
            Vostok.Data.close();
            Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                    .baseDir(Files.createTempDirectory("vostok-ks-query").toString())
                    .masterKey("vostok-test-master-key-query-001"));

            VKDataConfig cfg = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .validationQuery("SELECT 1")
                    .autoCreateTable(true)
                    .fieldEncryptionEnabled(true)
                    .defaultEncryptionKeyId("enc-k1");
            Vostok.Data.init(cfg, "yueyang.vostok");

            EncryptedUserEntity user = new EncryptedUserEntity();
            user.setSecretName("bob");
            user.setAge(22);
            Vostok.Data.insert(user);

            VKQuery like = VKQuery.create().where(VKCondition.of("secretName", VKOperator.LIKE, "%bo%"));
            assertThrows(VKException.class, () -> Vostok.Data.query(EncryptedUserEntity.class, like));

            VKQuery order = VKQuery.create().orderBy(VKOrder.desc("secretName"));
            assertThrows(VKException.class, () -> Vostok.Data.query(EncryptedUserEntity.class, order));

            VKQuery agg = VKQuery.create();
            assertThrows(VKException.class, () -> Vostok.Data.aggregate(EncryptedUserEntity.class, agg,
                    VKAggregate.max("secretName", "mx")));

            VKQuery isNull = VKQuery.create().where(VKCondition.of("secretName", VKOperator.IS_NOT_NULL));
            assertEquals(1, Vostok.Data.query(EncryptedUserEntity.class, isNull).size());
        } finally {
            restoreDefaultDataConfig();
        }
    }

    @Test
    @Order(94)
    void testEncryptedFieldTypeValidation() {
        VKException ex = assertThrows(VKException.class, () -> MetaLoader.load(BadEncryptedTypeEntity.class));
        assertTrue(ex.getMessage().contains("must be String type"));
    }

    @Test
    @Order(95)
    void testEncryptionConfigValidation() {
        Vostok.Data.close();
        VKDataConfig bad = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1")
                .fieldEncryptionEnabled(true)
                .defaultEncryptionKeyId(" ");
        VKException ex = assertThrows(VKException.class, () -> Vostok.Data.init(bad, "yueyang.vostok"));
        assertTrue(ex.getMessage().contains("defaultEncryptionKeyId"));
        restoreDefaultDataConfig();
    }

    @Test
    @Order(96)
    void testCryptoMigrateEncryptDecrypt() throws Exception {
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(Files.createTempDirectory("vostok-ks-migrate").toString())
                .masterKey("vostok-test-master-key-migrate-001"));

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('p-1','A')");
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('p-2','A')");
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('p-3','B')");
        }

        VKCryptoMigrateOptions encrypt = new VKCryptoMigrateOptions()
                .table("t_crypto_migrate")
                .idColumn("id")
                .targetColumn("secret_val")
                .batchSize(2)
                .encryptKeyId("m-key");
        VKCryptoMigrateResult r1 = Vostok.Data.encryptColumn(encrypt);
        assertEquals(3, r1.getScannedRows());
        assertEquals(3, r1.getUpdatedRows());
        assertEquals(0, r1.getFailedRows());

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var rs = conn.createStatement().executeQuery("SELECT secret_val FROM t_crypto_migrate ORDER BY id")) {
            int count = 0;
            while (rs.next()) {
                String value = rs.getString(1);
                assertTrue(value.startsWith("vk1:aes:m-key:"));
                count++;
            }
            assertEquals(3, count);
        }

        VKCryptoMigrateResult r2 = Vostok.Data.encryptColumn(encrypt);
        assertEquals(0, r2.getUpdatedRows());
        assertEquals(3, r2.getSkippedRows());

        VKCryptoMigrateResult r3 = Vostok.Data.decryptColumn(new VKCryptoMigrateOptions()
                .table("t_crypto_migrate")
                .idColumn("id")
                .targetColumn("secret_val")
                .batchSize(2)
                .allowPlaintextRead(false));
        assertEquals(3, r3.getUpdatedRows());
        assertEquals(0, r3.getFailedRows());

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var rs = conn.createStatement().executeQuery("SELECT secret_val FROM t_crypto_migrate ORDER BY id")) {
            assertTrue(rs.next());
            assertEquals("p-1", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("p-2", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("p-3", rs.getString(1));
        }
    }

    @Test
    @Order(97)
    void testCryptoMigrateWhitelistPreviewAndDryRun() throws Exception {
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(Files.createTempDirectory("vostok-ks-migrate-plan").toString())
                .masterKey("vostok-test-master-key-migrate-plan"));

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('w-1','A')");
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('w-2','B')");
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('w-3','A')");
        }

        VKCryptoMigrateOptions badWhere = new VKCryptoMigrateOptions()
                .table("t_crypto_migrate")
                .idColumn("id")
                .targetColumn("secret_val")
                .whereSql("tag = ?")
                .whereParams("A")
                .encryptKeyId("m-key");
        assertThrows(VKException.class, () -> Vostok.Data.previewEncrypt(badWhere));

        Vostok.Data.registerRawSql("tag = ?");
        VKCryptoMigratePlan plan = Vostok.Data.previewEncrypt(badWhere);
        assertEquals("ENCRYPT", plan.getMode());
        assertEquals(2, plan.getEstimatedRows());

        VKCryptoMigrateResult dryRun = Vostok.Data.encryptColumn(new VKCryptoMigrateOptions()
                .table("t_crypto_migrate")
                .idColumn("id")
                .targetColumn("secret_val")
                .whereSql("tag = ?")
                .whereParams("A")
                .encryptKeyId("m-key")
                .dryRun(true));
        assertEquals(2, dryRun.getScannedRows());
        assertEquals(0, dryRun.getFailedRows());
        assertEquals(2, dryRun.getUpdatedRows());

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var rs = conn.createStatement().executeQuery("SELECT secret_val FROM t_crypto_migrate WHERE tag='A' ORDER BY id")) {
            while (rs.next()) {
                assertFalse(rs.getString(1).startsWith("vk1:aes:"));
            }
        }
    }

    @Test
    @Order(98)
    void testCryptoMigrateDecryptPlaintextHandling() throws Exception {
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(Files.createTempDirectory("vostok-ks-migrate-plain").toString())
                .masterKey("vostok-test-master-key-migrate-plain"));

        String cipher = Vostok.Security.encryptWithKeyId("c-1", "m-key");
        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES ('p-raw','A')");
            try (var ps = conn.prepareStatement("INSERT INTO t_crypto_migrate(secret_val, tag) VALUES (?, 'A')")) {
                ps.setString(1, cipher);
                ps.executeUpdate();
            }
        }

        VKException ex = assertThrows(VKException.class, () -> Vostok.Data.decryptColumn(new VKCryptoMigrateOptions()
                .table("t_crypto_migrate")
                .idColumn("id")
                .targetColumn("secret_val")
                .allowPlaintextRead(false)));
        assertNotNull(ex.getMessage());

        VKCryptoMigrateResult ok = Vostok.Data.decryptColumn(new VKCryptoMigrateOptions()
                .table("t_crypto_migrate")
                .idColumn("id")
                .targetColumn("secret_val")
                .allowPlaintextRead(true)
                .skipOnError(true));
        assertEquals(1, ok.getUpdatedRows());
        assertEquals(1, ok.getSkippedRows());
        assertEquals(0, ok.getFailedRows());
    }

    @Test
    @Order(89)
    void testCustomScanner() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");

        Vostok.Data.setScanner((pkgs) -> Set.of(UserEntity.class, TaskEntity.class));
        Vostok.Data.init(cfg, "ignored.pkg");

        assertEquals(2, yueyang.vostok.data.meta.MetaRegistry.size());

        // restore default scanner + config for remaining tests
        Vostok.Data.setScanner(yueyang.vostok.common.scan.VKScanner::scan);
        Vostok.Data.close();
        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg2, "yueyang.vostok");
        Vostok.Data.registerRawSql("COUNT(1)", "SLEEP(1200)", "SLEEP(600)", "SLEEP(10)");
        Vostok.Data.registerSubquery(
                "SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?",
                "SELECT id FROM t_user WHERE age >= ?"
        );
    }

    private static void restoreDefaultDataConfig() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");
        Vostok.Data.registerRawSql("COUNT(1)", "SLEEP(1200)", "SLEEP(600)", "SLEEP(10)");
        Vostok.Data.registerSubquery(
                "SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?",
                "SELECT id FROM t_user WHERE age >= ?"
        );
    }

    @Test
    @Order(88)
    void testWhitelistIsolatedByDataSource() throws Exception {
        VKDataConfig cfg2 = new VKDataConfig()
                .url("jdbc:h2:mem:ds2;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");
        Vostok.Data.registerDataSource("ds2", cfg2);

        try (var conn = java.sql.DriverManager.getConnection(cfg2.getUrl(), "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64) NOT NULL, age INT)");
            stmt.execute("DELETE FROM t_user");
        }

        Vostok.Data.registerRawSql("ds2", new String[]{"1"});

        Vostok.Data.insert(user("D1", 1));
        Vostok.Data.withDataSource("ds2", () -> Vostok.Data.insert(user("D2", 2)));

        VKQuery raw = VKQuery.create()
                .where(VKCondition.raw("1", VKOperator.GE, 0));

        assertThrows(VKException.class, () -> Vostok.Data.query(UserEntity.class, raw));
        Vostok.Data.withDataSource("ds2", () -> {
            List<UserEntity> list = Vostok.Data.query(UserEntity.class, raw);
            assertEquals(1, list.size());
        });
    }

    @Test
    @Order(99)
    void testConcurrencyPressure() throws Exception {
        try {
            Vostok.Data.close();
            VKDataConfig cfg = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .minIdle(2)
                    .maxActive(20)
                    .maxWaitMs(30000)
                    .validationQuery("SELECT 1");
            Vostok.Data.init(cfg, "yueyang.vostok");

            for (int i = 0; i < 100; i++) {
                Vostok.Data.insert(user("U-seed-" + i, i));
            }

            int threads = 10;
            int loops = 30;
            var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            var latch = new java.util.concurrent.CountDownLatch(threads);
            var errors = new java.util.concurrent.atomic.AtomicInteger(0);
            for (int i = 0; i < threads; i++) {
                int idx = i;
                pool.submit(() -> {
                    try {
                        for (int j = 0; j < loops; j++) {
                            List<UserEntity> list = Vostok.Data.findAll(UserEntity.class);
                            if (!list.isEmpty()) {
                                UserEntity one = list.get((idx + j) % list.size());
                                one.setAge((one.getAge() == null ? 0 : one.getAge()) + 1);
                                Vostok.Data.update(one);
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            pool.shutdown();
            assertEquals(0, errors.get());
        } finally {
            // restore default config for remaining tests
            Vostok.Data.close();
            VKDataConfig cfg2 = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .minIdle(1)
                    .maxActive(5)
                    .maxWaitMs(10000)
                    .batchSize(2)
                    .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                    .validationQuery("SELECT 1");
            Vostok.Data.init(cfg2, "yueyang.vostok");
        }
    }

    @Test
    @Order(100)
    void testPoolStabilityUnderLoad() throws Exception {
        try {
            Vostok.Data.close();
            VKDataConfig cfg = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .minIdle(1)
                    .maxActive(20)
                    .maxWaitMs(10000)
                    .validationQuery("SELECT 1");
            Vostok.Data.init(cfg, "yueyang.vostok");

            int threads = 20;
            var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            var latch = new java.util.concurrent.CountDownLatch(threads);
            var errors = new java.util.concurrent.atomic.AtomicInteger(0);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        Vostok.Data.findAll(UserEntity.class);
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            pool.shutdown();
            assertEquals(0, errors.get());
        } finally {
            // restore default config for remaining tests even when assert fails
            Vostok.Data.close();
            VKDataConfig cfg2 = new VKDataConfig()
                    .url(JDBC_URL)
                    .username("sa")
                    .password("")
                    .driver("org.h2.Driver")
                    .dialect(VKDialectType.MYSQL)
                    .minIdle(1)
                    .maxActive(5)
                    .maxWaitMs(10000)
                    .batchSize(2)
                    .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                    .validationQuery("SELECT 1");
            Vostok.Data.init(cfg2, "yueyang.vostok");
        }
    }

    @Test
    @Order(101)
    void testDdlValidation() {
        Vostok.Data.close();
        String url = "jdbc:h2:mem:ddltest;MODE=MySQL;DB_CLOSE_DELAY=-1";
        VKDataConfig cfg = new VKDataConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validateDdl(true);
        assertThrows(VKException.class, () -> Vostok.Data.init(cfg, "yueyang.vostok"));
        Vostok.Data.close();

        VKDataConfig cfg2 = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST);
        Vostok.Data.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(102)
    void testAutoCreateTableOnInit() {
        Vostok.Data.close();
        String url = "jdbc:h2:mem:ddlcreate;MODE=MySQL;DB_CLOSE_DELAY=-1";
        VKDataConfig cfg = new VKDataConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .autoCreateTable(true)
                .validateDdl(true);
        Vostok.Data.init(cfg, "yueyang.vostok");
        int inserted = Vostok.Data.insert(user("Auto", 1));
        assertEquals(1, inserted);
    }

    @Test
    @Order(103)
    void testAutoCreateTableOnRefreshMeta() throws Exception {
        Vostok.Data.close();
        String url = "jdbc:h2:mem:ddlrefresh;MODE=MySQL;DB_CLOSE_DELAY=-1";
        VKDataConfig cfg = new VKDataConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .autoCreateTable(true);
        Vostok.Data.init(cfg, "yueyang.vostok");
        Vostok.Data.insert(user("Auto2", 2));

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "sa", "");
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE t_user");
        }

        Vostok.Data.refreshMeta("yueyang.vostok");
        int inserted = Vostok.Data.insert(user("Auto3", 3));
        assertEquals(1, inserted);
    }

    @Test
    void testBatchFailStrategyContinue() {
        Vostok.Data.close();
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.CONTINUE)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");

        UserEntity ok1 = user("OK1", 1);
        UserEntity bad = user(null, 2); // user_name NOT NULL
        UserEntity ok2 = user("OK2", 3);

        int inserted = Vostok.Data.batchInsert(List.of(ok1, bad, ok2));
        // 部分 JDBC 实现会在失败分片中保留部分成功记录，这里只断言不会抛异常且至少成功 1 条
        assertTrue(inserted >= 1);
        assertTrue(Vostok.Data.findAll(UserEntity.class).size() >= 1);
    }

    @Test
    @Order(102)
    void testSqlLoggingAndSlow() {
        String s1 = VKSqlLogger.formatSql("SELECT * FROM t_user WHERE id = ?", new Object[]{1}, true);
        String s2 = VKSqlLogger.formatSlow("SELECT * FROM t_user WHERE id = ?", new Object[]{1}, 1, true);
        assertTrue(s1.contains("SQL:"));
        assertTrue(s1.contains("params="));
        assertTrue(s2.contains("SLOW SQL"));
        assertTrue(s2.contains("costMs="));
    }

    @Test
    void testSqlExceptionMapping() {
        try {
            Vostok.Data.insert(user(null, 1));
            fail("Expected exception");
        } catch (VKSqlException e) {
            assertNotNull(e.getSqlState());
        }
    }

    @Test
    void testMetaRefresh() {
        long before = yueyang.vostok.data.meta.MetaRegistry.getLastRefreshAt();
        Vostok.Data.refreshMeta("yueyang.vostok");
        long after = yueyang.vostok.data.meta.MetaRegistry.getLastRefreshAt();
        assertTrue(after >= before);
    }

    @Test
    @Order(103)
    void testNotInitialized() {
        Vostok.Data.close();
        assertThrows(VKStateException.class, () -> Vostok.Data.findAll(UserEntity.class));

        // re-init for remaining tests (if any)
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5)
                .maxWaitMs(10000)
                .batchSize(2)
                .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
                .validationQuery("SELECT 1");
        Vostok.Data.init(cfg, "yueyang.vostok");
    }

    private static UserEntity user(String name, int age) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setAge(age);
        return u;
    }
}
