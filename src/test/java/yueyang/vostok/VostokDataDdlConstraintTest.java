package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.data.ddl.VKDdlValidator;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.meta.EntityMeta;
import yueyang.vostok.data.meta.FieldMeta;
import yueyang.vostok.data.meta.MetaLoader;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@code @VKColumn} 新增的 nullable / length / unique 属性：
 * <ul>
 *   <li>MetaLoader 正确解析注解属性到 FieldMeta</li>
 *   <li>VKDdlValidator 生成的 DDL SQL 包含对应的约束关键字</li>
 *   <li>实际 H2 数据库正确执行建表，并强制 NOT NULL / UNIQUE 约束</li>
 * </ul>
 */
class VostokDataDdlConstraintTest {

    private static final String JDBC_URL = "jdbc:h2:mem:vk_ddl_constraint_test;MODE=MySQL;DB_CLOSE_DELAY=-1";

    // ─────────────────────────────────────────────────────────────────────
    // 1. MetaLoader 属性解析
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void testMetaLoaderParsesNullableFalse() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta email = meta.getFieldByName("email");
        assertNotNull(email, "email 字段应存在");
        assertFalse(email.isNullable(), "email 应为 nullable=false");
    }

    @Test
    void testMetaLoaderParsesLength() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta email = meta.getFieldByName("email");
        assertEquals(100, email.getLength(), "email length 应为 100");

        FieldMeta username = meta.getFieldByName("username");
        assertEquals(50, username.getLength(), "username length 应为 50");
    }

    @Test
    void testMetaLoaderParsesUnique() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta email = meta.getFieldByName("email");
        assertTrue(email.isUnique(), "email 应为 unique=true");
    }

    @Test
    void testMetaLoaderDefaultNullableTrue() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta bio = meta.getFieldByName("bio");
        assertTrue(bio.isNullable(), "bio 默认应为 nullable=true");
    }

    @Test
    void testMetaLoaderDefaultLength255() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta bio = meta.getFieldByName("bio");
        assertEquals(255, bio.getLength(), "bio 默认 length 应为 255");
    }

    @Test
    void testMetaLoaderDefaultUniqueFalse() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta bio = meta.getFieldByName("bio");
        assertFalse(bio.isUnique(), "bio 默认应为 unique=false");
    }

    @Test
    void testMetaLoaderNullableFalseOnIntegerWrapper() {
        EntityMeta meta = MetaLoader.load(DdlConstraintEntity.class);
        FieldMeta score = meta.getFieldByName("score");
        assertFalse(score.isNullable(), "score（Integer 包装类）nullable=false 应被正确解析");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. DDL SQL 字符串验证（通过反射调用 private buildCreateTableSql）
    // ─────────────────────────────────────────────────────────────────────

    private String buildDdl(Class<?> entityClass, VKDialectType dialect) throws Exception {
        EntityMeta meta = MetaLoader.load(entityClass);
        Method m = VKDdlValidator.class.getDeclaredMethod("buildCreateTableSql",
                EntityMeta.class, String.class, VKDialectType.class);
        m.setAccessible(true);
        return (String) m.invoke(null, meta, null, dialect);
    }

    @Test
    void testDdlSqlContainsVarcharLength() throws Exception {
        String sql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        assertTrue(sql.contains("VARCHAR(100)"), "email 列应生成 VARCHAR(100)，实际 SQL: " + sql);
        assertTrue(sql.contains("VARCHAR(50)"), "username 列应生成 VARCHAR(50)，实际 SQL: " + sql);
        assertTrue(sql.contains("VARCHAR(255)"), "bio 列应生成 VARCHAR(255)，实际 SQL: " + sql);
    }

    @Test
    void testDdlSqlContainsNotNullForNullableFalse() throws Exception {
        String sql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        // email 列定义中应包含 NOT NULL
        assertTrue(sql.contains("email VARCHAR(100) NOT NULL"), "email 应生成 NOT NULL，实际 SQL: " + sql);
        // score 列（Integer 包装类 + nullable=false）也应有 NOT NULL
        assertTrue(sql.contains("score INT NOT NULL"), "score 应生成 NOT NULL，实际 SQL: " + sql);
    }

    @Test
    void testDdlSqlDoesNotContainNotNullForNullableTrue() throws Exception {
        String sql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        // bio 是 nullable=true 的普通字段，不应包含 NOT NULL
        assertFalse(sql.contains("bio VARCHAR(255) NOT NULL"), "bio 不应生成 NOT NULL，实际 SQL: " + sql);
    }

    @Test
    void testDdlSqlContainsUnique() throws Exception {
        String sql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        assertTrue(sql.contains("UNIQUE"), "unique=true 字段应在 DDL 中出现 UNIQUE，实际 SQL: " + sql);
        // email 列完整定义
        assertTrue(sql.contains("email VARCHAR(100) NOT NULL UNIQUE"), "email 应生成 NOT NULL UNIQUE，实际 SQL: " + sql);
    }

    @Test
    void testDdlSqlPrimaryKeyNotHasUniqueKeyword() throws Exception {
        String sql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        // 主键列通过 PRIMARY KEY(id) 保证唯一，id 列定义本身不应追加 UNIQUE
        // 提取 id 列定义部分（到第一个逗号为止）
        int idStart = sql.indexOf("id BIGINT");
        assertTrue(idStart >= 0, "SQL 中应包含 id 列定义");
        int idEnd = sql.indexOf(",", idStart);
        String idColDef = sql.substring(idStart, idEnd);
        assertFalse(idColDef.contains("UNIQUE"), "id 主键列定义不应包含冗余 UNIQUE，id 列定义: " + idColDef);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. H2 集成验证：实际执行 DDL，验证约束被数据库强制执行
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 建表后尝试插入 NULL 到 NOT NULL 列，预期数据库抛出异常。
     */
    @Test
    void testAutoCreateTableEnforcesNotNull() throws Exception {
        String ddlSql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_ddl_constraint");
            stmt.execute(ddlSql);
            // email 为 NOT NULL，插入 NULL 值应失败
            assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO t_ddl_constraint (email, username, score) VALUES (NULL, 'alice', 1)"),
                    "向 NOT NULL 列插入 NULL 应抛出 SQLException"
            );
        }
    }

    /**
     * 建表后尝试插入重复值到 UNIQUE 列，预期数据库抛出异常。
     */
    @Test
    void testAutoCreateTableEnforcesUnique() throws Exception {
        String ddlSql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_ddl_constraint_uniq");
            // H2 的 MODE=MySQL 下 CREATE TABLE 中的列名需唯一，重命名表避免冲突
            String renamedDdl = ddlSql.replace("t_ddl_constraint", "t_ddl_constraint_uniq");
            stmt.execute(renamedDdl);
            stmt.execute("INSERT INTO t_ddl_constraint_uniq (email, username, score) VALUES ('a@x.com', 'alice', 1)");
            // 插入相同 email（UNIQUE 列）应失败
            assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO t_ddl_constraint_uniq (email, username, score) VALUES ('a@x.com', 'bob', 2)"),
                    "向 UNIQUE 列插入重复值应抛出 SQLException"
            );
        }
    }

    /**
     * 默认 VARCHAR(255) 长度：bio 列允许插入最长 255 个字符的字符串。
     */
    @Test
    void testDefaultVarcharLengthAcceptsData() throws Exception {
        String ddlSql = buildDdl(DdlConstraintEntity.class, VKDialectType.MYSQL);
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_ddl_constraint_len");
            String renamedDdl = ddlSql.replace("t_ddl_constraint", "t_ddl_constraint_len");
            stmt.execute(renamedDdl);
            String val255 = "a".repeat(255);
            // 插入 255 字符应成功
            assertDoesNotThrow(() ->
                    stmt.execute("INSERT INTO t_ddl_constraint_len (email, username, bio, score) VALUES ('b@x.com', 'bob', '" + val255 + "', 1)"),
                    "bio 默认 VARCHAR(255) 应允许 255 字符"
            );
        }
    }
}
