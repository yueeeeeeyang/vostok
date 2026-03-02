package yueyang.vostok;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.exception.VKMetaException;
import yueyang.vostok.data.exception.VKOptimisticLockException;
import yueyang.vostok.data.meta.MetaLoader;
import yueyang.vostok.data.query.VKCondition;
import yueyang.vostok.data.query.VKOperator;
import yueyang.vostok.data.query.VKQuery;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据版本（@VKVersion / 乐观锁）与逻辑删除（@VKLogicDelete）功能集成测试。
 * 同时覆盖 @VKColumn.insertable / updatable 属性控制。
 */
class VostokDataVersionLogicDeleteTest {

    private static final String JDBC_URL = "jdbc:h2:mem:vk_vld_test;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @BeforeAll
    static void setUp() throws Exception {
        VKDataConfig cfg = new VKDataConfig()
                .url(JDBC_URL)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .minIdle(1)
                .maxActive(5);
        Vostok.Data.init(cfg, "yueyang.vostok");

        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement()) {

            // 乐观锁测试表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_versioned_order ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                    + "title VARCHAR(200),"
                    + "amount INT,"
                    + "version BIGINT NOT NULL DEFAULT 0"
                    + ")");

            // 逻辑删除测试表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_soft_delete_article ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                    + "title VARCHAR(200),"
                    + "author VARCHAR(100),"
                    + "is_deleted INT NOT NULL DEFAULT 0"
                    + ")");

            // insertable/updatable 测试表
            stmt.execute("CREATE TABLE IF NOT EXISTS t_insert_only_ts ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                    + "content VARCHAR(200),"
                    + "created_at VARCHAR(50)"
                    + ")");
        }
    }

    @AfterAll
    static void tearDown() {
        Vostok.Data.close();
    }

    @BeforeEach
    void cleanUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_versioned_order");
            stmt.execute("DELETE FROM t_soft_delete_article");
            stmt.execute("DELETE FROM t_insert_only_ts");
        }
    }

    // ===== @VKVersion 乐观锁测试 =====

    /**
     * 正常路径：INSERT 时版本字段自动初始化为 0，UPDATE 后版本自动 +1。
     */
    @Test
    void testVersionAutoInitAndIncrement() {
        VersionedOrderEntity order = new VersionedOrderEntity();
        order.setTitle("order1");
        order.setAmount(100);
        // 不设置 version，框架应自动初始化为 0

        Vostok.Data.insert(order);
        assertNotNull(order.getId(), "自增主键应写回");

        // 查询验证 version=0
        VersionedOrderEntity found = Vostok.Data.findById(VersionedOrderEntity.class, order.getId());
        assertNotNull(found);
        assertEquals(0L, found.getVersion(), "INSERT 后版本应为 0");

        // 更新并验证版本递增
        found.setAmount(200);
        int rows = Vostok.Data.update(found);
        assertEquals(1, rows, "更新应影响 1 行");
        assertEquals(1L, found.getVersion(), "UPDATE 后实体版本应自增为 1");

        // 再次查数据库确认
        VersionedOrderEntity updated = Vostok.Data.findById(VersionedOrderEntity.class, order.getId());
        assertEquals(1L, updated.getVersion(), "数据库版本应为 1");
        assertEquals(200, updated.getAmount());
    }

    /**
     * 边界：手动设置 version 值后 INSERT，框架应使用用户设置的值而非强制 0。
     */
    @Test
    void testVersionInsertWithUserSetValue() {
        VersionedOrderEntity order = new VersionedOrderEntity();
        order.setTitle("manual_version");
        order.setAmount(50);
        order.setVersion(5L); // 手动设置版本

        Vostok.Data.insert(order);

        VersionedOrderEntity found = Vostok.Data.findById(VersionedOrderEntity.class, order.getId());
        assertEquals(5L, found.getVersion(), "用户手动设置的版本值应被保留");
    }

    /**
     * 乐观锁冲突：模拟并发场景——同一实体被两个实例读取后先后更新，后更新的应抛异常。
     */
    @Test
    void testVersionConflictThrowsException() {
        VersionedOrderEntity order = new VersionedOrderEntity();
        order.setTitle("conflict_test");
        order.setAmount(10);
        Vostok.Data.insert(order);

        // 模拟两个并发读取同一实体
        VersionedOrderEntity instance1 = Vostok.Data.findById(VersionedOrderEntity.class, order.getId());
        VersionedOrderEntity instance2 = Vostok.Data.findById(VersionedOrderEntity.class, order.getId());

        assertEquals(0L, instance1.getVersion());
        assertEquals(0L, instance2.getVersion());

        // instance1 先更新成功
        instance1.setAmount(20);
        Vostok.Data.update(instance1);
        assertEquals(1L, instance1.getVersion());

        // instance2 更新时版本已过期，应抛 VKOptimisticLockException
        instance2.setAmount(30);
        assertThrows(VKOptimisticLockException.class, () -> Vostok.Data.update(instance2),
                "过期版本更新应抛出 VKOptimisticLockException");
    }

    /**
     * 异常路径：传入 null 实体。
     */
    @Test
    void testVersionUpdateWithNullEntityThrowsException() {
        assertThrows(Exception.class, () -> Vostok.Data.update(null));
    }

    /**
     * 多次更新：版本应连续递增。
     */
    @Test
    void testVersionMultipleUpdates() {
        VersionedOrderEntity order = new VersionedOrderEntity();
        order.setTitle("multi_update");
        order.setAmount(1);
        Vostok.Data.insert(order);

        VersionedOrderEntity current = Vostok.Data.findById(VersionedOrderEntity.class, order.getId());
        for (int i = 1; i <= 5; i++) {
            current.setAmount(i * 10);
            Vostok.Data.update(current);
            assertEquals((long) i, current.getVersion(), "第 " + i + " 次更新后版本应为 " + i);
        }
    }

    // ===== @VKLogicDelete 逻辑删除测试 =====

    /**
     * 正常路径：INSERT 后 is_deleted=0（normalValue），调用 delete() 后为软删（is_deleted=1），
     * findById 返回 null，findAll 不包含该记录。
     */
    @Test
    void testLogicDeleteSoftDeleteBehavior() throws Exception {
        SoftDeleteArticleEntity article = new SoftDeleteArticleEntity();
        article.setTitle("逻辑删除测试");
        article.setAuthor("Alice");
        // isDeleted 不设置，框架应初始化为 0

        Vostok.Data.insert(article);
        assertNotNull(article.getId());

        // 验证 is_deleted=0 写入
        SoftDeleteArticleEntity found = Vostok.Data.findById(SoftDeleteArticleEntity.class, article.getId());
        assertNotNull(found, "INSERT 后应能查到");
        assertEquals(0, found.getIsDeleted(), "isDeleted 应初始化为 0");

        // 软删
        int rows = Vostok.Data.delete(SoftDeleteArticleEntity.class, article.getId());
        assertEquals(1, rows, "软删应影响 1 行");

        // findById 应返回 null（过滤掉已删除记录）
        SoftDeleteArticleEntity afterDelete = Vostok.Data.findById(SoftDeleteArticleEntity.class, article.getId());
        assertNull(afterDelete, "软删后 findById 应返回 null");

        // findAll 不应包含已删除记录
        List<SoftDeleteArticleEntity> all = Vostok.Data.findAll(SoftDeleteArticleEntity.class);
        assertTrue(all.stream().noneMatch(a -> article.getId().equals(a.getId())),
                "findAll 不应包含已软删的记录");

        // 验证数据库中记录仍然存在（物理未删除）
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT is_deleted FROM t_soft_delete_article WHERE id = " + article.getId())) {
            assertTrue(rs.next(), "物理记录应仍存在");
            assertEquals(1, rs.getInt("is_deleted"), "is_deleted 应标记为 1");
        }
    }

    /**
     * 查询过滤：query() 自动注入 is_deleted = 0 过滤，不返回已删除记录。
     */
    @Test
    void testLogicDeleteQueryFiltering() {
        // 插入 3 条，删除 1 条
        SoftDeleteArticleEntity a1 = create("Article A", "Bob");
        SoftDeleteArticleEntity a2 = create("Article B", "Carol");
        SoftDeleteArticleEntity a3 = create("Article C", "Bob");

        Vostok.Data.delete(SoftDeleteArticleEntity.class, a2.getId());

        // 无条件查询应返回 2 条
        List<SoftDeleteArticleEntity> all = Vostok.Data.findAll(SoftDeleteArticleEntity.class);
        assertEquals(2, all.size(), "findAll 应过滤已删除记录");

        // 带条件查询（author=Bob）应返回 2 条（a1, a3）
        VKQuery q = VKQuery.create()
                .where(VKCondition.of("author", VKOperator.EQ, "Bob"));
        List<SoftDeleteArticleEntity> bobArticles = Vostok.Data.query(SoftDeleteArticleEntity.class, q);
        assertEquals(2, bobArticles.size(), "query 应自动过滤已删除记录");

        // count 应返回 2
        long cnt = Vostok.Data.count(SoftDeleteArticleEntity.class, VKQuery.create());
        assertEquals(2L, cnt, "count 应自动过滤已删除记录");
    }

    /**
     * 逻辑删除字段不设置时，INSERT 自动初始化为 normalValue。
     */
    @Test
    void testLogicDeleteNormalValueAutoInit() {
        SoftDeleteArticleEntity article = new SoftDeleteArticleEntity();
        article.setTitle("Auto init test");
        article.setAuthor("Test");
        // 不设置 isDeleted

        Vostok.Data.insert(article);

        SoftDeleteArticleEntity found = Vostok.Data.findById(SoftDeleteArticleEntity.class, article.getId());
        assertNotNull(found);
        assertEquals(0, found.getIsDeleted(), "isDeleted 应自动初始化为 normalValue(0)");
    }

    /**
     * 恢复软删：直接 update() 将 isDeleted 设回 0 应使记录重新可见。
     */
    @Test
    void testLogicDeleteRestore() {
        SoftDeleteArticleEntity article = create("restore_test", "Dave");

        // 软删
        Vostok.Data.delete(SoftDeleteArticleEntity.class, article.getId());
        assertNull(Vostok.Data.findById(SoftDeleteArticleEntity.class, article.getId()));

        // 从数据库手动读回（绕过框架过滤），修改 isDeleted=0 后 update
        // 用 query 会被过滤，只能用 JDBC 直接读取
        try {
            try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
                 var ps = conn.prepareStatement("SELECT * FROM t_soft_delete_article WHERE id = ?")) {
                ps.setLong(1, article.getId());
                var rs = ps.executeQuery();
                assertTrue(rs.next());
                SoftDeleteArticleEntity deleted = new SoftDeleteArticleEntity();
                deleted.setId(rs.getLong("id"));
                deleted.setTitle(rs.getString("title"));
                deleted.setAuthor(rs.getString("author"));
                deleted.setIsDeleted(0); // 恢复
                Vostok.Data.update(deleted);
            }
        } catch (Exception e) {
            fail("恢复操作不应抛出异常: " + e.getMessage());
        }

        // 现在应该能查到
        SoftDeleteArticleEntity restored = Vostok.Data.findById(SoftDeleteArticleEntity.class, article.getId());
        assertNotNull(restored, "恢复后 findById 应能查到");
        assertEquals(0, restored.getIsDeleted());
    }

    /**
     * 批量删除：软删实体批量 delete 应更新 is_deleted 而非物理删除。
     */
    @Test
    void testLogicDeleteBatchDelete() throws Exception {
        SoftDeleteArticleEntity a1 = create("batch1", "Eve");
        SoftDeleteArticleEntity a2 = create("batch2", "Eve");
        SoftDeleteArticleEntity a3 = create("batch3", "Eve");

        int total = Vostok.Data.batchDelete(SoftDeleteArticleEntity.class,
                List.of(a1.getId(), a2.getId()));
        assertEquals(2, total, "批量软删应返回成功行数 2");

        // a3 不受影响
        assertNotNull(Vostok.Data.findById(SoftDeleteArticleEntity.class, a3.getId()));

        // a1, a2 已被软删
        assertNull(Vostok.Data.findById(SoftDeleteArticleEntity.class, a1.getId()));
        assertNull(Vostok.Data.findById(SoftDeleteArticleEntity.class, a2.getId()));

        // 数据库物理记录仍在
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM t_soft_delete_article WHERE is_deleted=1")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "应有 2 条已软删的物理记录");
        }
    }

    // ===== @VKColumn insertable/updatable 测试 =====

    /**
     * insertable=true, updatable=false：字段写入 INSERT，不出现在 UPDATE SET 子句。
     */
    @Test
    void testInsertableOnlyField() {
        InsertOnlyTimestampEntity e = new InsertOnlyTimestampEntity();
        e.setContent("hello");
        e.setCreatedAt("2026-01-01");

        Vostok.Data.insert(e);
        assertNotNull(e.getId());

        InsertOnlyTimestampEntity found = Vostok.Data.findById(InsertOnlyTimestampEntity.class, e.getId());
        assertEquals("2026-01-01", found.getCreatedAt(), "INSERT 后 created_at 应存在");

        // 更新 content，同时尝试修改 createdAt（updatable=false，框架应忽略）
        found.setContent("world");
        found.setCreatedAt("2099-12-31"); // 不会被写入 UPDATE
        Vostok.Data.update(found);

        InsertOnlyTimestampEntity afterUpdate = Vostok.Data.findById(InsertOnlyTimestampEntity.class, e.getId());
        assertEquals("world", afterUpdate.getContent(), "content 应已更新");
        assertEquals("2026-01-01", afterUpdate.getCreatedAt(), "created_at 应保持不变（updatable=false）");
    }

    // ===== 元数据校验测试 =====

    /**
     * @VKVersion 标注在非数值字段上应在 MetaLoader 阶段抛异常。
     */
    @Test
    void testVersionOnNonNumericFieldThrowsMetaException() {
        // 使用匿名内部类模拟非法实体（@VKVersion 标注 String 字段）
        // 直接调用 MetaLoader.load 验证
        assertThrows(VKMetaException.class, () ->
                MetaLoader.load(BadVersionTypeEntity.class),
                "版本字段非数值类型应抛 VKMetaException");
    }

    /**
     * 每个实体最多一个 @VKLogicDelete，重复标注应抛异常。
     */
    @Test
    void testMultipleLogicDeleteFieldsThrowsMetaException() {
        assertThrows(VKMetaException.class, () ->
                MetaLoader.load(BadMultipleLogicDeleteEntity.class),
                "多个逻辑删除字段应抛 VKMetaException");
    }

    // ===== 辅助方法 =====

    private SoftDeleteArticleEntity create(String title, String author) {
        SoftDeleteArticleEntity e = new SoftDeleteArticleEntity();
        e.setTitle(title);
        e.setAuthor(author);
        Vostok.Data.insert(e);
        return e;
    }

    // 用于元数据异常测试的辅助内部实体类
    @yueyang.vostok.util.annotation.VKEntity(table = "bad_version_type")
    static class BadVersionTypeEntity {
        @yueyang.vostok.data.annotation.VKId
        private Long id;

        @yueyang.vostok.data.annotation.VKVersion
        private String version; // 非法：版本字段不能是 String
    }

    @yueyang.vostok.util.annotation.VKEntity(table = "bad_multi_logic_delete")
    static class BadMultipleLogicDeleteEntity {
        @yueyang.vostok.data.annotation.VKId
        private Long id;

        @yueyang.vostok.data.annotation.VKLogicDelete
        private Integer deleted1;

        @yueyang.vostok.data.annotation.VKLogicDelete
        private Integer deleted2; // 非法：重复逻辑删除字段
    }
}
