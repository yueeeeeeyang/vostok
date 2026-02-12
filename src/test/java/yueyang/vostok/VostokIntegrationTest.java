package yueyang.vostok;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import yueyang.vostok.config.VKBatchFailStrategy;
import yueyang.vostok.config.VKTxIsolation;
import yueyang.vostok.config.VKTxPropagation;
import yueyang.vostok.config.DataSourceConfig;
import yueyang.vostok.core.Vostok;
import yueyang.vostok.dialect.VKDialectManager;
import yueyang.vostok.dialect.VKDialectType;
import yueyang.vostok.exception.VKException;
import yueyang.vostok.exception.VKSqlException;
import yueyang.vostok.exception.VKStateException;
import yueyang.vostok.jdbc.VKSqlLogger;
import yueyang.vostok.meta.MetaLoader;
import yueyang.vostok.plugin.VKInterceptor;
import yueyang.vostokbad.BadColumnEntity;
import yueyang.vostokbad.BadEntity;
import yueyang.vostok.query.VKAggregate;
import yueyang.vostok.query.VKCondition;
import yueyang.vostok.query.VKOrder;
import yueyang.vostok.query.VKOperator;
import yueyang.vostok.query.VKQuery;
import yueyang.vostok.sql.SqlBuilder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
        DataSourceConfig cfg = new DataSourceConfig()
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

        Vostok.init(cfg, "yueyang.vostok");
        Vostok.registerRawSql("COUNT(1)", "SLEEP(1200)", "SLEEP(600)", "SLEEP(10)");
        Vostok.registerSubquery(
                "SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?",
                "SELECT id FROM t_user WHERE age >= ?"
        );

        try (var conn = java.sql.DriverManager.getConnection(JDBC_URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64) NOT NULL, age INT)");
            stmt.execute("CREATE TABLE t_task (id BIGINT AUTO_INCREMENT PRIMARY KEY, start_date DATE, finish_time TIMESTAMP, amount DECIMAL(18,2), status VARCHAR(20))");
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    static void tearDown() {
        Vostok.close();
    }

    @Test
    void testInsertFindUpdateDelete() {
        UserEntity u = new UserEntity();
        u.setName("Tom");
        u.setAge(20);
        int inserted = Vostok.insert(u);
        assertEquals(1, inserted);
        assertNotNull(u.getId());

        UserEntity db = Vostok.findById(UserEntity.class, u.getId());
        assertNotNull(db);
        assertEquals("Tom", db.getName());
        assertEquals(20, db.getAge());

        db.setName("Tom-2");
        int updated = Vostok.update(db);
        assertEquals(1, updated);

        UserEntity db2 = Vostok.findById(UserEntity.class, db.getId());
        assertEquals("Tom-2", db2.getName());

        int deleted = Vostok.delete(UserEntity.class, db.getId());
        assertEquals(1, deleted);

        UserEntity none = Vostok.findById(UserEntity.class, db.getId());
        assertNull(none);
    }

    @Test
    void testQueryOrderLimitOffset() {
        Vostok.batchInsert(List.of(user("A", 10), user("B", 20), user("C", 30), user("D", 40)));

        VKQuery q = VKQuery.create()
                .where(VKCondition.of("age", VKOperator.GE, 20))
                .orderBy(VKOrder.desc("age"))
                .limit(2)
                .offset(0);

        List<UserEntity> list = Vostok.query(UserEntity.class, q);
        assertEquals(2, list.size());
        assertEquals(40, list.get(0).getAge());
        assertEquals(30, list.get(1).getAge());

        long count = Vostok.count(UserEntity.class, q);
        assertEquals(3, count);
    }

    @Test
    void testOrBetweenNotIn() {
        Vostok.batchInsert(List.of(user("Tom", 18), user("Jack", 25), user("Lucy", 35)));

        VKQuery q = VKQuery.create()
                .or(
                        VKCondition.of("name", VKOperator.LIKE, "%Tom%"),
                        VKCondition.of("name", VKOperator.LIKE, "%Jack%")
                )
                .where(VKCondition.of("age", VKOperator.BETWEEN, 18, 30))
                .where(VKCondition.of("id", VKOperator.NOT_IN, 999, 1000));

        List<UserEntity> list = Vostok.query(UserEntity.class, q);
        assertEquals(2, list.size());
    }

    @Test
    void testExistsAndInSubquery() {
        Vostok.batchInsert(List.of(user("A", 10), user("B", 20), user("C", 30)));

        VKQuery q1 = VKQuery.create()
                .where(VKCondition.exists("SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?", 20));
        List<UserEntity> list1 = Vostok.query(UserEntity.class, q1);
        assertEquals(2, list1.size());

        VKQuery q2 = VKQuery.create()
                .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 20));
        List<UserEntity> list2 = Vostok.query(UserEntity.class, q2);
        assertEquals(2, list2.size());

        VKQuery q3 = VKQuery.create()
                .where(VKCondition.notInSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 20));
        List<UserEntity> list3 = Vostok.query(UserEntity.class, q3);
        assertEquals(1, list3.size());
    }

    @Test
    void testBatchUpdateDelete() {
        List<UserEntity> users = List.of(user("U1", 11), user("U2", 12), user("U3", 13));
        int inserted = Vostok.batchInsert(users);
        assertEquals(3, inserted);

        users.get(0).setAge(21);
        users.get(1).setAge(22);
        users.get(2).setAge(23);
        int updated = Vostok.batchUpdate(users);
        assertEquals(3, updated);

        int deleted = Vostok.batchDelete(UserEntity.class, List.of(users.get(0).getId(), users.get(1).getId(), users.get(2).getId()));
        assertEquals(3, deleted);
    }

    @Test
    void testBatchDetailResult() {
        List<UserEntity> users = List.of(user("D1", 1), user("D2", 2), user("D3", 3));
        var detail = Vostok.batchInsertDetail(users);
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

        Vostok.insert(task);
        assertNotNull(task.getId());

        TaskEntity db = Vostok.findById(TaskEntity.class, task.getId());
        assertNotNull(db);
        assertEquals(LocalDate.of(2025, 1, 1), db.getStartDate());
        assertEquals(LocalDateTime.of(2025, 1, 2, 10, 30), db.getFinishTime());
        assertEquals(new BigDecimal("123.45"), db.getAmount());
        assertEquals(Status.DONE, db.getStatus());
    }

    @Test
    void testTransactionRollback() {
        int before = Vostok.findAll(UserEntity.class).size();
        try {
            Vostok.tx(() -> {
                Vostok.insert(user("Rollback", 99));
                throw new RuntimeException("boom");
            });
            fail("Expected exception");
        } catch (RuntimeException e) {
            // expected
        }
        int after = Vostok.findAll(UserEntity.class).size();
        assertEquals(before, after);
    }

    @Test
    void testTxRequiresNewIsolation() {
        Vostok.tx(() -> {
            Vostok.insert(user("Outer", 1));
            try {
                Vostok.tx(() -> {
                    Vostok.insert(user("Inner", 2));
                    throw new RuntimeException("inner");
                }, VKTxPropagation.REQUIRES_NEW, VKTxIsolation.READ_COMMITTED);
            } catch (RuntimeException e) {
                // expected for inner tx
            }
        });
        List<UserEntity> all = Vostok.findAll(UserEntity.class);
        assertEquals(1, all.size());
        assertEquals("Outer", all.get(0).getName());
    }

    @Test
    void testSavepointNestedRequired() {
        Vostok.tx(() -> {
            Vostok.insert(user("A", 1));
            try {
                Vostok.tx(() -> {
                    Vostok.insert(user("B", 2));
                    throw new RuntimeException("inner");
                }, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT);
            } catch (RuntimeException e) {
                // ignore
            }
            Vostok.insert(user("C", 3));
        });
        List<UserEntity> all = Vostok.findAll(UserEntity.class);
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(u -> "A".equals(u.getName())));
        assertTrue(all.stream().anyMatch(u -> "C".equals(u.getName())));
    }

    @Test
    void testProjectionQuery() {
        Vostok.batchInsert(List.of(user("A", 10), user("B", 20)));
        VKQuery q = VKQuery.create().orderBy(VKOrder.asc("id"));
        List<UserEntity> list = Vostok.queryColumns(UserEntity.class, q, "name");
        assertEquals(2, list.size());
        assertNotNull(list.get(0).getName());
        assertNull(list.get(0).getAge());
    }

    @Test
    void testAggregateGroupByHaving() {
        Vostok.batchInsert(List.of(user("A", 10), user("B", 10), user("C", 20)));
        VKQuery q = VKQuery.create()
                .groupBy("age")
                .having(VKCondition.raw("COUNT(1)", VKOperator.GT, 1))
                .selectAggregates(VKAggregate.countAll("cnt"));
        List<Object[]> rows = Vostok.aggregate(UserEntity.class, q);
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
        DataSourceConfig cfg2 = new DataSourceConfig()
                .url(url2)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL);
        Vostok.registerDataSource("ds2", cfg2);

        Vostok.withDataSource("ds2", () -> Vostok.insert(user("DS2", 1)));
        assertEquals(0, Vostok.findAll(UserEntity.class).size());
        Vostok.withDataSource("ds2", () -> assertEquals(1, Vostok.findAll(UserEntity.class).size()));
    }

    @Test
    void testWrapPropagatesDataSource() throws Exception {
        String url = "jdbc:h2:mem:devkit_wrap;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var conn = java.sql.DriverManager.getConnection(url, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), age INT)");
        }

        DataSourceConfig cfg = new DataSourceConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL);
        Vostok.registerDataSource("ds_wrap", cfg);

        var executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Integer> future = Vostok.withDataSource("ds_wrap", () -> {
                Vostok.insert(user("W1", 1));
                return CompletableFuture.supplyAsync(
                        Vostok.wrap(() -> Vostok.findAll(UserEntity.class).size()), executor
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
        Vostok.registerInterceptor(it);
        Vostok.insert(user("Hook", 1));
        assertTrue(it.before > 0);
        assertTrue(it.after > 0);
        Vostok.clearInterceptors();
    }

    @Test
    void testPoolMetricsAndReport() {
        var metrics = Vostok.poolMetrics();
        assertFalse(metrics.isEmpty());
        String report = Vostok.report();
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
        VKDialectManager.init(new DataSourceConfig().dialect(VKDialectType.SQLSERVER));
        String sql1 = SqlBuilder.buildSelect(meta, VKQuery.create().limit(10).offset(5)).getSql();
        assertTrue(sql1.contains("OFFSET 5 ROWS"));
        assertTrue(sql1.contains("FETCH NEXT 10 ROWS ONLY"));

        VKDialectManager.init(new DataSourceConfig().dialect(VKDialectType.DB2));
        String sql2 = SqlBuilder.buildSelect(meta, VKQuery.create().limit(10).offset(5)).getSql();
        assertTrue(sql2.contains("OFFSET 5 ROWS"));
        assertTrue(sql2.contains("FETCH FIRST 10 ROWS ONLY"));

        VKDialectManager.init(new DataSourceConfig().dialect(VKDialectType.MYSQL));
    }

    @Test
    @Order(96)
    void testIdleValidationEvict() throws Exception {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
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
        Vostok.init(cfg, "yueyang.vostok");

        Vostok.findAll(UserEntity.class);

        long deadline = System.currentTimeMillis() + 500;
        int total;
        do {
            Thread.sleep(20);
            total = Vostok.poolMetrics().get(0).getTotal();
        } while (total > 0 && System.currentTimeMillis() < deadline);
        assertEquals(0, total);

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(97)
    void testPreheatAndLeakStack() throws Exception {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
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
        Vostok.init(cfg, "yueyang.vostok");

        var metrics = Vostok.poolMetrics().get(0);
        assertTrue(metrics.getIdle() >= 2);

        var holder = yueyang.vostok.ds.VKDataSourceRegistry.getDefault();
        try (var conn = holder.getDataSource().getConnection()) {
            Thread.sleep(20);
        }
        String report = Vostok.report();
        assertTrue(report.contains("LeakStack"));

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(98)
    void testTxTimeout() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .txTimeoutMs(30)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        assertThrows(yueyang.vostok.exception.VKTxException.class, () -> {
            Vostok.tx(() -> {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Vostok.findAll(UserEntity.class);
            });
        });

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(94)
    void testConnectionStateResetOnReturn() throws Exception {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(1)
                .maxWaitMs(10000)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        var holder = yueyang.vostok.ds.VKDataSourceRegistry.getDefault();
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
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(93)
    void testStatementTimeoutInTransaction() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .txTimeoutMs(900)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        assertThrows(VKException.class, () -> {
            Vostok.tx(() -> {
                Vostok.insert(user("Sleep", 1));
                VKQuery q = VKQuery.create()
                        .where(VKCondition.raw("SLEEP(1200)", VKOperator.GE, 0));
                Vostok.query(UserEntity.class, q);
            });
        });

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(95)
    void testTxIsolationRestoredAfterCommit() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(1)
                .maxWaitMs(10000)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        var holder = yueyang.vostok.ds.VKDataSourceRegistry.getDefault();
        int defaultIsolation;
        try (Connection conn = holder.getDataSource().getConnection()) {
            defaultIsolation = conn.getTransactionIsolation();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Vostok.tx(() -> Vostok.findAll(UserEntity.class), VKTxPropagation.REQUIRED, VKTxIsolation.SERIALIZABLE, false);

        try (Connection conn = holder.getDataSource().getConnection()) {
            assertEquals(defaultIsolation, conn.getTransactionIsolation());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(96)
    void testStatementCacheClosedOnReturn() throws Exception {
        var holder = yueyang.vostok.ds.VKDataSourceRegistry.getDefault();
        PreparedStatement ps;
        try (Connection conn = holder.getDataSource().getConnection()) {
            ps = conn.prepareStatement("SELECT 1");
        }
        assertTrue(ps.isClosed());
    }

    @Test
    @Order(97)
    void testConcurrentInitSafety() throws Exception {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
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
                    Vostok.init(cfg, "yueyang.vostok");
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
        assertEquals(1, yueyang.vostok.ds.VKDataSourceRegistry.all().size());

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(92)
    void testConcurrentRefreshMeta() throws Exception {
        int threads = 6;
        int loops = 50;
        var pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < loops; j++) {
                        Vostok.refreshMeta("yueyang.vostok");
                        Vostok.findAll(UserEntity.class);
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

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertTrue(errors.isEmpty());
    }

    @Test
    @Order(91)
    void testQueryTimeoutNonTx() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .queryTimeoutMs(200)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        Vostok.insert(user("Sleep2", 2));
        assertThrows(VKException.class, () -> {
            VKQuery q = VKQuery.create()
                    .where(VKCondition.raw("SLEEP(600)", VKOperator.GE, 0));
            Vostok.query(UserEntity.class, q);
        });

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(90)
    void testRawAndSubqueryWhitelist() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        Vostok.registerRawSql("1");
        Vostok.registerSubquery("SELECT id FROM t_user WHERE age >= ?");

        Vostok.insert(user("W1", 10));

        VKQuery okRaw = VKQuery.create()
                .where(VKCondition.raw("1", VKOperator.GE, 0));
        List<UserEntity> okRawList = Vostok.query(UserEntity.class, okRaw);
        assertEquals(1, okRawList.size());

        VKQuery okSub = VKQuery.create()
                .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 0));
        List<UserEntity> list = Vostok.query(UserEntity.class, okSub);
        assertEquals(1, list.size());

        VKQuery badRaw = VKQuery.create()
                .where(VKCondition.raw("2", VKOperator.GE, 0));
        assertThrows(VKException.class, () -> Vostok.query(UserEntity.class, badRaw));

        VKQuery badSub = VKQuery.create()
                .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 0, 1));
        assertThrows(VKException.class, () -> Vostok.query(UserEntity.class, badSub));

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(89)
    void testCustomScanner() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");

        Vostok.setScanner((pkgs) -> Set.of(UserEntity.class, TaskEntity.class));
        Vostok.init(cfg, "ignored.pkg");

        assertEquals(2, yueyang.vostok.meta.MetaRegistry.size());

        // restore default scanner + config for remaining tests
        Vostok.setScanner(yueyang.vostok.scan.ClassScanner::scan);
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
        Vostok.registerRawSql("COUNT(1)", "SLEEP(1200)", "SLEEP(600)", "SLEEP(10)");
        Vostok.registerSubquery(
                "SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?",
                "SELECT id FROM t_user WHERE age >= ?"
        );
    }

    @Test
    @Order(88)
    void testWhitelistIsolatedByDataSource() throws Exception {
        DataSourceConfig cfg2 = new DataSourceConfig()
                .url("jdbc:h2:mem:ds2;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");
        Vostok.registerDataSource("ds2", cfg2);

        try (var conn = java.sql.DriverManager.getConnection(cfg2.getUrl(), "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS t_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64) NOT NULL, age INT)");
            stmt.execute("DELETE FROM t_user");
        }

        Vostok.registerRawSql("ds2", new String[]{"1"});

        Vostok.insert(user("D1", 1));
        Vostok.withDataSource("ds2", () -> Vostok.insert(user("D2", 2)));

        VKQuery raw = VKQuery.create()
                .where(VKCondition.raw("1", VKOperator.GE, 0));

        assertThrows(VKException.class, () -> Vostok.query(UserEntity.class, raw));
        Vostok.withDataSource("ds2", () -> {
            List<UserEntity> list = Vostok.query(UserEntity.class, raw);
            assertEquals(1, list.size());
        });
    }

    @Test
    @Order(99)
    void testConcurrencyPressure() throws Exception {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(2)
                .maxActive(20)
                .maxWaitMs(30000)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        int threads = 10;
        int loops = 50;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var latch = new java.util.concurrent.CountDownLatch(threads);
        var errors = new java.util.concurrent.atomic.AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < loops; j++) {
                        Vostok.insert(user("U-" + idx + "-" + j, j));
                        Vostok.findAll(UserEntity.class);
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

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(100)
    void testPoolStabilityUnderLoad() throws Exception {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(3)
                .maxWaitMs(10000)
                .validationQuery("SELECT 1");
        Vostok.init(cfg, "yueyang.vostok");

        int threads = 20;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var latch = new java.util.concurrent.CountDownLatch(threads);
        var errors = new java.util.concurrent.atomic.AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    Vostok.findAll(UserEntity.class);
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

        // restore default config for remaining tests
        Vostok.close();
        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    @Order(101)
    void testDdlValidation() {
        Vostok.close();
        String url = "jdbc:h2:mem:ddltest;MODE=MySQL;DB_CLOSE_DELAY=-1";
        DataSourceConfig cfg = new DataSourceConfig()
                .url(url)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validateDdl(true);
        assertThrows(VKException.class, () -> Vostok.init(cfg, "yueyang.vostok"));
        Vostok.close();

        DataSourceConfig cfg2 = new DataSourceConfig()
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
        Vostok.init(cfg2, "yueyang.vostok");
    }

    @Test
    void testBatchFailStrategyContinue() {
        Vostok.close();
        DataSourceConfig cfg = new DataSourceConfig()
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
        Vostok.init(cfg, "yueyang.vostok");

        UserEntity ok1 = user("OK1", 1);
        UserEntity bad = user(null, 2); // user_name NOT NULL
        UserEntity ok2 = user("OK2", 3);

        int inserted = Vostok.batchInsert(List.of(ok1, bad, ok2));
        // 部分 JDBC 实现会在失败分片中保留部分成功记录，这里只断言不会抛异常且至少成功 1 条
        assertTrue(inserted >= 1);
        assertTrue(Vostok.findAll(UserEntity.class).size() >= 1);
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
            Vostok.insert(user(null, 1));
            fail("Expected exception");
        } catch (VKSqlException e) {
            assertNotNull(e.getSqlState());
        }
    }

    @Test
    void testMetaRefresh() {
        long before = yueyang.vostok.meta.MetaRegistry.getLastRefreshAt();
        Vostok.refreshMeta("yueyang.vostok");
        long after = yueyang.vostok.meta.MetaRegistry.getLastRefreshAt();
        assertTrue(after >= before);
    }

    @Test
    @Order(103)
    void testNotInitialized() {
        Vostok.close();
        assertThrows(VKStateException.class, () -> Vostok.findAll(UserEntity.class));

        // re-init for remaining tests (if any)
        DataSourceConfig cfg = new DataSourceConfig()
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
        Vostok.init(cfg, "yueyang.vostok");
    }

    private static UserEntity user(String name, int age) {
        UserEntity u = new UserEntity();
        u.setName(name);
        u.setAge(age);
        return u;
    }
}
