package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.data.DataResult;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.exception.VKErrorCode;
import yueyang.vostok.data.exception.VKSqlException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokDataExecuteSqlTest {
    private static final String JDBC_URL = "jdbc:h2:mem:data_exec_sql;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final String JDBC_URL_DS2 = "jdbc:h2:mem:data_exec_sql_ds2;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @BeforeEach
    void setUp() {
        Vostok.Data.close();
        Vostok.Data.init(baseConfig(JDBC_URL), "yueyang.vostok");
        Vostok.Data.registerDataSource("ds2", baseConfig(JDBC_URL_DS2));
        createTables(JDBC_URL);
        createTables(JDBC_URL_DS2);
        clearTables(JDBC_URL);
        clearTables(JDBC_URL_DS2);
    }

    @AfterEach
    void tearDown() {
        Vostok.Data.close();
    }

    @Test
    void testExecuteUpdateAndExecuteQuery() {
        int inserted = Vostok.Data.executeUpdate(
                "INSERT INTO t_user(user_name, age) VALUES(?, ?)",
                "Tom", 18
        );
        assertEquals(1, inserted);

        try (DataResult rs = Vostok.Data.executeQuery(
                "SELECT id, user_name, age FROM t_user WHERE user_name = ?",
                "Tom"
        )) {
            assertTrue(rs.next());
            long id = rs.getLong(1);
            assertTrue(id > 0);
            assertEquals("Tom", rs.getString("user_name"));
            assertEquals(18, rs.getInt(3));
            assertFalse(rs.wasNull());
            assertEquals(3, rs.getColumnCount());
            assertEquals("ID", rs.getColumnLabel(1).toUpperCase());
            assertFalse(rs.next());
        }
    }

    @Test
    void testDataResultTypedGettersAndWasNull() {
        Vostok.Data.executeUpdate(
                "INSERT INTO t_user(user_name, age) VALUES(?, ?)",
                "NullAge", null
        );

        LocalDate date = LocalDate.of(2026, 3, 5);
        LocalDateTime dt = LocalDateTime.of(2026, 3, 5, 10, 11, 12);
        Vostok.Data.executeUpdate(
                "INSERT INTO t_task(start_date, finish_time, amount, status) VALUES(?, ?, ?, ?)",
                Date.valueOf(date), Timestamp.valueOf(dt), new BigDecimal("123.45"), "DONE"
        );

        try (DataResult userRs = Vostok.Data.executeQuery(
                "SELECT user_name, age FROM t_user WHERE user_name = ?",
                "NullAge"
        )) {
            assertTrue(userRs.next());
            assertEquals("NullAge", userRs.getString("user_name"));
            assertEquals(0, userRs.getInt("age"));
            assertTrue(userRs.wasNull());
            assertFalse(userRs.next());
        }

        try (DataResult taskRs = Vostok.Data.executeQuery(
                "SELECT start_date, finish_time, amount, status FROM t_task"
        )) {
            assertTrue(taskRs.next());
            assertEquals(date, taskRs.getLocalDate("start_date"));
            assertEquals(dt, taskRs.getLocalDateTime("finish_time"));
            assertEquals(new BigDecimal("123.45"), taskRs.getBigDecimal("amount"));
            assertEquals("DONE", taskRs.getObject("status", String.class));
        }
    }

    @Test
    void testNextAutoCloseAndCloseIdempotent() {
        DataResult rs = Vostok.Data.executeQuery("SELECT 1 AS v");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("v"));
        assertFalse(rs.next());
        rs.close();
    }

    @Test
    void testExecuteWithoutRawWhitelist() {
        try (DataResult rs = Vostok.Data.executeQuery("SELECT CASE WHEN 1=1 THEN 'ok' END AS v")) {
            assertTrue(rs.next());
            assertEquals("ok", rs.getString("v"));
        }
    }

    @Test
    void testTransactionRollbackWithExecuteUpdate() {
        long before = countUsers();
        assertThrows(RuntimeException.class, () -> Vostok.Data.tx(() -> {
            Vostok.Data.executeUpdate("INSERT INTO t_user(user_name, age) VALUES(?, ?)", "TX", 20);
            throw new RuntimeException("rollback");
        }));
        long after = countUsers();
        assertEquals(before, after);
    }

    @Test
    void testWithDataSourceExecuteQuery() {
        Vostok.Data.executeUpdate("INSERT INTO t_user(user_name, age) VALUES(?, ?)", "default-user", 1);
        Vostok.Data.withDataSource("ds2", () ->
                Vostok.Data.executeUpdate("INSERT INTO t_user(user_name, age) VALUES(?, ?)", "ds2-user", 2)
        );

        assertEquals(1, countUsers());
        long ds2Count = Vostok.Data.withDataSource("ds2", this::countUsers);
        assertEquals(1, ds2Count);
    }

    @Test
    void testSqlSyntaxErrorTranslated() {
        VKSqlException ex = assertThrows(VKSqlException.class, () -> Vostok.Data.executeQuery("SELEC id FROM t_user"));
        assertEquals(VKErrorCode.SQL_SYNTAX, ex.getErrorCode());
        assertNotNull(ex.getSqlState());
    }

    private long countUsers() {
        try (DataResult rs = Vostok.Data.executeQuery("SELECT COUNT(*) AS c FROM t_user")) {
            assertTrue(rs.next());
            return rs.getLong("c");
        }
    }

    private static VKDataConfig baseConfig(String jdbcUrl) {
        return new VKDataConfig()
                .url(jdbcUrl)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .validationQuery("SELECT 1");
    }

    private static void createTables(String jdbcUrl) {
        try (var conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS t_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(64) NOT NULL, " +
                    "age INT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS t_task (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "start_date DATE, " +
                    "finish_time TIMESTAMP, " +
                    "amount DECIMAL(18,2), " +
                    "status VARCHAR(20))");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void clearTables(String jdbcUrl) {
        try (var conn = DriverManager.getConnection(jdbcUrl, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_user");
            stmt.execute("DELETE FROM t_task");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
