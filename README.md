# Vostok

---

Vostok 是一个面向 `JDK 17+` 的轻量 Java 框架，提供统一门面 `Vostok`，聚合九个模块能力：

- `Vostok.Data`：基于 JDBC 的数据访问（CRUD、事务、查询、多数据源、连接池）
- `Vostok.Web`：基于 NIO Reactor 的 Web 服务器（路由、中间件、静态资源、自动 CRUD API）
- `Vostok.File`：统一文件访问（本地文本存储默认实现，可扩展 Store）
- `Vostok.Log`：异步日志（滚动、队列策略、降级、指标）
- `Vostok.Config`：统一配置访问（自动扫描 `.properties/.yml/.yaml`、按文件名命名空间读取、支持手动追加任意路径文件）
- `Vostok.Security`：安全检测工具集（SQL 注入、XSS、命令注入、路径穿越、响应脱敏、文件魔数与脚本上传检测）
- `Vostok.Event`：进程内事件总线（统一 `publish(...)`，监听器支持同步/异步）
- `Vostok.Cache`：统一缓存访问（支持 Redis 或内存 Provider、内置连接池、可扩展编解码器）
- `Vostok.Http`：统一 HTTP Client（命名 Client、鉴权、重试、超时、JSON/表单/文件上传）

项目构建方式为 Maven：`/Users/yueyang/Develop/code/codex/Vostok/pom.xml`。

---

# 1. 注意事项

- 当前项目定位为实验与技术验证，不建议直接用于生产环境。
- 运行环境为 `JDK 17+`。
- 数据模块为纯 JDBC 模式，生产环境需自行提供对应数据库驱动。
- `Vostok.Data` 在调用前必须先 `init(...)`。
- `Vostok.Web` 在调用 `start()` 前必须先 `init(...)` 并注册路由。
- `Vostok.File` 在文件操作前必须先 `init(...)`。
- `Vostok.Log` 可显式 `init(...)`，也支持首次写日志时懒加载。
- `Vostok.Config` 支持 `init(...)` 显式初始化；未 `init(...)` 时首次读取会懒加载默认配置。
- `Vostok.Security` 为主动调用型模块，不会自动接入 `Data/Web` 执行链路，需要在业务代码中显式调用。
- `Vostok.Event` 仅提供一个发布方法 `publish(...)`；同步/异步行为由监听器注册模式决定。
- `Vostok.Cache` 不依赖 `Vostok.Config`，必须通过 `VKCacheConfig` 或显式 Loader 初始化。
- `Vostok.Http` 建议显式 `init(...)` 后再使用；如调用相对路径，必须先注册带 `baseUrl` 的命名 Client。
- `Vostok.Http` 默认会对非 `2xx` 响应抛出异常（可通过 `failOnNon2xx(false)` 关闭）。
- `Vostok.Security` 的检测结果是风险判断，不替代参数化查询、鉴权、最小权限、WAF/主机安全等基础安全控制。
- 对于 `Vostok.Security` 的响应脱敏与文件检测能力，建议与业务字段分级、上传大小限制、存储隔离与病毒扫描联合使用。

---

---

# 2. Data 模块

## 2.1 接口定义

```java
public interface Vostok.Data {
    /**
     * 初始化 Data 模块（幂等，重复调用会忽略后续初始化）。
     *
     * - config：数据模块配置，类型为 VKDataConfig，不能为空。
     * - basePackages：实体扫描包路径，类型为 String...；为空时按全 classpath 扫描。
     */
    public static void init(VKDataConfig config, String... basePackages);

    /**
     * 注册命名数据源。
     *
     * - name：数据源名称，类型为 String，不能为空且不能重复。
     * - config：数据源配置，类型为 VKDataConfig，不能为空。
     */
    public static void registerDataSource(String name, VKDataConfig config);

    /**
     * 按初始化时保存的包路径刷新元数据（并同步刷新 SQL 模板缓存）。
     */
    public static void refreshMeta();

    /**
     * 按指定包路径刷新元数据（并同步刷新 SQL 模板缓存）。
     *
     * - basePackages：实体扫描包路径，类型为 String...。
     */
    public static void refreshMeta(String... basePackages);

    /**
     * 设置实体扫描器实现。
     *
     * - scanner：扫描器函数，类型为 VKScanner.EntityScanner，不能为空。
     */
    public static void setScanner(VKScanner.EntityScanner scanner);

    /**
     * 在指定数据源上下文中执行无返回值逻辑。
     *
     * - name：数据源名称，类型为 String。
     * - action：待执行逻辑，类型为 Runnable，不能为空。
     */
    public static void withDataSource(String name, Runnable action);

    /**
     * 在指定数据源上下文中执行有返回值逻辑。
     *
     * - name：数据源名称，类型为 String。
     * - supplier：待执行逻辑，类型为 Supplier<T>，不能为空。
     * - 返回值：T（supplier 的返回结果）。
     */
    public static <T> T withDataSource(String name, Supplier<T> supplier);

    /**
     * 捕获当前线程上下文（当前主要包含数据源上下文）。
     *
     * - 返回值：VostokContext（可用于异步线程显式传播）。
     */
    public static VostokContext captureContext();

    /**
     * 捕获当前上下文并包装 Runnable。
     *
     * - action：原始逻辑，类型为 Runnable，不能为空。
     * - 返回值：Runnable（执行时会恢复捕获上下文）。
     */
    public static Runnable wrap(Runnable action);

    /**
     * 捕获当前上下文并包装 Supplier。
     *
     * - supplier：原始逻辑，类型为 Supplier<T>，不能为空。
     * - 返回值：Supplier<T>（执行时会恢复捕获上下文）。
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier);

    /**
     * 使用指定上下文包装 Runnable。
     *
     * - context：已捕获上下文，类型为 VostokContext，不能为空。
     * - action：原始逻辑，类型为 Runnable，不能为空。
     * - 返回值：Runnable（执行时恢复指定上下文）。
     */
    public static Runnable wrap(VostokContext context, Runnable action);

    /**
     * 使用指定上下文包装 Supplier。
     *
     * - context：已捕获上下文，类型为 VostokContext，不能为空。
     * - supplier：原始逻辑，类型为 Supplier<T>，不能为空。
     * - 返回值：Supplier<T>（执行时恢复指定上下文）。
     */
    public static <T> Supplier<T> wrap(VostokContext context, Supplier<T> supplier);

    /**
     * 注册 SQL 拦截器。
     *
     * - interceptor：拦截器实现，类型为 VKInterceptor，不能为空。
     */
    public static void registerInterceptor(VKInterceptor interceptor);

    /**
     * 注册默认数据源 raw SQL 白名单。
     *
     * - sqls：允许的 raw SQL 片段列表，类型为 String...。
     */
    public static void registerRawSql(String... sqls);

    /**
     * 注册指定数据源 raw SQL 白名单。
     *
     * - dataSourceName：数据源名称，类型为 String。
     * - sqls：允许的 raw SQL 片段列表，类型为 String[]。
     */
    public static void registerRawSql(String dataSourceName, String[] sqls);

    /**
     * 注册默认数据源 subquery 白名单。
     *
     * - sqls：允许的子查询 SQL 列表，类型为 String...。
     */
    public static void registerSubquery(String... sqls);

    /**
     * 注册指定数据源 subquery 白名单。
     *
     * - dataSourceName：数据源名称，类型为 String。
     * - sqls：允许的子查询 SQL 列表，类型为 String[]。
     */
    public static void registerSubquery(String dataSourceName, String[] sqls);

    /**
     * 清空所有 SQL 拦截器。
     */
    public static void clearInterceptors();

    /**
     * 关闭 Data 模块并释放数据源、连接池、元数据缓存资源。
     */
    public static void close();

    /**
     * 在事务中执行无返回值逻辑（默认传播行为与隔离级别）。
     *
     * - action：事务逻辑，类型为 Runnable，不能为空。
     */
    public static void tx(Runnable action);

    /**
     * 在事务中执行无返回值逻辑（指定传播行为、隔离级别）。
     *
     * - action：事务逻辑，类型为 Runnable，不能为空。
     * - propagation：事务传播行为，类型为 VKTxPropagation。
     * - isolation：事务隔离级别，类型为 VKTxIsolation。
     */
    public static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation);

    /**
     * 在事务中执行无返回值逻辑（指定传播行为、隔离级别、只读）。
     *
     * - action：事务逻辑，类型为 Runnable，不能为空。
     * - propagation：事务传播行为，类型为 VKTxPropagation。
     * - isolation：事务隔离级别，类型为 VKTxIsolation。
     * - readOnly：是否只读，类型为 boolean。
     */
    public static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly);

    /**
     * 在事务中执行有返回值逻辑（默认传播行为与隔离级别）。
     *
     * - supplier：事务逻辑，类型为 Supplier<T>，不能为空。
     * - 返回值：T（supplier 的返回结果）。
     */
    public static <T> T tx(Supplier<T> supplier);

    /**
     * 在事务中执行有返回值逻辑（指定传播行为、隔离级别）。
     *
     * - supplier：事务逻辑，类型为 Supplier<T>，不能为空。
     * - propagation：事务传播行为，类型为 VKTxPropagation。
     * - isolation：事务隔离级别，类型为 VKTxIsolation。
     * - 返回值：T（supplier 的返回结果）。
     */
    public static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation);

    /**
     * 在事务中执行有返回值逻辑（指定传播行为、隔离级别、只读）。
     *
     * - supplier：事务逻辑，类型为 Supplier<T>，不能为空。
     * - propagation：事务传播行为，类型为 VKTxPropagation。
     * - isolation：事务隔离级别，类型为 VKTxIsolation。
     * - readOnly：是否只读，类型为 boolean。
     * - 返回值：T（supplier 的返回结果）。
     */
    public static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly);

    /**
     * 手动开启事务（默认传播行为与隔离级别）。
     */
    public static void beginTx();

    /**
     * 手动开启事务（指定传播行为、隔离级别）。
     *
     * - propagation：事务传播行为，类型为 VKTxPropagation。
     * - isolation：事务隔离级别，类型为 VKTxIsolation。
     */
    public static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation);

    /**
     * 手动开启事务（指定传播行为、隔离级别、只读）。
     *
     * - propagation：事务传播行为，类型为 VKTxPropagation。
     * - isolation：事务隔离级别，类型为 VKTxIsolation。
     * - readOnly：是否只读，类型为 boolean。
     */
    public static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly);

    /**
     * 提交当前线程事务。
     */
    public static void commitTx();

    /**
     * 回滚当前线程事务。
     */
    public static void rollbackTx();

    /**
     * 插入单条实体。
     *
     * - entity：实体对象，类型为 Object，不能为空。
     * - 返回值：int（受影响行数，通常为 1）。
     */
    public static int insert(Object entity);

    /**
     * 批量插入实体（按配置 batchSize 分片执行）。
     *
     * - entities：实体列表，类型为 List<?>，不能为空且不能包含不同实体类型。
     * - 返回值：int（成功写入总行数）。
     */
    public static int batchInsert(List<?> entities);

    /**
     * 批量插入并返回明细结果。
     *
     * - entities：实体列表，类型为 List<?>。
     * - 返回值：VKBatchDetailResult（包含每批次成功/失败明细）。
     */
    public static VKBatchDetailResult batchInsertDetail(List<?> entities);

    /**
     * 更新单条实体（按主键更新）。
     *
     * - entity：实体对象，类型为 Object，不能为空。
     * - 返回值：int（受影响行数）。
     */
    public static int update(Object entity);

    /**
     * 批量更新实体（按主键更新）。
     *
     * - entities：实体列表，类型为 List<?>。
     * - 返回值：int（成功更新总行数）。
     */
    public static int batchUpdate(List<?> entities);

    /**
     * 批量更新并返回明细结果。
     *
     * - entities：实体列表，类型为 List<?>。
     * - 返回值：VKBatchDetailResult（包含每批次成功/失败明细）。
     */
    public static VKBatchDetailResult batchUpdateDetail(List<?> entities);

    /**
     * 按主键删除单条记录。
     *
     * - entityClass：实体类型，类型为 Class<?>。
     * - idValue：主键值，类型为 Object。
     * - 返回值：int（受影响行数）。
     */
    public static int delete(Class<?> entityClass, Object idValue);

    /**
     * 按主键批量删除记录。
     *
     * - entityClass：实体类型，类型为 Class<?>。
     * - idValues：主键值列表，类型为 List<?>。
     * - 返回值：int（成功删除总行数）。
     */
    public static int batchDelete(Class<?> entityClass, List<?> idValues);

    /**
     * 按主键批量删除并返回明细结果。
     *
     * - entityClass：实体类型，类型为 Class<?>。
     * - idValues：主键值列表，类型为 List<?>。
     * - 返回值：VKBatchDetailResult（包含每批次成功/失败明细）。
     */
    public static VKBatchDetailResult batchDeleteDetail(Class<?> entityClass, List<?> idValues);

    /**
     * 按主键查询单条记录。
     *
     * - entityClass：实体类型，类型为 Class<T>。
     * - idValue：主键值，类型为 Object。
     * - 返回值：T（命中返回实体，未命中返回 null）。
     */
    public static <T> T findById(Class<T> entityClass, Object idValue);

    /**
     * 查询指定实体对应表的全部记录。
     *
     * - entityClass：实体类型，类型为 Class<T>。
     * - 返回值：List<T>。
     */
    public static <T> List<T> findAll(Class<T> entityClass);

    /**
     * 按 VKQuery 条件查询实体列表。
     *
     * - entityClass：实体类型，类型为 Class<T>。
     * - query：查询对象，类型为 VKQuery，不能为空。
     * - 返回值：List<T>。
     */
    public static <T> List<T> query(Class<T> entityClass, VKQuery query);

    /**
     * 按 VKQuery 条件查询指定列并映射到实体。
     *
     * - entityClass：实体类型，类型为 Class<T>。
     * - query：查询对象，类型为 VKQuery。
     * - fields：列对应的实体字段名，类型为 String...。
     * - 返回值：List<T>。
     */
    public static <T> List<T> queryColumns(Class<T> entityClass, VKQuery query, String... fields);

    /**
     * 执行聚合查询（如 COUNT/SUM/MAX/MIN/AVG）。
     *
     * - entityClass：实体类型，类型为 Class<?>。
     * - query：查询条件，类型为 VKQuery。
     * - aggregates：聚合定义，类型为 VKAggregate...。
     * - 返回值：List<Object[]>（每行一个数组，顺序与聚合定义一致）。
     */
    public static List<Object[]> aggregate(Class<?> entityClass, VKQuery query, VKAggregate... aggregates);

    /**
     * 按 VKQuery 条件统计记录数。
     *
     * - entityClass：实体类型，类型为 Class<?>。
     * - query：查询条件，类型为 VKQuery。
     * - 返回值：long（匹配记录总数，不受 limit/offset 影响）。
     */
    public static long count(Class<?> entityClass, VKQuery query);

    /**
     * 预览字段加密迁移计划。
     *
     * - options：迁移参数，类型为 VKCryptoMigrateOptions。
     * - 返回值：VKCryptoMigratePlan（估算行数、批次参数、说明）。
     */
    public static VKCryptoMigratePlan previewEncrypt(VKCryptoMigrateOptions options);

    /**
     * 执行字段加密迁移（明文 -> 密文）。
     *
     * - options：迁移参数，类型为 VKCryptoMigrateOptions（encryptKeyId 必填）。
     * - 返回值：VKCryptoMigrateResult（扫描/更新/跳过/失败统计）。
     */
    public static VKCryptoMigrateResult encryptColumn(VKCryptoMigrateOptions options);

    /**
     * 预览字段解密迁移计划。
     *
     * - options：迁移参数，类型为 VKCryptoMigrateOptions。
     * - 返回值：VKCryptoMigratePlan。
     */
    public static VKCryptoMigratePlan previewDecrypt(VKCryptoMigrateOptions options);

    /**
     * 执行字段解密迁移（密文 -> 明文）。
     *
     * - options：迁移参数，类型为 VKCryptoMigrateOptions。
     * - 返回值：VKCryptoMigrateResult。
     */
    public static VKCryptoMigrateResult decryptColumn(VKCryptoMigrateOptions options);

    /**
     * 获取连接池指标快照。
     *
     * - 返回值：List<VKPoolMetrics>（每个数据源一份指标）。
     */
    public static List<VKPoolMetrics> poolMetrics();

    /**
     * 生成 Data 模块运行报告（连接池、SQL 指标等）。
     *
     * - 返回值：String（多行文本报告）。
     */
    public static String report();

}
```

## 2.2 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.common.annotation.VKEntity;
import yueyang.vostok.common.scan.VKScanner;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.data.config.VKTxIsolation;
import yueyang.vostok.data.config.VKTxPropagation;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.data.migrate.VKCryptoMigrateOptions;
import yueyang.vostok.data.plugin.VKInterceptor;
import yueyang.vostok.data.query.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@VKEntity(table = "t_user")
class User {
    @VKId private Long id;
    @VKColumn(name = "user_name") private String name;
    private Integer age;

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAge(Integer age) { this.age = age; }
}

public class DataApiDemo {
    public static void main(String[] args) {
        VKDataConfig cfg = new VKDataConfig()
                .url("jdbc:h2:mem:vostok;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL);

        // init / scanner / meta
        Vostok.Data.init(cfg, "com.example.entity");
        Vostok.Data.refreshMeta();
        Vostok.Data.refreshMeta("com.example.entity");
        Vostok.Data.setScanner(VKScanner::scan);

        // datasource
        Vostok.Data.registerDataSource("ds2", cfg);
        Vostok.Data.withDataSource("ds2", () -> {});
        Integer x = Vostok.Data.withDataSource("ds2", () -> 1);

        // context capture + wrap
        var ctx = Vostok.Data.captureContext();
        Runnable r1 = Vostok.Data.wrap(() -> {});
        var s1 = Vostok.Data.wrap(() -> 1);
        Runnable r2 = Vostok.Data.wrap(ctx, () -> {});
        var s2 = Vostok.Data.wrap(ctx, () -> 2);
        CompletableFuture.runAsync(r1).join();
        s1.get(); r2.run(); s2.get();

        // interceptor / whitelist
        Vostok.Data.registerInterceptor(new VKInterceptor() {});
        Vostok.Data.registerRawSql("COUNT(1)");
        Vostok.Data.registerRawSql("ds2", new String[]{"COUNT(1)"});
        Vostok.Data.registerSubquery("SELECT id FROM t_user WHERE age >= ?");
        Vostok.Data.registerSubquery("ds2", new String[]{"SELECT id FROM t_user WHERE age >= ?"});
        Vostok.Data.clearInterceptors();

        // tx (lambda)
        Vostok.Data.tx(() -> {});
        Vostok.Data.tx(() -> {}, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT);
        Vostok.Data.tx(() -> {}, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT, false);
        Integer tx1 = Vostok.Data.tx(() -> 1);
        Integer tx2 = Vostok.Data.tx(() -> 2, VKTxPropagation.REQUIRES_NEW, VKTxIsolation.READ_COMMITTED);
        Integer tx3 = Vostok.Data.tx(() -> 3, VKTxPropagation.REQUIRES_NEW, VKTxIsolation.READ_COMMITTED, true);

        // tx (manual)
        Vostok.Data.beginTx();
        Vostok.Data.commitTx();
        Vostok.Data.beginTx(VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT);
        Vostok.Data.rollbackTx();
        Vostok.Data.beginTx(VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT, true);
        Vostok.Data.rollbackTx();

        // CRUD / query
        User u = new User();
        u.setName("tom");
        u.setAge(18);

        int inserted = Vostok.Data.insert(u);
        int bi = Vostok.Data.batchInsert(List.of(u));
        var bid = Vostok.Data.batchInsertDetail(List.of(u));

        u.setName("tom-2");
        int updated = Vostok.Data.update(u);
        int bu = Vostok.Data.batchUpdate(List.of(u));
        var bud = Vostok.Data.batchUpdateDetail(List.of(u));

        int deleted = Vostok.Data.delete(User.class, 1L);
        int bd = Vostok.Data.batchDelete(User.class, List.of(1L, 2L));
        var bdd = Vostok.Data.batchDeleteDetail(User.class, List.of(1L, 2L));

        User one = Vostok.Data.findById(User.class, 1L);
        List<User> all = Vostok.Data.findAll(User.class);

        VKQuery q = VKQuery.create()
                .where(VKCondition.of("age", VKOperator.GE, 18))
                .orderBy(VKOrder.desc("id"))
                .limit(10)
                .offset(0);
        List<User> list = Vostok.Data.query(User.class, q);
        List<User> cols = Vostok.Data.queryColumns(User.class, q, "name");
        List<Object[]> agg = Vostok.Data.aggregate(User.class, VKQuery.create(), VKAggregate.countAll("cnt"));
        long count = Vostok.Data.count(User.class, q);

        // crypto migrate（列迁移：明文 <-> 密文）
        Vostok.Data.registerRawSql("tag = ?");
        VKCryptoMigrateOptions m = new VKCryptoMigrateOptions()
                .table("t_user")
                .idColumn("id")
                .targetColumn("user_name")
                .whereSql("tag = ?")
                .whereParams("A")
                .batchSize(200)
                .encryptKeyId("biz-user");
        var plan = Vostok.Data.previewEncrypt(m);
        var enc = Vostok.Data.encryptColumn(m);
        var dec = Vostok.Data.decryptColumn(new VKCryptoMigrateOptions()
                .table("t_user")
                .idColumn("id")
                .targetColumn("user_name")
                .batchSize(200)
                .allowPlaintextRead(true));

        // metrics / report
        var metrics = Vostok.Data.poolMetrics();
        String report = Vostok.Data.report();

        Vostok.Data.close();
    }
}
```

### 2.2.1 Data 日志接入说明

- Data 模块日志统一直接调用 `Vostok.Log` 接口（`info/warn/error/debug/trace`）。
- Data 模块不定义独立日志门面，也不强制使用 `Vostok.Log.logger("data-xxx")` 自定义 logger。
- Data 日志默认写入 Log 模块当前默认 logger 对应文件（通常由 `VKLogConfig.filePrefix(...)` 决定）。

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.log.VKLogConfig;

Vostok.Log.init(new VKLogConfig()
    .outputDir("logs")
    .filePrefix("app")
    .consoleEnabled(true));

// 后续 Data 内部日志将统一进入 app.log（及滚动文件）
```

### 2.2.2 字段加密（VKColumn.encrypted）

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.common.annotation.VKEntity;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;

@VKEntity(table = "t_user_secret")
class SecureUser {
    @VKId
    private Long id;

    // 启用字段加密，写入时加密、读取时解密
    @VKColumn(name = "secret_name", encrypted = true, keyId = "enc-user")
    private String secretName;

    private Integer age;
}

public class EncryptionDemo {
    public static void main(String[] args) {
        // 1) 初始化密钥存储
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir("./.vostok/keystore")
                .masterKey("replace-with-your-master-key"));

        // 2) 开启 Data 字段加密能力
        VKDataConfig cfg = new VKDataConfig()
                .url("jdbc:h2:mem:vostok;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL)
                .autoCreateTable(true)
                .fieldEncryptionEnabled(true)
                .defaultEncryptionKeyId("enc-default")
                .allowPlaintextRead(false);

        Vostok.Data.init(cfg, "com.example.entity");

        SecureUser u = new SecureUser();
        // DB 实际存储为 vk1:aes:keyId:... 格式密文
        u.secretName = "alice";
        u.age = 20;
        Vostok.Data.insert(u);

        // 读取时自动解密为明文
        SecureUser one = Vostok.Data.findById(SecureUser.class, u.id);
    }
}
```

- `@VKColumn(encrypted = true)` 仅支持 `String` 字段；非 String 会在元数据加载阶段抛异常。
- `keyId` 优先级：字段 `keyId` > `VKDataConfig.defaultEncryptionKeyId`。
- `allowPlaintextRead`：
- `false`：遇到非密文格式值直接抛错（推荐，防止脏数据混入）。
- `true`：允许兼容历史明文数据，按明文返回。
- 受限查询能力（加密字段）：
- `where`/`having` 仅允许 `IS_NULL`、`IS_NOT_NULL`。
- 禁止 `LIKE/IN/BETWEEN/范围比较` 等条件。
- 禁止 `orderBy/groupBy/aggregate` 使用加密字段。

### 2.2.3 字段迁移（明文 <-> 密文）

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.data.migrate.VKCryptoMigrateOptions;

// where 条件必须入 raw 白名单
Vostok.Data.registerRawSql("tag = ?");

VKCryptoMigrateOptions encryptOpt = new VKCryptoMigrateOptions()
        .table("t_order")
        .idColumn("id")
        .targetColumn("buyer_name")
        .whereSql("tag = ?")
        .whereParams("A")
        .batchSize(500)
        .encryptKeyId("biz-order");

var plan = Vostok.Data.previewEncrypt(encryptOpt);
var enc = Vostok.Data.encryptColumn(encryptOpt);

var decPlan = Vostok.Data.previewDecrypt(new VKCryptoMigrateOptions()
        .table("t_order")
        .idColumn("id")
        .targetColumn("buyer_name"));

var dec = Vostok.Data.decryptColumn(new VKCryptoMigrateOptions()
        .table("t_order")
        .idColumn("id")
        .targetColumn("buyer_name")
        .allowPlaintextRead(true)
        .skipOnError(true));
```

- `previewEncrypt/previewDecrypt` 返回估算行数和执行参数，不会更新数据库。
- `encryptColumn` 仅处理明文行；已是密文格式（`vk1:aes:`）会跳过。
- `decryptColumn` 仅处理密文行；遇明文时按 `allowPlaintextRead` 决定抛错或跳过。
- `whereSql` 必须先注册到 `Vostok.Data.registerRawSql(...)` 白名单。

## 2.3 配置详解（VKDataConfig）

```java
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.config.VKBatchFailStrategy;
import yueyang.vostok.data.dialect.VKDialectType;

VKDataConfig cfg = new VKDataConfig()
    // JDBC URL。必填；示例：jdbc:mysql://127.0.0.1:3306/demo
    .url("jdbc:mysql://127.0.0.1:3306/demo")
    // 数据库用户名。必填。
    .username("root")
    // 数据库密码。可为空字符串。
    .password("123456")
    // JDBC 驱动类名。必填；例如 com.mysql.cj.jdbc.Driver。
    .driver("com.mysql.cj.jdbc.Driver")
    // SQL 方言。可选；不设置时按 URL 自动推断。常见：MYSQL/POSTGRESQL/ORACLE/SQLSERVER/DB2。
    .dialect(VKDialectType.MYSQL)
    // 是否执行 DDL 校验。默认 false；开启后初始化时会检查实体与表结构一致性。
    .validateDdl(false)
    // DDL 校验 schema。默认 null；多 schema 数据库建议显式设置。
    .ddlSchema(null)
    // 初始化/refreshMeta 时是否自动创建缺失表。默认 false。
    .autoCreateTable(false)
    // 最小空闲连接数。默认 1；应 <= maxActive。
    .minIdle(1)
    // 最大活动连接数。默认 10；池并发上限。
    .maxActive(10)
    // 借连接最大等待毫秒。默认 30000；超时将抛异常。
    .maxWaitMs(30000)
    // 借出连接时是否校验可用性。默认 false；开启会增加延迟。
    .testOnBorrow(false)
    // 归还连接时是否校验可用性。默认 false；开启会增加开销。
    .testOnReturn(false)
    // 连接校验 SQL。默认 null；设置后优先于 Connection.isValid。
    .validationQuery("SELECT 1")
    // 校验超时秒数。默认 2；用于 validationQuery 或 isValid。
    .validationTimeoutSec(2)
    // 空闲校验与回收间隔毫秒。默认 0（关闭）；>0 时启用周期任务。
    .idleValidationIntervalMs(0)
    // 是否预热连接池。默认 true；初始化时预建 minIdle 连接。
    .preheatEnabled(true)
    // 空闲连接超时毫秒。默认 0（不回收）；>0 时会回收长时间空闲连接。
    .idleTimeoutMs(0)
    // 连接泄露检测阈值毫秒。默认 0（不检测）；>0 时超阈值打印告警。
    .leakDetectMs(0)
    // 每连接 PreparedStatement 缓存大小。默认 50；0 表示不缓存。
    .statementCacheSize(50)
    // 每数据源 SQL 模板缓存大小。默认 200；0 表示不缓存模板。
    .sqlTemplateCacheSize(200)
    // 是否启用 SQL 异常重试。默认 false；只对可重试异常生效。
    .retryEnabled(false)
    // 最大重试次数。默认 2；仅在 retryEnabled=true 时生效。
    .maxRetries(2)
    // 指数退避基数毫秒。默认 50；第 N 次重试延迟按指数增长。
    .retryBackoffBaseMs(50)
    // 指数退避最大毫秒。默认 2000；防止退避时间无限增长。
    .retryBackoffMaxMs(2000)
    // 可重试 SQLState 前缀白名单。默认 {"08","40","57"}；按数据库可调整。
    .retrySqlStatePrefixes("08", "40", "57")
    // 批处理分片大小。默认 500；大批量写入时按此拆分。
    .batchSize(500)
    // 批处理失败策略。默认 FAIL_FAST；CONTINUE 表示跳过失败分片继续。
    .batchFailStrategy(VKBatchFailStrategy.FAIL_FAST)
    // 是否打印 SQL 文本。默认 false；排障时可开启。
    .logSql(false)
    // 是否打印 SQL 参数。默认 false；注意敏感信息暴露风险。
    .logParams(false)
    // 慢 SQL 阈值毫秒。默认 0（关闭）；>0 时超阈值记录慢 SQL。
    .slowSqlMs(0)
    // 是否启用 SQL 耗时分布统计。默认 true；可用于 report/pool 诊断。
    .sqlMetricsEnabled(true)
    // 慢 SQL TopN 数量。默认 0（关闭）；>0 时保留最慢 N 条。
    .slowSqlTopN(0)
    // 是否启用事务 savepoint。默认 true；用于嵌套事务回滚点。
    .savepointEnabled(true)
    // 事务超时毫秒。默认 0（不限制）；>0 时超时会触发回滚。
    .txTimeoutMs(0)
    // 非事务 SQL 超时毫秒。默认 0（不限制）；>0 会设置 Statement timeout。
    .queryTimeoutMs(0)
    // 是否启用字段加密（仅 @VKColumn(encrypted=true) 字段生效）。默认 false。
    .fieldEncryptionEnabled(false)
    // 默认字段加密 keyId。默认 data-default；当字段未指定 keyId 时使用。
    .defaultEncryptionKeyId("data-default")
    // 读取时是否允许明文兼容。默认 false；true 时非密文值按明文返回。
    .allowPlaintextRead(false);
```

## 2.4 Data 模块 Options 配置项说明

### 2.4.1 VKCryptoMigrateOptions

用于字段加解密迁移接口：`previewEncrypt`、`encryptColumn`、`previewDecrypt`、`decryptColumn`。

- `dataSourceName`：可选；指定数据源名，不填则使用当前数据源。
- `table`：必填；目标表名（支持 `schema.table`）。
- `idColumn`：必填；分页游标列，建议主键。
- `targetColumn`：必填；待迁移列（当前实现按字符串列处理）。
- `whereSql`：可选；附加筛选表达式，必须先通过 `Vostok.Data.registerRawSql(...)` 注册白名单。
- `whereParams`：可选；`whereSql` 占位符参数。
- `batchSize`：默认 `500`；每批处理行数，必须 `> 0`。
- `maxRows`：默认 `0`（不限制）；限制本次最多扫描行数。
- `dryRun`：默认 `false`；`true` 时只做扫描与转换评估，不执行更新。
- `skipOnError`：默认 `false`；`true` 时单行失败后继续后续行。
- `useTransactionPerBatch`：默认 `true`；每批独立事务。
- `encryptKeyId`：加密迁移必填；调用 `encryptColumn` 时使用。
- `allowPlaintextRead`：解密迁移默认 `false`；`true` 时遇到明文值直接跳过。

---

# 3. Web 模块

## 3.1 接口定义

```java
public interface Vostok.Web {
    /**
     * 使用端口号初始化 Web 模块（会覆盖之前的 Web 配置与路由）。
     *
     * - port：监听端口，类型为 int，建议范围 1~65535。
     * - 返回值：VostokWeb（链式路由注册对象）。
     */
    public static VostokWeb init(int port);

    /**
     * 使用配置对象初始化 Web 模块（会覆盖之前的 Web 配置与路由）。
     *
     * - config：Web 配置对象，类型为 VKWebConfig，不能为空。
     * - 返回值：VostokWeb（链式路由注册对象）。
     */
    public static VostokWeb init(VKWebConfig config);

    /**
     * 启动 Web 服务（阻塞主线程由用户控制，框架内部线程为非 daemon）。
     */
    public static void start();

    /**
     * 停止 Web 服务并释放监听端口、Reactor、AccessLog 等资源。
     */
    public static void stop();

    /**
     * 判断 Web 服务是否已启动。
     *
     * - true：已启动。
     * - false：未启动。
     */
    public static boolean started();

    /**
     * 获取当前 Web 服务实际监听端口。
     *
     * - 返回值：int（实际监听端口）。
     */
    public static int port();

    /**
     * 注册 GET 路由。
     *
     * - path：路由路径，类型为 String，例如 "/ping"、"/users/{id}"。
     * - handler：业务处理器，类型为 VKHandler。
     * - 返回值：VostokWeb（支持链式继续注册路由/中间件）。
     */
    public VostokWeb get(String path, VKHandler handler);

    /**
     * 注册 POST 路由。
     *
     * - path：路由路径，类型为 String。
     * - handler：业务处理器，类型为 VKHandler。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb post(String path, VKHandler handler);

    /**
     * 注册任意 HTTP 方法路由。
     *
     * - method：HTTP 方法，类型为 String，例如 "PUT"、"DELETE"、"PATCH"。
     * - path：路由路径，类型为 String。
     * - handler：业务处理器，类型为 VKHandler。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb route(String method, String path, VKHandler handler);

    /**
     * 扫描实体并注册自动 CRUD API（默认 RESTFUL 风格）。
     *
     * - basePackages：实体扫描包路径，类型为 String...，可空；为空时按默认规则扫描。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb autoCrudApi(String... basePackages);

    /**
     * 扫描实体并注册自动 CRUD API（默认扫描范围 + 默认 RESTFUL 风格）。
     *
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb autoCrudApi();

    /**
     * 扫描实体并注册自动 CRUD API（可选风格）。
     *
     * - style：CRUD API 风格，类型为 VKCrudStyle（RESTFUL/TRADITIONAL），可为 null（null 时默认 RESTFUL）。
     * - basePackages：实体扫描包路径，类型为 String...，可空。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb autoCrudApi(VKCrudStyle style, String... basePackages);

    /**
     * 注册全局中间件（按注册顺序执行）。
     *
     * - middleware：中间件实现，类型为 VKMiddleware。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb use(VKMiddleware middleware);

    /**
     * 挂载静态资源目录。
     *
     * - urlPrefix：URL 前缀，类型为 String，例如 "/assets"。
     * - directory：本地目录路径，类型为 String，例如 "./public"。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb staticDir(String urlPrefix, String directory);

    /**
     * 注册全局异常处理器（用于兜底处理业务异常与运行时异常）。
     *
     * - handler：异常处理器，类型为 VKErrorHandler。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb error(VKErrorHandler handler);

    /**
     * 注册全局限流器（对全部路由生效）。
     *
     * - config：限流配置，类型为 VKRateLimitConfig，不能为空。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb rateLimit(VKRateLimitConfig config);

    /**
     * 注册指定路由限流器（仅命中 method+path 时生效）。
     *
     * - method：HTTP 方法枚举，类型为 VKHttpMethod，例如 VKHttpMethod.GET/VKHttpMethod.POST。
     * - path：路由模板，类型为 String，例如 "/user/{id}"。
     * - config：限流配置，类型为 VKRateLimitConfig，不能为空。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb rateLimit(VKHttpMethod method, String path, VKRateLimitConfig config);

    /**
     * 注册 WebSocket 路由（使用全局 WebSocket 配置）。
     *
     * - path：路由路径，类型为 String，例如 "/ws"。
     * - handler：WebSocket 处理器，类型为 VKWebSocketHandler。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb websocket(String path, VKWebSocketHandler handler);

    /**
     * 注册 WebSocket 路由（使用路由级 WebSocket 配置）。
     *
     * - path：路由路径，类型为 String。
     * - config：WebSocket 配置，类型为 VKWebSocketConfig。
     * - handler：WebSocket 处理器，类型为 VKWebSocketHandler。
     * - 返回值：VostokWeb（支持链式调用）。
     */
    public VostokWeb websocket(String path, VKWebSocketConfig config, VKWebSocketHandler handler);

}
```

```java
public interface VKRequest {
    /**
     * 读取请求 Cookie。
     *
     * - name：Cookie 名称，类型为 String。
     * - 返回值：String（未命中返回 null）。
     */
    public String cookie(String name);

    /**
     * 读取全部 Cookie（键值对）。
     *
     * - 返回值：Map<String, String>。
     */
    public Map<String, String> cookies();

    /**
     * 判断当前请求是否 multipart/form-data。
     *
     * - 返回值：boolean。
     */
    public boolean isMultipart();

    /**
     * 读取 multipart 文本字段。
     *
     * - name：字段名，类型为 String。
     * - 返回值：String（未命中返回 null）。
     */
    public String formField(String name);

    /**
     * 读取 multipart 单文件字段（取第一个）。
     *
     * - name：字段名，类型为 String。
     * - 返回值：VKUploadedFile（未命中返回 null）。
     */
    public VKUploadedFile file(String name);
}
```

```java
public interface VKResponse {
    /**
     * 写入响应 Cookie（快捷形式）。
     *
     * - name：Cookie 名称，类型为 String。
     * - value：Cookie 值，类型为 String。
     * - 返回值：VKResponse（支持链式调用）。
     */
    public VKResponse cookie(String name, String value);

    /**
     * 写入响应 Cookie（完整属性形式）。
     *
     * - cookie：Cookie 对象，类型为 VKCookie（支持 path/domain/maxAge/httpOnly/secure/sameSite）。
     * - 返回值：VKResponse（支持链式调用）。
     */
    public VKResponse cookie(VKCookie cookie);

    /**
     * 删除 Cookie（Max-Age=0）。
     *
     * - name：Cookie 名称，类型为 String。
     * - 返回值：VKResponse（支持链式调用）。
     */
    public VKResponse deleteCookie(String name);
}
```

## 3.2 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.web.VKHttpMethod;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.auto.VKCrudStyle;
import yueyang.vostok.web.http.VKCookie;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.rate.VKRateLimitKeyStrategy;
import yueyang.vostok.web.websocket.VKWebSocketConfig;
import yueyang.vostok.web.websocket.VKWebSocketHandler;
import yueyang.vostok.web.websocket.VKWebSocketSession;

public class WebApiDemo {
    public static void main(String[] args) {
        // init(int)
        Vostok.Web.init(8080)
                .get("/ping", (req, res) -> res.text("ok"))
                .post("/echo", (req, res) -> res.text(req.bodyText()))
                .route("PUT", "/v1/user/1", (req, res) -> res.text("updated"))
                .autoCrudApi("com.example.entity")
                .autoCrudApi()
                .autoCrudApi(VKCrudStyle.TRADITIONAL, "com.example.entity")
                .use((req, res, chain) -> {
                    res.header("X-Trace-Id", "demo");
                    chain.next(req, res);
                })
                .staticDir("/static", "/tmp/www")
                .rateLimit(new VKRateLimitConfig()
                        .capacity(200)
                        .refillTokens(200)
                        .refillPeriodMs(1000)
                        .keyStrategy(VKRateLimitKeyStrategy.IP))
                .rateLimit(VKHttpMethod.POST, "/upload", new VKRateLimitConfig()
                        .capacity(20)
                        .refillTokens(20)
                        .refillPeriodMs(1000))
                .websocket("/ws", new VKWebSocketConfig().pingIntervalMs(15_000), new VKWebSocketHandler() {
                    @Override
                    public void onText(VKWebSocketSession session, String text) {
                        session.sendText("echo:" + text);
                    }
                })
                .error((err, req, res) -> res.status(500).text("custom error"));

        Vostok.Web.start();
        boolean started = Vostok.Web.started();
        int p = Vostok.Web.port();
        Vostok.Web.stop();

        // init(VKWebConfig)
        VKWebConfig cfg = new VKWebConfig().port(0);
        Vostok.Web.init(cfg)
                .get("/health", (req, res) -> res.json("{\"ok\":true}"));
        Vostok.Web.start();
        Vostok.Web.stop();
    }
}
```

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKCookie;

public class WebCookieMultipartDemo {
    public static void main(String[] args) {
        VKWebConfig cfg = new VKWebConfig()
                .port(8080)
                // 默认 true，此处显式展示
                .multipartEnabled(true)
                .multipartInMemoryThresholdBytes(64 * 1024)
                .multipartTempDir("/tmp/vostok-upload")
                .multipartMaxParts(128)
                .multipartMaxFileSizeBytes(16L * 1024 * 1024)
                .multipartMaxTotalBytes(32L * 1024 * 1024);

        Vostok.Web.init(cfg)
                .get("/me", (req, res) -> {
                    String sid = req.cookie("sid");
                    res.cookie(new VKCookie("sid", sid == null ? "new-session" : sid)
                                    .path("/")
                                    .httpOnly(true)
                                    .sameSite(VKCookie.SameSite.LAX))
                            .json("{\"sid\":\"" + (sid == null ? "new-session" : sid) + "\"}");
                })
                .post("/upload", (req, res) -> {
                    String title = req.formField("title");
                    var file = req.file("file");
                    if (file == null) {
                        res.status(400).text("file missing");
                        return;
                    }
                    res.json("{\"title\":\"" + title + "\",\"name\":\"" + file.fileName() + "\",\"size\":" + file.size() + "}");
                });

        Vostok.Web.start();
    }
}
```

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKUploadedFile;

import java.nio.file.Path;

/**
 * Multipart 详细示例：展示从 request 获取上传对象后如何使用。
 */
public class WebMultipartDetailDemo {
    public static void main(String[] args) {
        VKWebConfig cfg = new VKWebConfig()
                .port(8080)
                .multipartEnabled(true)
                // 小于 64KB 的文件留在内存；超过阈值自动落临时文件
                .multipartInMemoryThresholdBytes(64 * 1024)
                .multipartTempDir("/tmp/vostok-upload")
                .multipartMaxParts(128)
                .multipartMaxFileSizeBytes(50L * 1024 * 1024)
                .multipartMaxTotalBytes(100L * 1024 * 1024);

        Vostok.Web.init(cfg)
                .post("/upload/detail", (req, res) -> {
                    if (!req.isMultipart()) {
                        res.status(400).text("content-type must be multipart/form-data");
                        return;
                    }

                    // 1) 读取普通表单字段
                    String bizType = req.formField("bizType");
                    String owner = req.formField("owner");

                    // 2) 读取文件字段（单文件）
                    VKUploadedFile avatar = req.file("avatar");
                    if (avatar == null) {
                        res.status(400).text("avatar is required");
                        return;
                    }

                    // 3) 读取多文件字段（同名 input）
                    var attachments = req.files("attachments");

                    // 4) 使用上传对象：元信息
                    String avatarName = avatar.fileName();
                    long avatarSize = avatar.size();
                    String avatarType = avatar.contentType();
                    boolean avatarInMemory = avatar.inMemory();

                    // 5) 使用上传对象：保存到业务目录
                    Path saveTo = Path.of("/tmp/biz-upload", avatarName);
                    avatar.transferTo(saveTo);

                    // 6) 也可直接取字节 / 输入流
                    byte[] avatarBytes = avatar.bytes();
                    int bytesLen = avatarBytes.length;
                    try (var in = avatar.inputStream()) {
                        // do something
                    } catch (Exception e) {
                        res.status(500).text("read upload stream failed");
                        return;
                    }

                    res.json("{\"ok\":true,\"bizType\":\"" + bizType
                            + "\",\"owner\":\"" + owner
                            + "\",\"avatarName\":\"" + avatarName
                            + "\",\"avatarType\":\"" + avatarType
                            + "\",\"avatarSize\":" + avatarSize
                            + ",\"avatarInMemory\":" + avatarInMemory
                            + ",\"avatarBytes\":" + bytesLen
                            + ",\"attachments\":" + attachments.size() + "}");
                });

        Vostok.Web.start();
    }
}
```

```bash
# 触发上面 /upload/detail 的示例请求
curl -X POST "http://127.0.0.1:8080/upload/detail" \
  -F "bizType=image" \
  -F "owner=neo" \
  -F "avatar=@/path/to/avatar.png" \
  -F "attachments=@/path/to/a.pdf" \
  -F "attachments=@/path/to/b.pdf"
```

## 3.3 配置详解（VKWebConfig）

```java
import yueyang.vostok.web.VKWebConfig;

VKWebConfig cfg = new VKWebConfig()
    // 监听端口。默认 8080；设为 0 表示随机可用端口。
    .port(8080)
    // IO Reactor 线程数。默认 1；内部会强制 >=1。
    .ioThreads(1)
    // 业务线程池线程数。默认 max(2, CPU*2)；内部会强制 >=1。
    .workerThreads(Math.max(2, Runtime.getRuntime().availableProcessors() * 2))
    // ServerSocket backlog。默认 1024；内部会强制 >=1。
    .backlog(1024)
    // 每连接读缓冲区大小（字节）。默认 16KB；内部最小 1024。
    .readBufferSize(16 * 1024)
    // 最大请求头字节数。默认 32KB；内部最小 4096。
    .maxHeaderBytes(32 * 1024)
    // 最大请求体字节数。默认 4MB；内部最小 1024。
    .maxBodyBytes(4 * 1024 * 1024)
    // Keep-Alive 空闲超时毫秒。默认 30000；内部最小 1000。
    .keepAliveTimeoutMs(30_000)
    // 最大连接数。默认 10000；超出后新连接会被拒绝。内部最小 1。
    .maxConnections(10_000)
    // 读请求超时毫秒。默认 15000；内部最小 1000。
    .readTimeoutMs(15_000)
    // 业务线程池队列长度。默认 10000；内部最小 1。
    .workerQueueSize(10_000)
    // 是否启用 AccessLog。默认 true。
    .accessLogEnabled(true)
    // 是否启用限流拒绝日志。默认 true。
    .rateLimitLogEnabled(true)
    // AccessLog 异步队列长度。默认 8192；内部最小 256。
    .accessLogQueueSize(8_192)
    // 是否启用 multipart/form-data 解析。默认 true。
    .multipartEnabled(true)
    // multipart 临时文件目录（大文件超阈值后写入该目录）。默认 ${java.io.tmpdir}/vostok-upload。
    .multipartTempDir("/tmp/vostok-upload")
    // multipart 文件内存阈值（字节）。默认 64KB；内部最小 1024。
    .multipartInMemoryThresholdBytes(64 * 1024)
    // multipart 最大 part 数。默认 128；内部最小 1。
    .multipartMaxParts(128)
    // multipart 单文件最大字节数。默认 16MB；内部最小 1024。
    .multipartMaxFileSizeBytes(16L * 1024 * 1024)
    // multipart 总字节数限制。默认 32MB；内部最小 1024。
    .multipartMaxTotalBytes(32L * 1024 * 1024)
    // 限流清理间隔（当前版本用于配置保留，单位毫秒）。默认 60000；内部最小 1000。
    .rateLimitCleanupIntervalMs(60_000)
    // 是否启用 WebSocket。默认 true。
    .websocketEnabled(true)
    // WebSocket 单帧最大 payload（字节）。默认 1MB。
    .websocketMaxFramePayloadBytes(1024 * 1024)
    // WebSocket 单消息最大字节。默认 4MB。
    .websocketMaxMessageBytes(4 * 1024 * 1024)
    // WebSocket 连接写队列最大帧数。默认 1024。
    .websocketMaxPendingFrames(1024)
    // WebSocket 连接写队列最大字节。默认 8MB。
    .websocketMaxPendingBytes(8 * 1024 * 1024)
    // WebSocket ping 间隔毫秒。默认 30000。
    .websocketPingIntervalMs(30_000)
    // WebSocket pong 超时毫秒。默认 10000。
    .websocketPongTimeoutMs(10_000)
    // WebSocket 空闲超时毫秒。默认 120000。
    .websocketIdleTimeoutMs(120_000);
```

## 3.4 Options 配置详解（Web）

### 3.4.1 限流配置（VKRateLimitConfig）

```java
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.rate.VKRateLimitKeyStrategy;

VKRateLimitConfig limit = new VKRateLimitConfig()
    // 令牌桶容量（桶中最多令牌数）。默认 100；内部最小 1。
    .capacity(100)
    // 每个补充周期新增令牌数。默认 100；内部最小 1。
    .refillTokens(100)
    // 补充周期（毫秒）。默认 1000；内部最小 1。
    .refillPeriodMs(1000)
    // 限流 key 计算策略。默认 IP。
    // IP：按客户端 IP 限流
    // TRACE_ID：按请求 traceId 限流
    // HEADER：按指定请求头限流
    // CUSTOM：按自定义函数限流
    .keyStrategy(VKRateLimitKeyStrategy.IP)
    // HEADER 模式时读取的请求头名。默认 X-Rate-Limit-Key。
    .headerName("X-Rate-Limit-Key")
    // CUSTOM 模式时的 key 生成函数（req -> key）。
    .customKeyResolver(req -> req.queryParam("tenantId"))
    // 超限响应状态码。默认 429；<=0 时回退为 429。
    .rejectStatus(429)
    // 超限响应内容。默认 "Too Many Requests"；null 时按空串。
    .rejectBody("Too Many Requests");
```

```java
// 全局限流
Vostok.Web.init(8080)
    .rateLimit(new VKRateLimitConfig()
        .capacity(500)
        .refillTokens(500)
        .refillPeriodMs(1000));

// 路由限流（method 改为枚举，避免误传）
Vostok.Web.init(8080)
    .rateLimit(VKHttpMethod.POST, "/api/order/create", new VKRateLimitConfig()
        .capacity(50)
        .refillTokens(50)
        .refillPeriodMs(1000)
        .keyStrategy(VKRateLimitKeyStrategy.HEADER)
        .headerName("X-User-Id"));
```

```java
// Web 日志分流初始化建议（access.log / ratelimit.log）
// 说明：
// 1) 若 Vostok.Log 尚未初始化，Web 启动时会自动按默认配置初始化并注册：
//    web-access -> access.log
//    web-ratelimit -> ratelimit.log
// 2) 若 Vostok.Log 已初始化，Web 不会 reinit 覆盖用户配置；
//    会尝试调用 Vostok.Log.registerLogger(...)（当前版本若无该接口则回退默认 logger，并告警一次）。
import yueyang.vostok.log.VKLogConfig;
import yueyang.vostok.log.VKLogSinkConfig;

VKLogConfig logCfg = VKLogConfig.defaults()
    .registerLogger("web-access", new VKLogSinkConfig().filePrefix("access"))
    .registerLogger("web-ratelimit", new VKLogSinkConfig().filePrefix("ratelimit"));
Vostok.Log.init(logCfg);
```

### 3.4.2 WebSocket 路由配置（VKWebSocketConfig）

```java
import yueyang.vostok.web.websocket.VKWebSocketConfig;

VKWebSocketConfig ws = new VKWebSocketConfig()
    // 单帧 payload 最大字节数。默认 1MB；内部最小 1024。
    .maxFramePayloadBytes(1024 * 1024)
    // 单消息（含分片聚合）最大字节数。默认 4MB；内部最小 1024。
    .maxMessageBytes(4 * 1024 * 1024)
    // 单连接发送队列最大帧数。默认 1024；内部最小 16。
    .maxPendingFrames(1024)
    // 单连接发送队列最大字节数。默认 8MB；内部最小 1024。
    .maxPendingBytes(8 * 1024 * 1024)
    // 服务端主动 ping 间隔毫秒。默认 30000；内部最小 1000。
    .pingIntervalMs(30_000)
    // 发送 ping 后等待 pong 超时毫秒。默认 10000；内部最小 1000。
    .pongTimeoutMs(10_000)
    // 连接空闲超时毫秒。默认 120000；内部最小 1000。
    .idleTimeoutMs(120_000);
```

```java
import yueyang.vostok.web.websocket.VKWebSocketHandler;
import yueyang.vostok.web.websocket.VKWebSocketSession;

Vostok.Web.init(8080)
    .websocket("/ws/chat", new VKWebSocketConfig()
        .maxMessageBytes(2 * 1024 * 1024)
        .pingIntervalMs(15_000)
        .pongTimeoutMs(5_000), new VKWebSocketHandler() {
        @Override
        public void onOpen(VKWebSocketSession session) {
            session.sendText("welcome");
        }

        @Override
        public void onText(VKWebSocketSession session, String text) {
            session.sendText("echo:" + text);
        }
    });
```

---

# 4. File 模块

## 4.1 接口定义

```java
public interface Vostok.File {
    /**
     * 初始化 File 模块（可重复调用，后一次配置覆盖前一次）。
     *
     * - fileConfig：文件模块配置对象，类型为 VKFileConfig，不能为空。
     */
    public static void init(VKFileConfig fileConfig);

    /**
     * 判断 File 模块是否已初始化。
     *
     * - true：已初始化。
     * - false：未初始化。
     */
    public static boolean started();

    /**
     * 获取当前生效的 File 配置快照。
     *
     * - 返回值：VKFileConfig。
     */
    public static VKFileConfig config();

    /**
     * 关闭 File 模块并释放资源（如 watch 后台监听资源）。
     */
    public static void close();

    /**
     * 注册文件存储实现（如 local/oss/s3）。
     *
     * - mode：存储模式名，类型为 String，不能为空。
     * - store：存储实现对象，类型为 VKFileStore，不能为空。
     */
    public static void registerStore(String mode, VKFileStore store);

    /**
     * 设置默认文件存储模式。
     *
     * - mode：已注册模式名，类型为 String，不能为空。
     */
    public static void setDefaultMode(String mode);

    /**
     * 获取默认存储模式名。
     *
     * - 返回值：String。
     */
    public static String defaultMode();

    /**
     * 获取当前可用的模式集合。
     *
     * - 返回值：Set<String>。
     */
    public static Set<String> modes();

    /**
     * 在指定模式下执行无返回值操作（线程上下文切换）。
     *
     * - mode：模式名，类型为 String。
     * - action：执行逻辑，类型为 Runnable，不能为空。
     */
    public static void withMode(String mode, Runnable action);

    /**
     * 在指定模式下执行有返回值操作（线程上下文切换）。
     *
     * - mode：模式名，类型为 String。
     * - supplier：执行逻辑，类型为 Supplier<T>，不能为空。
     * - 返回值：T。
     */
    public static <T> T withMode(String mode, Supplier<T> supplier);

    /**
     * 获取当前线程实际使用的模式名。
     *
     * - 返回值：String。
     */
    public static String currentMode();

    /**
     * 创建文本文件（文件已存在时失败）。
     *
     * - path：文件相对/绝对路径，类型为 String。
     * - content：写入文本内容，类型为 String，可为 null（按空串处理）。
     */
    public static void create(String path, String content);

    /**
     * 写入文本文件（不存在则创建，存在则覆盖）。
     *
     * - path：文件路径，类型为 String。
     * - content：文本内容，类型为 String，可为 null（按空串处理）。
     */
    public static void write(String path, String content);

    /**
     * 更新文本文件（要求文件已存在）。
     *
     * - path：文件路径，类型为 String。
     * - content：文本内容，类型为 String。
     */
    public static void update(String path, String content);

    /**
     * 读取文本文件。
     *
     * - path：文件路径，类型为 String。
     * - 返回值：String（文本内容）。
     */
    public static String read(String path);

    /**
     * 读取文件二进制内容。
     *
     * - path：文件路径，类型为 String。
     * - 返回值：byte[]。
     */
    public static byte[] readBytes(String path);

    /**
     * 按范围读取二进制内容到内存。
     *
     * - path：文件路径，类型为 String。
     * - offset：起始偏移（从 0 开始），类型为 long，必须 >= 0。
     * - length：读取长度，类型为 int，必须 >= 0。
     * - 返回值：byte[]（超出文件末尾返回空数组）。
     */
    public static byte[] readRange(String path, long offset, int length);

    /**
     * 按范围流式读取到输出流，适合大块数据，避免大数组占用堆内存。
     *
     * - path：文件路径，类型为 String。
     * - offset：起始偏移，类型为 long，必须 >= 0。
     * - length：读取长度，类型为 long，必须 >= 0。
     * - output：输出流，类型为 OutputStream，不能为空。
     * - 返回值：long（实际写出的字节数）。
     */
    public static long readRangeTo(String path, long offset, long length, OutputStream output);

    /**
     * 整文件流式读取到输出流，适合大文件导出/转发。
     *
     * - path：文件路径，类型为 String。
     * - output：输出流，类型为 OutputStream，不能为空。
     * - 返回值：long（实际写出的字节数）。
     */
    public static long readTo(String path, OutputStream output);

    /**
     * 写入二进制文件（不存在则创建，存在则覆盖）。
     *
     * - path：文件路径，类型为 String。
     * - content：二进制内容，类型为 byte[]，不能为空。
     */
    public static void writeBytes(String path, byte[] content);

    /**
     * 追加二进制内容到文件末尾。
     *
     * - path：文件路径，类型为 String。
     * - content：二进制内容，类型为 byte[]，不能为空。
     */
    public static void appendBytes(String path, byte[] content);

    /**
     * 从输入流写入文件（默认覆盖已存在文件）。
     *
     * - path：目标文件路径，类型为 String。
     * - input：输入流，类型为 InputStream，不能为空。
     * - 返回值：long（实际写入字节数）。
     */
    public static long writeFrom(String path, InputStream input);

    /**
     * 从输入流写入文件（可指定是否覆盖）。
     *
     * - path：目标文件路径，类型为 String。
     * - input：输入流，类型为 InputStream，不能为空。
     * - replaceExisting：是否覆盖已存在文件，类型为 boolean。
     * - 返回值：long（实际写入字节数）。
     */
    public static long writeFrom(String path, InputStream input, boolean replaceExisting);

    /**
     * 从输入流追加写入文件末尾。
     *
     * - path：目标文件路径，类型为 String。
     * - input：输入流，类型为 InputStream，不能为空。
     * - 返回值：long（实际写入字节数）。
     */
    public static long appendFrom(String path, InputStream input);

    /**
     * 根据日期分片规则生成建议存储路径（当前时间）。
     *
     * - relativePath：业务相对路径（不含日期目录），类型为 String，必须为相对路径。
     * - 返回值：String（如 "2026/02/18/upload/a.png"）。
     */
    public static String suggestDatePath(String relativePath);

    /**
     * 根据日期分片规则生成建议存储路径（指定时间）。
     *
     * - relativePath：业务相对路径，类型为 String，必须为相对路径。
     * - atTime：指定时间，类型为 Instant，不能为空。
     * - 返回值：String（含日期目录的建议路径）。
     */
    public static String suggestDatePath(String relativePath, Instant atTime);

    /**
     * 按日期分片目录写入文本，并返回实际写入路径。
     *
     * - relativePath：业务相对路径，类型为 String，必须为相对路径。
     * - content：文本内容，类型为 String。
     * - 返回值：String（实际写入路径）。
     */
    public static String writeByDatePath(String relativePath, String content);

    /**
     * 按日期分片目录写入二进制，并返回实际写入路径。
     *
     * - relativePath：业务相对路径，类型为 String，必须为相对路径。
     * - content：二进制内容，类型为 byte[]。
     * - 返回值：String（实际写入路径）。
     */
    public static String writeBytesByDatePath(String relativePath, byte[] content);

    /**
     * 按日期分片目录从输入流写入，并返回实际写入路径。
     *
     * - relativePath：业务相对路径，类型为 String，必须为相对路径。
     * - input：输入流，类型为 InputStream。
     * - 返回值：String（实际写入路径）。
     */
    public static String writeFromByDatePath(String relativePath, InputStream input);

    /**
     * 迁移当前 baseDir 全部文件到目标目录（默认 COPY_ONLY + FAIL）。
     *
     * - targetBaseDir：目标根目录（绝对路径），类型为 String，不能为空且不能位于当前 baseDir 内部。
     * - 返回值：VKFileMigrateResult（迁移统计结果）。
     */
    public static VKFileMigrateResult migrateBaseDir(String targetBaseDir);

    /**
     * 迁移当前 baseDir 全部文件到目标目录（自定义迁移参数）。
     *
     * - targetBaseDir：目标根目录（绝对路径），类型为 String，不能为空且不能位于当前 baseDir 内部。
     * - options：迁移参数，类型为 VKFileMigrateOptions，不能为空。
     *   options.parallelism > 1 时启用并行迁移；任务进入有界队列，队列满时采用阻塞背压（不会丢任务）。
     * - 返回值：VKFileMigrateResult（迁移统计结果）。
     */
    public static VKFileMigrateResult migrateBaseDir(String targetBaseDir, VKFileMigrateOptions options);

    /**
     * 生成图片缩略图并以字节数组返回。
     *
     * - imagePath：源图片路径，类型为 String。
     * - options：缩略图参数，类型为 VKThumbnailOptions，不能为空。
     * - 返回值：byte[]（缩略图内容）。
     */
    public static byte[] thumbnail(String imagePath, VKThumbnailOptions options);

    /**
     * 生成图片缩略图并写入目标文件。
     *
     * - imagePath：源图片路径，类型为 String。
     * - targetPath：目标文件路径，类型为 String。
     * - options：缩略图参数，类型为 VKThumbnailOptions，不能为空。
     */
    public static void thumbnailTo(String imagePath, String targetPath, VKThumbnailOptions options);

    /**
     * 计算文件摘要值。
     *
     * - path：文件路径，类型为 String。
     * - algorithm：摘要算法名称（如 MD5/SHA-256），类型为 String。
     * - 返回值：String（十六进制摘要）。
     */
    public static String hash(String path, String algorithm);

    /**
     * 删除文件或目录（目录按递归删除处理）。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：boolean（true 表示删除了目标；false 表示目标原本不存在）。
     */
    public static boolean delete(String path);

    /**
     * 删除目标（不递归）。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：boolean（true 表示删除成功；false 表示目标不存在）。
     */
    public static boolean deleteIfExists(String path);

    /**
     * 递归删除文件或目录。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：boolean（true 表示删除成功；false 表示目标不存在）。
     */
    public static boolean deleteRecursively(String path);

    /**
     * 判断路径是否存在。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：boolean。
     */
    public static boolean exists(String path);

    /**
     * 判断路径是否为普通文件。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：boolean。
     */
    public static boolean isFile(String path);

    /**
     * 判断路径是否为目录。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：boolean。
     */
    public static boolean isDirectory(String path);

    /**
     * 追加文本到文件末尾。
     *
     * - path：文件路径，类型为 String。
     * - content：文本内容，类型为 String，可为 null（按空串处理）。
     */
    public static void append(String path, String content);

    /**
     * 按行读取文本文件。
     *
     * - path：文件路径，类型为 String。
     * - 返回值：List<String>。
     */
    public static List<String> readLines(String path);

    /**
     * 按行写入文本文件（覆盖）。
     *
     * - path：文件路径，类型为 String。
     * - lines：文本行集合，类型为 List<String>，不能为空。
     */
    public static void writeLines(String path, List<String> lines);

    /**
     * 列出路径下文件信息（非递归）。
     *
     * - path：目录或文件路径，类型为 String。
     * - 返回值：List<VKFileInfo>。
     */
    public static List<VKFileInfo> list(String path);

    /**
     * 列出路径下文件信息（可选递归）。
     *
     * - path：目录或文件路径，类型为 String。
     * - recursive：是否递归子目录，类型为 boolean。
     * - 返回值：List<VKFileInfo>。
     */
    public static List<VKFileInfo> list(String path, boolean recursive);

    /**
     * 遍历路径并按过滤器筛选文件信息。
     *
     * - path：目录或文件路径，类型为 String。
     * - recursive：是否递归子目录，类型为 boolean。
     * - filter：过滤器，类型为 Predicate<VKFileInfo>，可为 null（表示不过滤）。
     * - 返回值：List<VKFileInfo>。
     */
    public static List<VKFileInfo> walk(String path, boolean recursive, Predicate<VKFileInfo> filter);

    /**
     * 遍历路径并返回所有文件信息（无过滤器）。
     *
     * - path：目录或文件路径，类型为 String。
     * - recursive：是否递归子目录，类型为 boolean。
     * - 返回值：List<VKFileInfo>。
     */
    public static List<VKFileInfo> walk(String path, boolean recursive);

    /**
     * 创建单级目录（父目录不存在时失败）。
     *
     * - path：目录路径，类型为 String。
     */
    public static void mkdir(String path);

    /**
     * 递归创建目录（等价于 mkdir -p 语义）。
     *
     * - path：目录路径，类型为 String。
     */
    public static void mkdirs(String path);

    /**
     * 重命名文件/目录（同目录下改名）。
     *
     * - path：目标路径，类型为 String。
     * - newName：新名称（不能包含路径分隔符），类型为 String。
     */
    public static void rename(String path, String newName);

    /**
     * 复制文件（默认覆盖目标）。
     *
     * - sourcePath：源文件路径，类型为 String。
     * - targetPath：目标文件路径，类型为 String。
     */
    public static void copy(String sourcePath, String targetPath);

    /**
     * 复制文件（可指定是否覆盖目标）。
     *
     * - sourcePath：源文件路径，类型为 String。
     * - targetPath：目标文件路径，类型为 String。
     * - replaceExisting：目标存在时是否覆盖，类型为 boolean。
     */
    public static void copy(String sourcePath, String targetPath, boolean replaceExisting);

    /**
     * 移动文件（默认覆盖目标）。
     *
     * - sourcePath：源文件路径，类型为 String。
     * - targetPath：目标文件路径，类型为 String。
     */
    public static void move(String sourcePath, String targetPath);

    /**
     * 移动文件（可指定是否覆盖目标）。
     *
     * - sourcePath：源文件路径，类型为 String。
     * - targetPath：目标文件路径，类型为 String。
     * - replaceExisting：目标存在时是否覆盖，类型为 boolean。
     */
    public static void move(String sourcePath, String targetPath, boolean replaceExisting);

    /**
     * 复制目录。
     *
     * - sourceDir：源目录路径，类型为 String。
     * - targetDir：目标目录路径，类型为 String。
     * - strategy：冲突策略（FAIL/SKIP/OVERWRITE），类型为 VKFileConflictStrategy。
     */
    public static void copyDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy);

    /**
     * 移动目录。
     *
     * - sourceDir：源目录路径，类型为 String。
     * - targetDir：目标目录路径，类型为 String。
     * - strategy：冲突策略（FAIL/SKIP/OVERWRITE），类型为 VKFileConflictStrategy。
     */
    public static void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy);

    /**
     * 触碰文件：不存在则创建，存在则更新最后修改时间。
     *
     * - path：目标文件路径，类型为 String。
     */
    public static void touch(String path);

    /**
     * 获取文件大小（字节）。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：long（字节数）。
     */
    public static long size(String path);

    /**
     * 获取最后修改时间。
     *
     * - path：目标路径，类型为 String。
     * - 返回值：Instant。
     */
    public static Instant lastModified(String path);

    /**
     * 压缩文件或目录为 zip。
     *
     * - sourcePath：源文件/目录路径，类型为 String。
     * - zipPath：目标 zip 路径，类型为 String。
     */
    public static void zip(String sourcePath, String zipPath);

    /**
     * 解压 zip（使用全局配置中的默认限制，默认覆盖）。
     *
     * - zipPath：zip 文件路径，类型为 String。
     * - targetDir：解压目标目录，类型为 String。
     */
    public static void unzip(String zipPath, String targetDir);

    /**
     * 解压 zip（使用全局配置中的默认限制，可指定覆盖策略）。
     *
     * - zipPath：zip 文件路径，类型为 String。
     * - targetDir：解压目标目录，类型为 String。
     * - replaceExisting：目标存在时是否覆盖，类型为 boolean。
     */
    public static void unzip(String zipPath, String targetDir, boolean replaceExisting);

    /**
     * 解压 zip（显式指定安全选项，如 entries/总解压字节/单文件字节上限）。
     *
     * - zipPath：zip 文件路径，类型为 String。
     * - targetDir：解压目标目录，类型为 String。
     * - options：解压选项，类型为 VKUnzipOptions。
     */
    public static void unzip(String zipPath, String targetDir, VKUnzipOptions options);

    /**
     * 监听路径变化（是否递归由全局配置 watchRecursiveDefault 决定）。
     *
     * - path：监听路径（文件或目录），类型为 String。
     * - listener：事件回调，类型为 VKFileWatchListener。
     * - 返回值：VKFileWatchHandle（可 close 取消监听）。
     */
    public static VKFileWatchHandle watch(String path, VKFileWatchListener listener);

    /**
     * 监听路径变化（显式指定是否递归监听子目录）。
     *
     * - path：监听路径（文件或目录），类型为 String。
     * - recursive：是否递归监听，类型为 boolean。
     * - listener：事件回调，类型为 VKFileWatchListener。
     * - 返回值：VKFileWatchHandle（可 close 取消监听）。
     */
    public static VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener);

}
```

## 4.2 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.*;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class FileApiDemo {
    public static void main(String[] args) throws Exception {
        Vostok.File.init(new VKFileConfig().mode("local").baseDir("/tmp/vostok-files"));
        boolean s = Vostok.File.started();
        VKFileConfig cfg = Vostok.File.config();

        Vostok.File.registerStore("local2", new LocalFileStore(java.nio.file.Path.of("/tmp/vostok-files-2")));
        Vostok.File.setDefaultMode("local");
        String d = Vostok.File.defaultMode();
        Set<String> ms = Vostok.File.modes();

        Vostok.File.withMode("local", () -> {});
        String modeName = Vostok.File.withMode("local", Vostok.File::currentMode);
        String current = Vostok.File.currentMode();

        Vostok.File.create("a.txt", "hello");
        Vostok.File.write("a.txt", "world");
        Vostok.File.update("a.txt", "world2");
        String txt = Vostok.File.read("a.txt");

        Vostok.File.writeBytes("b.bin", new byte[]{1,2,3,4,5});
        byte[] all = Vostok.File.readBytes("b.bin");
        byte[] part = Vostok.File.readRange("b.bin", 1, 2);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long copied = Vostok.File.readRangeTo("b.bin", 0, 10, out);
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        long copied2 = Vostok.File.readTo("b.bin", out2);
        Vostok.File.appendBytes("b.bin", new byte[]{6,7});
        long w1 = Vostok.File.writeFrom("stream/in.bin", new java.io.ByteArrayInputStream(new byte[]{1,2,3}));
        long w2 = Vostok.File.writeFrom("stream/in2.bin", new java.io.ByteArrayInputStream(new byte[]{4,5}), true);
        long w3 = Vostok.File.appendFrom("stream/in2.bin", new java.io.ByteArrayInputStream(new byte[]{6,7}));
        String dp = Vostok.File.suggestDatePath("upload/a.txt");
        String dp2 = Vostok.File.suggestDatePath("upload/b.txt", java.time.Instant.parse("2026-02-18T01:02:03Z"));
        String p1 = Vostok.File.writeByDatePath("upload/t1.txt", "hello");
        String p2 = Vostok.File.writeBytesByDatePath("upload/t2.bin", new byte[]{8,9});
        String p3 = Vostok.File.writeFromByDatePath("upload/t3.bin", new java.io.ByteArrayInputStream(new byte[]{10,11}));

        VKFileMigrateResult migrateResult = Vostok.File.migrateBaseDir("/tmp/vostok-files-backup",
                new VKFileMigrateOptions()
                        .mode(VKFileMigrateMode.COPY_ONLY)
                        .conflictStrategy(VKFileConflictStrategy.OVERWRITE)
                        .verifyHash(true)
                        .includeHidden(true)
                        .parallelism(4)
                        .queueCapacity(2048)
                        .checkpointFile("/tmp/vostok-migrate.ckpt")
                        .maxRetries(2)
                        .retryIntervalMs(300)
                        .progressListener(progress -> {
                            // progress.status(): RETRYING / MIGRATED / SKIPPED / FAILED / DONE
                            // progress.path(): 当前文件相对路径（DONE 时为 null）
                        })
                        .dryRun(false));
        boolean migrateOk = migrateResult.success();
        long migratedCount = migrateResult.migratedFiles();
        List<VKFileMigrateResult.Failure> migrateFailures = migrateResult.failures();
        byte[] tb1 = Vostok.File.thumbnail("img/origin.png",
                VKThumbnailOptions.builder(200, 200)
                        .mode(VKThumbnailMode.FIT)
                        .format("jpg")
                        .quality(0.85f)
                        .upscale(false)
                        .build());
        Vostok.File.thumbnailTo("img/origin.png", "img/thumb_200.jpg",
                VKThumbnailOptions.builder(200, 200)
                        .mode(VKThumbnailMode.FILL)
                        .build());

        String sha = Vostok.File.hash("b.bin", "SHA-256");

        boolean ex = Vostok.File.exists("a.txt");
        boolean isF = Vostok.File.isFile("a.txt");
        boolean isD = Vostok.File.isDirectory("dir");

        Vostok.File.append("a.txt", "\nline2");
        List<String> lines = Vostok.File.readLines("a.txt");
        Vostok.File.writeLines("c.txt", List.of("l1", "l2"));

        List<VKFileInfo> l1 = Vostok.File.list(".");
        List<VKFileInfo> l2 = Vostok.File.list(".", true);
        List<VKFileInfo> wl1 = Vostok.File.walk(".", true, info -> !info.directory());
        List<VKFileInfo> wl2 = Vostok.File.walk(".", false);

        Vostok.File.mkdir("dir");
        Vostok.File.mkdirs("dir/sub");
        Vostok.File.rename("a.txt", "a2.txt");

        Vostok.File.copy("a2.txt", "copy/a2.txt");
        Vostok.File.copy("a2.txt", "copy/a3.txt", true);
        Vostok.File.move("c.txt", "moved/c.txt");
        Vostok.File.move("moved/c.txt", "moved/c2.txt", true);

        Vostok.File.copyDir("dir", "dir-copy", VKFileConflictStrategy.OVERWRITE);
        Vostok.File.moveDir("dir-copy", "dir-move", VKFileConflictStrategy.OVERWRITE);

        Vostok.File.touch("touch.txt");
        long size = Vostok.File.size("a2.txt");
        Instant lm = Vostok.File.lastModified("a2.txt");

        Vostok.File.zip("dir-move", "archive.zip");
        Vostok.File.unzip("archive.zip", "unz1");
        Vostok.File.unzip("archive.zip", "unz2", true);
        Vostok.File.unzip("archive.zip", "unz3", VKUnzipOptions.builder().replaceExisting(true).build());

        try (VKFileWatchHandle h1 = Vostok.File.watch(".", event -> {});
             VKFileWatchHandle h2 = Vostok.File.watch(".", true, event -> {})) {
            // watching...
        }

        boolean del1 = Vostok.File.delete("a2.txt");
        boolean del2 = Vostok.File.deleteIfExists("touch.txt");
        boolean del3 = Vostok.File.deleteRecursively("dir-move");

        Vostok.File.close();
    }
}
```

## 4.3 配置详解（VKFileConfig）

```java
import yueyang.vostok.file.VKFileConfig;
import java.nio.charset.StandardCharsets;

VKFileConfig cfg = new VKFileConfig()
    // 文件模式。默认 "local"；自定义存储需先 registerStore 再切换模式。
    .mode("local")
    // 基础目录。默认 System.getProperty("user.dir", ".")；所有相对路径都基于该目录。
    .baseDir("/tmp/vostok-files")
    // 文本读写字符集。默认 UTF-8。
    .charset(StandardCharsets.UTF_8)
    // 解压最大文件条目数。默认 -1（不限制）；建议生产环境设置上限防 zip bomb。
    .unzipMaxEntries(-1)
    // 解压总解压字节上限。默认 -1（不限制）；建议设置防止磁盘打满。
    .unzipMaxTotalUncompressedBytes(-1)
    // 单文件解压字节上限。默认 -1（不限制）；建议设置防止异常大文件。
    .unzipMaxEntryUncompressedBytes(-1)
    // watch(path, listener) 的默认递归策略。默认 false。
    .watchRecursiveDefault(false)
    // 日期分片目录格式（用于 suggestDatePath / write*ByDatePath）。默认 "yyyy/MM/dd"。
    .datePartitionPattern("yyyy/MM/dd")
    // 日期分片时区（用于 suggestDatePath / write*ByDatePath）。默认系统时区。
    .datePartitionZoneId("UTC");
```

## 4.4 Options 配置详解（File）

```java
import yueyang.vostok.file.VKUnzipOptions;

VKUnzipOptions unzipOptions = VKUnzipOptions.builder()
    // 解压时若目标文件已存在，是否覆盖。默认 true。
    .replaceExisting(true)
    // 最大解压条目数。默认 -1（不限制）；建议生产环境设置上限。
    .maxEntries(10_000)
    // 总解压字节上限。默认 -1（不限制）；建议设置防止磁盘被打满。
    .maxTotalUncompressedBytes(2L * 1024 * 1024 * 1024)
    // 单个条目解压字节上限。默认 -1（不限制）；建议设置防止异常大文件。
    .maxEntryUncompressedBytes(512L * 1024 * 1024)
    .build();
```

```java
import yueyang.vostok.file.VKThumbnailMode;
import yueyang.vostok.file.VKThumbnailOptions;
import java.awt.Color;

VKThumbnailOptions thumbnailOptions = VKThumbnailOptions.builder(320, 240)
    // 缩放模式：FIT（完整显示）/ FILL（填满裁切）。默认 FIT。
    .mode(VKThumbnailMode.FIT)
    // 输出格式（jpg/png/webp 等，依赖运行时 ImageIO 编解码器）。默认 null（沿用源格式）。
    .format("jpg")
    // 压缩质量 [0,1]。默认 0.85。
    .quality(0.85f)
    // 是否保持原图宽高比。默认 true。
    .keepAspectRatio(true)
    // 是否允许放大到目标尺寸。默认 false。
    .upscale(false)
    // 背景色（如透明图转 jpg 时生效）。默认白色。
    .background(Color.WHITE)
    // 是否移除元数据。默认 true。
    .stripMetadata(true)
    // 是否锐化。默认 false。
    .sharpen(false)
    // 输入像素上限。默认 100_000_000。
    .maxInputPixels(100_000_000L)
    // 输出像素上限。默认 64_000_000。
    .maxOutputPixels(64_000_000L)
    .build();
```

```java
import yueyang.vostok.file.VKFileMigrateMode;
import yueyang.vostok.file.VKFileConflictStrategy;
import yueyang.vostok.file.VKFileMigrateOptions;

VKFileMigrateOptions migrateOptions = new VKFileMigrateOptions()
    // 迁移模式：COPY_ONLY（仅复制）/ MOVE（复制后删除源文件）。默认 COPY_ONLY。
    .mode(VKFileMigrateMode.COPY_ONLY)
    // 冲突策略：FAIL / SKIP / OVERWRITE。默认 FAIL。
    .conflictStrategy(VKFileConflictStrategy.FAIL)
    // 是否校验源/目标文件哈希（SHA-256）。默认 false。
    .verifyHash(false)
    // 是否包含隐藏文件。默认 true。
    .includeHidden(true)
    // MOVE 模式下是否清理迁移后的空目录。默认 true。
    .deleteEmptyDirsAfterMove(true)
    // 仅演练不落盘。默认 false。
    .dryRun(false)
    // 单文件失败最大重试次数。默认 0（不重试）。
    .maxRetries(2)
    // 重试间隔（毫秒）。默认 200。
    .retryIntervalMs(300)
    // 并行 worker 数。默认 1（串行）；>1 启用并行迁移。
    .parallelism(4)
    // 有界任务队列容量。默认 1024；队列满时阻塞等待（背压，不丢任务）。
    .queueCapacity(2048)
    // 断点续传文件路径（建议使用 source baseDir 外部绝对路径）。
    .checkpointFile("/tmp/vostok-migrate.ckpt")
    // 进度回调（状态：RETRYING/MIGRATED/SKIPPED/FAILED/DONE）。
    .progressListener(progress -> {});
```

---

# 5. Log 模块

## 5.1 接口定义

```java
public interface Vostok.Log {
    /**
     * 使用默认配置初始化 Log 模块（幂等）。
     */
    public static void init();

    /**
     * 使用指定配置初始化 Log 模块（幂等）。
     * 
     * - config：日志配置对象，类型为 VKLogConfig，不能为空。
     */
    public static void init(VKLogConfig config);

    /**
     * 运行期重建日志引擎并应用新配置。
     * 
     * - config：新日志配置对象，类型为 VKLogConfig，不能为空。
     */
    public static void reinit(VKLogConfig config);

    /**
     * 关闭 Log 模块并停止后台写线程。
     */
    public static void close();

    /**
     * 判断 Log 模块是否已初始化。
     * 
     * - true：已初始化。
     * - false：未初始化。
     */
    public static boolean initialized();

    /**
     * 获取指定名称的 Logger（快捷方式）。
     *
     * - loggerName：Logger 名称；同时作为日志文件名（例如 data -> data.log）。
     * - 返回值：VKLogger（可直接调用 info/debug/warn/error）。
     */
    public static VKLogger logger(String loggerName);

    /**
     * 获取指定名称的 Logger（可复用实例）。
     *
     * - loggerName：Logger 名称；同时作为日志文件名（例如 web -> web.log）。
     * - 返回值：VKLogger（缓存复用同名实例）。
     */
    public static VKLogger getLogger(String loggerName);

    /**
     * 打印 TRACE 级别日志（纯文本消息）。
     * 
     * - msg：日志文本，类型为 String，可为 null（会按 "null" 输出）。
     */
    public static void trace(String msg);

    /**
     * 打印 DEBUG 级别日志（纯文本消息）。
     * 
     * - msg：日志文本，类型为 String。
     */
    public static void debug(String msg);

    /**
     * 打印 INFO 级别日志（纯文本消息）。
     * 
     * - msg：日志文本，类型为 String。
     */
    public static void info(String msg);

    /**
     * 打印 WARN 级别日志（纯文本消息）。
     * 
     * - msg：日志文本，类型为 String。
     */
    public static void warn(String msg);

    /**
     * 打印 ERROR 级别日志（纯文本消息）。
     * 
     * - msg：日志文本，类型为 String。
     */
    public static void error(String msg);

    /**
     * 打印 ERROR 级别日志并附带异常堆栈。
     * 
     * - msg：日志文本，类型为 String。
     * - t：异常对象，类型为 Throwable，可为 null。
     */
    public static void error(String msg, Throwable t);

    /**
     * 打印 TRACE 级别格式化日志（支持 {} 占位符）。
     * 
     * - template：模板字符串，类型为 String。
     * - args：模板参数，类型为 Object...。
     */
    public static void trace(String template, Object... args);

    /**
     * 打印 DEBUG 级别格式化日志（支持 {} 占位符）。
     * 
     * - template：模板字符串，类型为 String。
     * - args：模板参数，类型为 Object...。
     */
    public static void debug(String template, Object... args);

    /**
     * 打印 INFO 级别格式化日志（支持 {} 占位符）。
     * 
     * - template：模板字符串，类型为 String。
     * - args：模板参数，类型为 Object...。
     */
    public static void info(String template, Object... args);

    /**
     * 打印 WARN 级别格式化日志（支持 {} 占位符）。
     * 
     * - template：模板字符串，类型为 String。
     * - args：模板参数，类型为 Object...。
     */
    public static void warn(String template, Object... args);

    /**
     * 打印 ERROR 级别格式化日志（支持 {} 占位符）。
     * 
     * - template：模板字符串，类型为 String。
     * - args：模板参数，类型为 Object...。
     */
    public static void error(String template, Object... args);

    /**
     * 设置日志级别阈值。
     * 
     * - level：日志级别，类型为 VKLogLevel，不可为 null。
     */
    public static void setLevel(VKLogLevel level);

    /**
     * 获取当前日志级别。
     * 当前生效的 VKLogLevel。
     */
    public static VKLogLevel level();

    /**
     * 设置日志输出目录。
     * 
     * - outputDir：输出目录路径，类型为 String，不能为空。
     */
    public static void setOutputDir(String outputDir);

    /**
     * 设置日志文件名前缀。
     * 
     * - filePrefix：文件名前缀，类型为 String，不能为空。
     */
    public static void setFilePrefix(String filePrefix);

    /**
     * 设置单日志文件最大大小（MB）。
     * 
     * - mb：文件大小上限（MB），类型为 long，必须大于 0。
     */
    public static void setMaxFileSizeMb(long mb);

    /**
     * 设置单日志文件最大大小（字节）。
     * 
     * - bytes：文件大小上限（字节），类型为 long，必须大于 0。
     */
    public static void setMaxFileSizeBytes(long bytes);

    /**
     * 设置滚动日志最多保留文件数量。
     * 
     * - maxBackups：保留数量，类型为 int，必须大于等于 0。
     */
    public static void setMaxBackups(int maxBackups);

    /**
     * 设置滚动日志按天保留上限。
     * 
     * - maxBackupDays：保留天数，类型为 int，必须大于等于 0。
     */
    public static void setMaxBackupDays(int maxBackupDays);

    /**
     * 设置滚动日志总容量上限（MB）。
     * 
     * - mb：总容量上限（MB），类型为 long，必须大于 0。
     */
    public static void setMaxTotalSizeMb(long mb);

    /**
     * 设置是否同时输出到控制台。
     * 
     * - enabled：true 表示输出控制台，false 表示仅写文件。
     */
    public static void setConsoleEnabled(boolean enabled);

    /**
     * 设置异步队列满时处理策略。
     * 
     * - policy：队列策略，类型为 VKLogQueueFullPolicy（DROP/BLOCK/SYNC_FALLBACK）。
     */
    public static void setQueueFullPolicy(VKLogQueueFullPolicy policy);

    /**
     * 设置异步队列容量。
     * 
     * - capacity：队列长度，类型为 int，必须大于 0。
     */
    public static void setQueueCapacity(int capacity);

    /**
     * 设置定时刷盘间隔（毫秒）。
     * 
     * - flushIntervalMs：刷盘间隔，类型为 long，必须大于 0。
     */
    public static void setFlushIntervalMs(long flushIntervalMs);

    /**
     * 设置批量刷盘阈值（累计条数）。
     * 
     * - flushBatchSize：批量阈值，类型为 int，必须大于 0。
     */
    public static void setFlushBatchSize(int flushBatchSize);

    /**
     * 设置关闭日志线程等待超时（毫秒）。
     * 
     * - shutdownTimeoutMs：等待超时，类型为 long，必须大于 0。
     */
    public static void setShutdownTimeoutMs(long shutdownTimeoutMs);

    /**
     * 设置 fsync 持久化策略。
     * 
     * - fsyncPolicy：策略枚举，类型为 VKLogFsyncPolicy（NEVER/EVERY_FLUSH/EVERY_WRITE）。
     */
    public static void setFsyncPolicy(VKLogFsyncPolicy fsyncPolicy);

    /**
     * 设置按时间滚动策略。
     * 
     * - interval：滚动周期，类型为 VKLogRollInterval（NONE/HOURLY/DAILY）。
     */
    public static void setRollInterval(VKLogRollInterval interval);

    /**
     * 设置滚动文件是否压缩为 gzip。
     * 
     * - compress：true 开启压缩，false 关闭压缩。
     */
    public static void setCompressRolledFiles(boolean compress);

    /**
     * 设置文件写失败后的重试间隔（毫秒）。
     * 
     * - retryIntervalMs：重试间隔，类型为 long，必须大于 0。
     */
    public static void setFileRetryIntervalMs(long retryIntervalMs);

    /**
     * 获取累计丢弃日志条数（通常在 DROP 策略下发生）。
     * long 类型累计计数。
     */
    public static long droppedLogs();

    /**
     * 获取降级写入次数（stderr 或同步兜底写）。
     * long 类型累计计数。
     */
    public static long fallbackWrites();

    /**
     * 获取文件写失败次数。
     * long 类型累计计数。
     */
    public static long fileWriteErrors();

    /**
     * 主动触发一次刷盘。
     */
    public static void flush();

    /**
     * 关闭 Log 模块（等价于 close）。
     */
    public static void shutdown();

    /**
     * 将 Log 配置重置为默认值并重建引擎。
     */
    public static void resetDefaults();

}
```

---

## 5.2 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.log.*;

public class LogApiDemo {
    public static void main(String[] args) {
        Vostok.Log.init();
        Vostok.Log.init(new VKLogConfig().outputDir("/tmp/vostok-log").filePrefix("app"));
        Vostok.Log.reinit(new VKLogConfig().outputDir("/tmp/vostok-log2").filePrefix("app2"));

        boolean inited = Vostok.Log.initialized();

        Vostok.Log.trace("trace");
        Vostok.Log.debug("debug");
        Vostok.Log.info("info");
        Vostok.Log.warn("warn");
        Vostok.Log.error("error");
        Vostok.Log.error("error with ex", new RuntimeException("boom"));

        Vostok.Log.trace("trace {}", 1);
        Vostok.Log.debug("debug {}", 2);
        Vostok.Log.info("info {}", 3);
        Vostok.Log.warn("warn {}", 4);
        Vostok.Log.error("error {}", 5);

        // 按 logger 路由到独立文件
        Vostok.Log.logger("data").info("select * from t_user"); // -> data.log
        VKLogger webLogger = Vostok.Log.getLogger("web");
        webLogger.warn("slow request /api/user");               // -> web.log
        webLogger.error("web error {}", 500);

        // 预注册模式（禁止运行时自动创建未注册 logger）
        Vostok.Log.reinit(new VKLogConfig()
                .outputDir("/tmp/vostok-logs")
                .filePrefix("app")
                .autoCreateLoggerSink(false)
                .registerLoggers("data", "web")
                .registerLogger("access", new VKLogSinkConfig()
                        .filePrefix("access")
                        .rollInterval(VKLogRollInterval.HOURLY)));

        Vostok.Log.setLevel(VKLogLevel.INFO);
        VKLogLevel lv = Vostok.Log.level();
        Vostok.Log.setOutputDir("/tmp/vostok-log");
        Vostok.Log.setFilePrefix("svc");
        Vostok.Log.setMaxFileSizeMb(128);
        Vostok.Log.setMaxFileSizeBytes(128L * 1024 * 1024);
        Vostok.Log.setMaxBackups(10);
        Vostok.Log.setMaxBackupDays(7);
        Vostok.Log.setMaxTotalSizeMb(1024);
        Vostok.Log.setConsoleEnabled(true);
        Vostok.Log.setQueueFullPolicy(VKLogQueueFullPolicy.BLOCK);
        Vostok.Log.setQueueCapacity(65536);
        Vostok.Log.setFlushIntervalMs(1000);
        Vostok.Log.setFlushBatchSize(256);
        Vostok.Log.setShutdownTimeoutMs(5000);
        Vostok.Log.setFsyncPolicy(VKLogFsyncPolicy.NEVER);
        Vostok.Log.setRollInterval(VKLogRollInterval.DAILY);
        Vostok.Log.setCompressRolledFiles(false);
        Vostok.Log.setFileRetryIntervalMs(3000);

        long dropped = Vostok.Log.droppedLogs();
        long fallback = Vostok.Log.fallbackWrites();
        long writeErr = Vostok.Log.fileWriteErrors();

        Vostok.Log.flush();
        Vostok.Log.resetDefaults();
        Vostok.Log.shutdown();
        Vostok.Log.close();
    }
}
```

## 5.3 配置详解（VKLogConfig）

```java
import yueyang.vostok.log.*;

VKLogConfig cfg = new VKLogConfig()
    // 日志级别。默认 INFO。
    .level(VKLogLevel.INFO)
    // 日志输出目录。默认 "logs"。
    .outputDir("logs")
    // 活跃日志文件前缀。默认 "vostok"；当前文件名形如 vostok.log。
    .filePrefix("vostok")
    // 单日志文件大小上限（MB 方式设置）。
    // 默认底层值为 64MB（maxFileSizeBytes = 64 * 1024 * 1024）。
    .maxFileSizeMb(64)
    // 单日志文件大小上限（字节方式设置）。和 maxFileSizeMb 二选一即可。
    .maxFileSizeBytes(64L * 1024 * 1024)
    // 滚动文件最大保留数量。默认 20；0 表示不按数量限制。
    .maxBackups(20)
    // 滚动文件最大保留天数。默认 30；0 表示不按天数限制。
    .maxBackupDays(30)
    // 总日志体积上限（MB 方式设置）。默认 1024MB。
    .maxTotalSizeMb(1024)
    // 总日志体积上限（字节方式设置）。和 maxTotalSizeMb 二选一即可。
    .maxTotalSizeBytes(1024L * 1024 * 1024)
    // 是否同时输出控制台。默认 true。
    .consoleEnabled(true)
    // 异步队列容量。默认 32768（1 << 15）；容量不足会触发队列满策略。
    .queueCapacity(1 << 15)
    // 队列满策略。默认 DROP；可选 BLOCK / SYNC_FALLBACK。
    .queueFullPolicy(VKLogQueueFullPolicy.DROP)
    // 异步刷新间隔毫秒。默认 1000。
    .flushIntervalMs(1000)
    // 每批次最大刷盘条数。默认 256。
    .flushBatchSize(256)
    // 关闭等待超时毫秒。默认 5000。
    .shutdownTimeoutMs(5000)
    // fsync 策略。默认 NEVER；可选 EVERY_WRITE / EVERY_FLUSH。
    .fsyncPolicy(VKLogFsyncPolicy.NEVER)
    // 滚动周期。默认 DAILY；可选 HOURLY / NONE。
    .rollInterval(VKLogRollInterval.DAILY)
    // 是否压缩滚动文件。默认 false；true 时生成 .gz。
    .compressRolledFiles(false)
    // 文件写失败后的重试间隔毫秒。默认 3000。
    .fileRetryIntervalMs(3000)
    // 默认 logger 名称（用于语义标识，默认 app）。
    .defaultLoggerName("app")
    // 未预注册 logger 是否允许自动创建 sink。默认 true。
    .autoCreateLoggerSink(true)
    // 预注册多个 logger sink（init 时一次性创建）。
    .registerLoggers("data", "web", "access")
    // 预注册单个 logger（等价于 registerLoggers 的单值版本）。
    .registerLogger("audit")
    // 注册带独立 sink 覆盖配置的 logger。
    .registerLogger("access", new VKLogSinkConfig()
        .filePrefix("access")
        .rollInterval(VKLogRollInterval.HOURLY)
        .maxFileSizeBytes(256L * 1024 * 1024))
    // 批量覆盖 sink 配置（key 为 loggerName）。
    .loggerSinkConfigs(java.util.Map.of(
        "data", new VKLogSinkConfig().filePrefix("data").queueCapacity(8192),
        "web", new VKLogSinkConfig().filePrefix("web").flushIntervalMs(500L)
    ));
```

---

# 6. Config 模块

## 6.1 接口定义

```java
public interface Vostok.Config {
    // 初始化（幂等，重复 init 忽略）
    public static void init();
    public static void init(VKConfigOptions options);

    // 强制重建（覆盖当前 options 并立刻重载）
    public static void reinit(VKConfigOptions options);

    // 运行状态
    public static boolean started();
    public static String lastWatchError();

    // 重载与关闭
    public static void reload();
    public static void close();

    // 运行时修改 options（已加载时会即时重载）
    public static void configure(Consumer<VKConfigOptions> customizer);

    // 解析扩展
    public static void registerParser(VKConfigParser parser);

    // 校验扩展（启动/重载阶段 fail-fast）
    public static void registerValidator(VKConfigValidator validator);
    public static void clearValidators();

    // 外部配置文件
    public static void addFile(String path);
    public static void addFiles(String... paths);
    public static void clearManualFiles();

    // 运行时覆盖（最高优先级）
    public static void putOverride(String key, String value);
    public static void removeOverride(String key);
    public static void clearOverrides();

    // 读取
    public static String get(String key);
    public static String required(String key);
    public static boolean has(String key);
    public static Set<String> keys();

    public static String getString(String key, String defaultValue);
    public static int getInt(String key, int defaultValue);
    public static long getLong(String key, long defaultValue);
    public static double getDouble(String key, double defaultValue);
    public static boolean getBool(String key, boolean defaultValue);
    public static List<String> getList(String key);
}
```

## 6.2 命名空间与优先级

- 文件命名空间：配置文件名（去扩展名）作为第一层 key。
- 示例：`a.properties` 内容 `enabled=true`，读取 key 为 `a.enabled`。
- 自动扫描范围：
  - `user.dir` 下所有 `*.properties/*.yml/*.yaml`
  - `classpath` 下目录与 `jar` 中所有 `*.properties/*.yml/*.yaml`
- 固化优先级（后者覆盖前者）：
  1. 默认文件（自动扫描）
  2. 外部文件（`addFile/addFiles`）
  3. 环境变量（`loadEnv=true`）
  4. JVM `-D`（`loadSystemProperties=true`）
  5. 运行时覆盖（`putOverride`）

## 6.3 配置校验

- 在 `init/reinit/reload` 阶段执行全部校验器。
- 任意校验失败都会抛 `VKConfigException(VALIDATION_ERROR)`，并中止本次加载。
- 热更新场景下若校验失败，会保留旧快照并记录 `lastWatchError()`。

示例：

```java
import yueyang.vostok.config.validate.VKConfigValidators;

Vostok.Config.registerValidator(VKConfigValidators.required("app.host", "app.port"));
Vostok.Config.registerValidator(VKConfigValidators.intRange("app.port", 1, 65535));
Vostok.Config.registerValidator(VKConfigValidators.pattern("app.host", "^[a-zA-Z0-9.-]+$"));
Vostok.Config.registerValidator(VKConfigValidators.cross(
    "tls-port",
    v -> !"true".equals(v.get("app.tls.enabled")) || v.getInt("app.port", 0) == 443,
    "when tls enabled, app.port must be 443"
));
```

## 6.4 热更新

- 开启方式：`VKConfigOptions.watchEnabled(true)`。
- 文件变更触发 debounce 后自动重载。
- 重载失败（解析失败/校验失败）时：
  - 不替换当前快照（继续使用旧配置）
  - `lastWatchError()` 返回错误信息

## 6.5 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.config.VKConfigOptions;
import yueyang.vostok.config.validate.VKConfigValidators;

public class ConfigDemo {
    public static void main(String[] args) {
        Vostok.Config.registerValidator(VKConfigValidators.required("a.enabled"));

        Vostok.Config.init(new VKConfigOptions()
                .scanClasspath(true)
                .scanUserDir(true)
                .loadEnv(true)
                .loadSystemProperties(true)
                .watchEnabled(true)
                .watchDebounceMs(200));

        boolean enabled = Vostok.Config.getBool("a.enabled", false);

        Vostok.Config.addFile("/opt/app/conf/a.yaml");
        Vostok.Config.putOverride("a.enabled", "true");

        System.out.println("enabled=" + enabled + ", watchErr=" + Vostok.Config.lastWatchError());
    }
}
```

## 6.6 配置详解（VKConfigOptions）

`Config` 模块当前仅包含 1 个 Options 类：`VKConfigOptions`。

```java
import yueyang.vostok.config.VKConfigOptions;

VKConfigOptions options = new VKConfigOptions()
    // 自动扫描 classpath（目录 + jar）中的配置文件，默认 true。
    .scanClasspath(true)
    // 自动扫描 user.dir 中的配置文件，默认 true。
    .scanUserDir(true)
    // 冲突策略：同 namespace 来自不同来源时是否报错，默认 false。
    .strictNamespaceConflict(false)
    // 追加扫描目录。
    .addScanDir(java.nio.file.Path.of("/opt/my-app/config"))

    // 是否启用环境变量层（优先级高于文件层），默认 true。
    .loadEnv(true)
    // 是否启用 JVM -D 层（优先级高于环境变量），默认 true。
    .loadSystemProperties(true)

    // 热更新：是否启用文件监听，默认 false。
    .watchEnabled(true)
    // 热更新防抖毫秒，默认 300ms。
    .watchDebounceMs(300)

    // 可选：覆盖 classpath 文本（用于测试或特殊运行时）。
    .classpath(System.getProperty("java.class.path"))
    // 可选：自定义环境变量提供器。
    .envProvider(System::getenv)
    // 可选：自定义系统属性提供器。
    .systemPropertiesProvider(System::getProperties);
```

配置项说明（全量）：

- `scanClasspath(boolean)`：默认 `true`。是否扫描 classpath（目录与 jar）中的配置文件。
- `scanUserDir(boolean)`：默认 `true`。是否扫描 `System.getProperty("user.dir")` 下的配置文件。
- `strictNamespaceConflict(boolean)`：默认 `false`。为 `true` 时，同名 namespace（如 `a.properties` 与 `a.yaml`）冲突直接 fail-fast。
- `addScanDir(Path)`：默认空。追加业务自定义扫描目录，可多次调用。
- `loadEnv(boolean)`：默认 `true`。是否启用环境变量层（优先级高于文件层）。
- `loadSystemProperties(boolean)`：默认 `true`。是否启用 JVM `-D` 系统属性层（优先级高于环境变量层）。
- `watchEnabled(boolean)`：默认 `false`。是否启用文件监听热更新。
- `watchDebounceMs(long)`：默认 `300` 毫秒，最小 `50` 毫秒。热更新防抖时间窗口。
- `classpath(String)`：默认 `System.getProperty("java.class.path", "")`。可在测试或特殊运行时覆盖 classpath 扫描来源。
- `envProvider(Supplier<Map<String,String>>)`：默认 `System::getenv`。用于替换环境变量来源（常用于测试注入）。
- `systemPropertiesProvider(Supplier<Properties>)`：默认 `System::getProperties`。用于替换 JVM 属性来源（常用于测试注入）。

---

# 7. Security 模块

`Vostok.Security` 为独立安全模块，当前提供以下检测能力（不依赖 `Data/Web` 自动集成）：

- SQL 注入检测（含风险函数扩展：`pg_sleep/load_file/into outfile/copy ... program/xp_cmdshell`）
- XSS 检测
- 命令注入检测
- 路径穿越检测
- 响应敏感信息检测与脱敏
- 文件魔数识别与白名单校验
- 可执行脚本上传检测（扩展名 + 魔数 + 内容特征）
- 常用加解密接口（AES、RSA、签名验签、SHA-256、HMAC-SHA256）

## 7.0 说明与注意事项

- `Vostok.Security` 当前为“主动调用型”模块，不会自动拦截 `Data/Web` 请求链路，需要在业务代码中显式调用检测方法。
- 安全检测结果是风险判断，不等价于绝对攻击结论；建议结合业务上下文、鉴权与审计日志联合判定。
- SQL 检测优先用于识别明显注入模式与高危函数，仍建议始终使用参数化查询，不要拼接原始输入。
- XSS、命令注入、路径穿越检测基于规则与特征匹配，可能存在误报/漏报；上线前应通过白名单与阈值策略做校准。
- 响应脱敏仅做通用字段掩码，不替代完整数据分级与隐私合规策略（如最小化返回、按角色脱敏）。
- 文件魔数与脚本上传检测建议与文件大小限制、存储隔离、病毒扫描等机制配合使用。
- 该模块定位为应用层安全增强能力，不替代 WAF、RASP、主机安全、数据库权限最小化等基础安全控制。

## 7.1 接口定义

```java
public interface Vostok.Security {
    /**
     * 使用默认配置初始化 Security 模块。
     *
     * - 无参数。
     */
    public static void init();

    /**
     * 使用指定配置初始化 Security 模块（幂等，重复 init 会忽略后续调用）。
     *
     * - config：安全模块配置，类型为 VKSecurityConfig；传 null 时使用默认配置。
     */
    public static void init(VKSecurityConfig config);

    /**
     * 使用指定配置重建 Security 模块（可用于运行中动态调整规则与阈值）。
     *
     * - config：安全模块配置，类型为 VKSecurityConfig；传 null 时使用默认配置。
     */
    public static void reinit(VKSecurityConfig config);

    /**
     * 判断 Security 模块是否已初始化。
     *
     * - 返回值：boolean，true 表示已初始化。
     */
    public static boolean started();

    /**
     * 关闭 Security 模块（释放当前扫描器实例，不清理自定义规则列表）。
     */
    public static void close();

    /**
     * 注册自定义安全规则（追加到内置规则链之后执行）。
     *
     * - rule：自定义规则实现，类型为 VKSecurityRule；为 null 时忽略。
     */
    public static void registerRule(VKSecurityRule rule);

    /**
     * 清空当前已注册的所有自定义规则。
     */
    public static void clearCustomRules();

    /**
     * 获取当前生效规则名称列表（包含内置规则与自定义规则）。
     *
     * - 返回值：List<String>，规则名称列表。
     */
    public static List<String> listRules();

    /**
     * 检测 SQL 字符串的安全性（不含参数数量校验）。
     *
     * - sql：待检测 SQL 文本，类型为 String。
     * - 返回值：VKSqlCheckResult，包含是否安全、风险等级、命中规则与原因。
     */
    public static VKSqlCheckResult checkSql(String sql);

    /**
     * 检测 SQL 字符串的安全性（包含占位符与参数数量一致性校验）。
     *
     * - sql：待检测 SQL 文本，类型为 String。
     * - params：SQL 参数列表，类型为 Object...。
     * - 返回值：VKSqlCheckResult，包含是否安全、风险等级、命中规则与原因。
     */
    public static VKSqlCheckResult checkSql(String sql, Object... params);

    /**
     * 快速判断 SQL 是否安全。
     *
     * - sql：待检测 SQL 文本，类型为 String。
     * - 返回值：boolean，true 表示通过当前阈值判定。
     */
    public static boolean isSafeSql(String sql);

    /**
     * 断言 SQL 安全，不安全时抛出 VKSecurityException。
     *
     * - sql：待检测 SQL 文本，类型为 String。
     */
    public static void assertSafeSql(String sql);

    /**
     * 检测输入文本是否存在 XSS 风险。
     *
     * - input：待检测文本，类型为 String。
     * - 返回值：VKSecurityCheckResult，包含风险等级与命中规则。
     */
    public static VKSecurityCheckResult checkXss(String input);

    /**
     * 断言输入文本无 XSS 风险，不安全时抛出 VKSecurityException。
     *
     * - input：待检测文本，类型为 String。
     */
    public static void assertSafeXss(String input);

    /**
     * 检测输入文本是否存在命令注入风险。
     *
     * - input：待检测命令文本，类型为 String。
     * - 返回值：VKSecurityCheckResult，包含风险等级与命中规则。
     */
    public static VKSecurityCheckResult checkCommandInjection(String input);

    /**
     * 断言输入文本无命令注入风险，不安全时抛出 VKSecurityException。
     *
     * - input：待检测命令文本，类型为 String。
     */
    public static void assertSafeCommand(String input);

    /**
     * 检测路径字符串是否存在路径穿越风险。
     *
     * - inputPath：待检测路径，类型为 String。
     * - 返回值：VKSecurityCheckResult，包含风险等级与命中规则。
     */
    public static VKSecurityCheckResult checkPathTraversal(String inputPath);

    /**
     * 断言路径字符串无穿越风险，不安全时抛出 VKSecurityException。
     *
     * - inputPath：待检测路径，类型为 String。
     */
    public static void assertSafePath(String inputPath);

    /**
     * 检测响应文本是否包含敏感信息（手机号、邮箱、证件号、银行卡等）。
     *
     * - payload：响应文本，类型为 String。
     * - 返回值：VKSecurityCheckResult，包含风险等级与命中规则。
     */
    public static VKSecurityCheckResult checkSensitiveResponse(String payload);

    /**
     * 对响应文本中的敏感信息进行脱敏。
     *
     * - payload：响应文本，类型为 String。
     * - 返回值：String，脱敏后的文本。
     */
    public static String maskSensitiveResponse(String payload);

    /**
     * 根据文件魔数识别文件类型。
     *
     * - content：文件字节内容，类型为 byte[]。
     * - 返回值：VKFileType，识别出的文件类型（未知时为 UNKNOWN）。
     */
    public static VKFileType detectFileType(byte[] content);

    /**
     * 校验文件魔数是否属于允许类型白名单。
     *
     * - content：文件字节内容，类型为 byte[]。
     * - allowed：允许的文件类型，类型为 VKFileType...。
     * - 返回值：VKSecurityCheckResult，安全时表示命中白名单。
     */
    public static VKSecurityCheckResult checkFileMagic(byte[] content, VKFileType... allowed);

    /**
     * 检测上传文件是否包含可执行脚本风险（扩展名/魔数/内容特征）。
     *
     * - fileName：上传文件名，类型为 String。
     * - content：上传文件字节内容，类型为 byte[]。
     * - 返回值：VKSecurityCheckResult，包含风险等级与命中规则。
     */
    public static VKSecurityCheckResult checkExecutableScriptUpload(String fileName, byte[] content);

    /**
     * 生成 AES 对称密钥（Base64 编码，默认 256 位）。
     *
     * - 返回值：String，Base64 格式密钥。
     */
    public static String generateAesKey();

    /**
     * 使用 AES-GCM 便捷加密文本。
     *
     * - plainText：明文，类型为 String。
     * - secret：密钥或口令，类型为 String（支持 Base64 密钥；否则按口令派生）。
     * - 返回值：String，Base64 密文（内含随机 IV）。
     */
    public static String encrypt(String plainText, String secret);

    /**
     * 使用 AES-GCM 便捷解密文本。
     *
     * - cipherText：Base64 密文，类型为 String（由 encrypt 生成）。
     * - secret：密钥或口令，类型为 String。
     * - 返回值：String，明文。
     */
    public static String decrypt(String cipherText, String secret);

    /**
     * 生成 RSA 密钥对（PEM 格式，默认 2048 位）。
     *
     * - 返回值：VKRsaKeyPair，包含 publicKeyPem/privateKeyPem。
     */
    public static VKRsaKeyPair generateRsaKeyPair();

    /**
     * 使用 RSA 公钥加密（OAEP SHA-256）。
     *
     * - plainText：明文，类型为 String。
     * - publicKeyPem：公钥 PEM，类型为 String。
     * - 返回值：String，Base64 密文。
     */
    public static String encryptByPublicKey(String plainText, String publicKeyPem);

    /**
     * 使用 RSA 私钥解密（OAEP SHA-256）。
     *
     * - cipherText：Base64 密文，类型为 String。
     * - privateKeyPem：私钥 PEM，类型为 String。
     * - 返回值：String，明文。
     */
    public static String decryptByPrivateKey(String cipherText, String privateKeyPem);

    /**
     * 使用 RSA 私钥签名（SHA256withRSA）。
     *
     * - text：待签名文本，类型为 String。
     * - privateKeyPem：私钥 PEM，类型为 String。
     * - 返回值：String，Base64 签名值。
     */
    public static String sign(String text, String privateKeyPem);

    /**
     * 使用 RSA 公钥验签（SHA256withRSA）。
     *
     * - text：原文，类型为 String。
     * - signature：Base64 签名值，类型为 String。
     * - publicKeyPem：公钥 PEM，类型为 String。
     * - 返回值：boolean，true 表示验签通过。
     */
    public static boolean verify(String text, String signature, String publicKeyPem);

    /**
     * 计算 SHA-256 摘要（Base64 编码）。
     *
     * - text：原文，类型为 String。
     * - 返回值：String，Base64 摘要。
     */
    public static String sha256(String text);

    /**
     * 计算 SHA-256 摘要（十六进制编码）。
     *
     * - text：原文，类型为 String。
     * - 返回值：String，Hex 摘要。
     */
    public static String sha256Hex(String text);

    /**
     * 计算 HMAC-SHA256（Base64 编码）。
     *
     * - text：原文，类型为 String。
     * - secret：HMAC 密钥，类型为 String。
     * - 返回值：String，Base64 签名。
     */
    public static String hmacSha256(String text, String secret);

    /**
     * 初始化密钥存储（用于持久化 AES/RSA 密钥）。
     *
     * - config：密钥存储配置，类型为 VKKeyStoreConfig（baseDir/masterKey/autoCreate）。
     */
    public static void initKeyStore(VKKeyStoreConfig config);

    /**
     * 按 keyId 获取或创建 AES 密钥（持久化）。
     *
     * - keyId：密钥标识，类型为 String（建议仅使用字母/数字/._-）。
     * - 返回值：String，Base64 格式 AES 密钥。
     */
    public static String getOrCreateAesKey(String keyId);

    /**
     * 按 keyId 获取或创建 RSA 密钥对（持久化）。
     *
     * - keyId：密钥标识，类型为 String。
     * - 返回值：VKRsaKeyPair，包含公钥/私钥 PEM。
     */
    public static VKRsaKeyPair getOrCreateRsaKeyPair(String keyId);

    /**
     * 轮换指定 keyId 的 AES 密钥。
     *
     * - keyId：密钥标识，类型为 String。
     */
    public static void rotateAesKey(String keyId);

    /**
     * 轮换指定 keyId 的 RSA 密钥对。
     *
     * - keyId：密钥标识，类型为 String。
     */
    public static void rotateRsaKeyPair(String keyId);

    /**
     * 使用 keyId 对应的 AES 密钥加密，并返回带版本/算法/keyId 的密文载荷。
     *
     * - plainText：明文，类型为 String。
     * - keyId：密钥标识，类型为 String。
     * - 返回值：String，格式为 vk1:aes:{keyId}:{base64Cipher}。
     */
    public static String encryptWithKeyId(String plainText, String keyId);

    /**
     * 从载荷中解析 keyId 并完成解密。
     *
     * - cipherPayload：密文载荷，类型为 String，格式 vk1:aes:{keyId}:{base64Cipher}。
     * - 返回值：String，明文。
     */
    public static String decryptWithKeyId(String cipherPayload);
}
```

## 7.2 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.security.VKSecurityConfig;
import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.crypto.VKRsaKeyPair;
import yueyang.vostok.security.file.VKFileType;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;

public class SecurityApiDemo {
    public static void main(String[] args) {
        Vostok.Security.init(new VKSecurityConfig()
                .riskThreshold(VKSecurityRiskLevel.MEDIUM)
                .allowMultiStatement(false)
                .allowCommentToken(false)
                .maxSqlLength(10000));

        var safe = Vostok.Security.checkSql("SELECT * FROM t_user WHERE id = ?", 1L);
        var unsafe = Vostok.Security.checkSql("SELECT * FROM t_user WHERE id = 1 OR 1=1 --");

        System.out.println("safe=" + safe.isSafe());
        System.out.println("unsafe=" + unsafe.isSafe() + ", reasons=" + unsafe.getReasons());

        Vostok.Security.assertSafeSql("SELECT * FROM t_user WHERE id = 1");

        var xss = Vostok.Security.checkXss("<script>alert(1)</script>");
        var cmd = Vostok.Security.checkCommandInjection("ls; rm -rf /");
        var path = Vostok.Security.checkPathTraversal("../../etc/passwd");
        var resp = Vostok.Security.checkSensitiveResponse("{\"phone\":\"13800138000\"}");
        String masked = Vostok.Security.maskSensitiveResponse("{\"phone\":\"13800138000\"}");

        byte[] png = new byte[]{(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        VKFileType type = Vostok.Security.detectFileType(png);
        var magic = Vostok.Security.checkFileMagic(png, VKFileType.PNG, VKFileType.JPEG);
        var upload = Vostok.Security.checkExecutableScriptUpload("run.sh", "#!/bin/bash".getBytes());

        String aesKey = Vostok.Security.generateAesKey();
        String aesCipher = Vostok.Security.encrypt("hello-aes", aesKey);
        String aesPlain = Vostok.Security.decrypt(aesCipher, aesKey);

        VKRsaKeyPair rsa = Vostok.Security.generateRsaKeyPair();
        String rsaCipher = Vostok.Security.encryptByPublicKey("hello-rsa", rsa.getPublicKeyPem());
        String rsaPlain = Vostok.Security.decryptByPrivateKey(rsaCipher, rsa.getPrivateKeyPem());
        String sign = Vostok.Security.sign("payload", rsa.getPrivateKeyPem());
        boolean ok = Vostok.Security.verify("payload", sign, rsa.getPublicKeyPem());

        String shaBase64 = Vostok.Security.sha256("abc");
        String shaHex = Vostok.Security.sha256Hex("abc");
        String hmac = Vostok.Security.hmacSha256("payload", "secret");

        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir("/tmp/vostok-keystore")
                .masterKey("change-this-master-key"));
        String dataCipher = Vostok.Security.encryptWithKeyId("order-1001", "biz-order");
        String dataPlain = Vostok.Security.decryptWithKeyId(dataCipher);

        String persistedAes = Vostok.Security.getOrCreateAesKey("biz-order");
        VKRsaKeyPair persistedRsa = Vostok.Security.getOrCreateRsaKeyPair("biz-sign");
        Vostok.Security.rotateAesKey("biz-order");
    }
}
```

## 7.3 配置详解（VKSecurityConfig）

```java
import yueyang.vostok.security.VKSecurityConfig;
import yueyang.vostok.security.VKSecurityRiskLevel;

VKSecurityConfig cfg = new VKSecurityConfig()
    // 是否启用 Security 检测。
    .enabled(true)
    // 严格模式：增加更激进的关键词策略。
    .strictMode(false)
    // 是否允许 SQL 多语句。
    .allowMultiStatement(false)
    // 是否允许 SQL 注释 token（-- / /* */ / #）。
    .allowCommentToken(false)
    // 最大 SQL 长度阈值。
    .maxSqlLength(10_000)
    // 风险阈值：达到该等级及以上判定为 unsafe。
    .riskThreshold(VKSecurityRiskLevel.MEDIUM)
    // 是否启用内置规则。
    .builtinRulesEnabled(true)
    // 自定义白名单正则（命中则快速放行）。
    .whitelistPatterns("^SELECT\\s+1$")
    // 自定义黑名单正则（命中则高风险）。
    .blacklistPatterns(".*\\bUNION\\b.*")
    // 对空 SQL 等无效输入是否直接抛错。
    .failOnInvalidInput(true);
```

## 7.4 检测说明

- `checkSql(...)`：SQL 注入与危险函数模式检测，支持占位符参数数量校验。
- `checkXss(...)`：检测 `script` 标签、事件处理器、`javascript:` 协议等 payload。
- `checkCommandInjection(...)`：检测 shell 元字符与危险命令组合。
- `checkPathTraversal(...)`：检测 `../`、URL 编码穿越、空字节绕过模式。
- `checkSensitiveResponse(...)`：检测手机号、邮箱、身份证号、银行卡号泄露。
- `maskSensitiveResponse(...)`：对敏感字段进行掩码输出。
- `detectFileType(...)`：按魔数识别常见文件类型。
- `checkFileMagic(...)`：按白名单校验文件魔数类型。
- `checkExecutableScriptUpload(...)`：识别脚本扩展名、可执行魔数和可执行脚本内容特征。
- `encrypt(...) / decrypt(...)`：AES-GCM 便捷加解密接口（支持 Base64 密钥或口令）。
- `generateRsaKeyPair(...)`：生成 PEM 格式 RSA 密钥对。
- `encryptByPublicKey(...) / decryptByPrivateKey(...)`：RSA OAEP 便捷加解密接口。
- `sign(...) / verify(...)`：RSA 签名验签接口（SHA256withRSA）。
- `sha256(...) / sha256Hex(...) / hmacSha256(...)`：常用摘要与消息认证接口。
- `initKeyStore(...)`：初始化密钥持久化存储（本地文件实现）。
- `getOrCreateAesKey(...) / getOrCreateRsaKeyPair(...)`：按 keyId 获取或自动生成持久化密钥。
- `encryptWithKeyId(...) / decryptWithKeyId(...)`：按 keyId 加解密，密文携带 keyId，重启后可解密。
- `rotateAesKey(...) / rotateRsaKeyPair(...)`：按 keyId 执行密钥轮换。

## 7.5 KeyStore 使用注意事项

- `masterKey` 必须妥善管理（建议从环境变量或外部密钥服务注入），不要硬编码到仓库。
- 更换 `masterKey` 后将无法解密历史密钥文件中的内容，除非先执行迁移。
- `baseDir` 建议配置为受限目录，并确保只有服务进程用户可读写。
- `rotateAesKey/rotateRsaKeyPair` 只影响新数据加密，历史数据如需兼容需在业务侧做分版本解密策略。

---

# 8. Event 模块

`Vostok.Event` 为进程内事件总线，支持：

- 同步监听器（`SYNC`）：在 `publish(...)` 当前线程执行。
- 异步监听器（`ASYNC`）：在异步线程池执行。
- 统一发布接口：仅 `publish(...)` 一个方法，由监听器模式决定执行方式。

## 8.1 接口定义

```java
public interface Vostok.Event {
    /**
     * 使用默认配置初始化 Event 模块（幂等）。
     */
    public static void init();

    /**
     * 使用指定配置初始化 Event 模块（幂等）。
     *
     * - config：事件模块配置，类型为 VKEventConfig；传 null 时使用默认配置。
     */
    public static void init(VKEventConfig config);

    /**
     * 使用新配置重建 Event 模块（会重建异步线程池，监听器注册表保留）。
     *
     * - config：新配置，类型为 VKEventConfig；传 null 时使用默认配置。
     */
    public static void reinit(VKEventConfig config);

    /**
     * 判断 Event 模块是否已启动。
     *
     * - 返回值：boolean，true 表示已启动。
     */
    public static boolean started();

    /**
     * 获取当前生效配置快照。
     *
     * - 返回值：VKEventConfig。
     */
    public static VKEventConfig config();

    /**
     * 关闭 Event 模块（释放线程池并清空监听器）。
     */
    public static void close();

    /**
     * 注册同步监听器（默认 SYNC）。
     *
     * - eventType：事件类型，类型为 Class<T>，不能为空。
     * - listener：监听器，类型为 VKEventListener<T>，不能为空。
     * - 返回值：VKEventSubscription，可用于 off(...) 取消订阅。
     */
    public static <T> VKEventSubscription on(Class<T> eventType, VKEventListener<T> listener);

    /**
     * 注册监听器并显式指定模式（SYNC / ASYNC）。
     *
     * - eventType：事件类型，类型为 Class<T>，不能为空。
     * - mode：监听模式，类型为 VKListenerMode；null 时按 SYNC 处理。
     * - listener：监听器，类型为 VKEventListener<T>，不能为空。
     * - 返回值：VKEventSubscription，可用于 off(...) 取消订阅。
     */
    public static <T> VKEventSubscription on(Class<T> eventType, VKListenerMode mode, VKEventListener<T> listener);

    /**
     * 取消指定订阅。
     *
     * - subscription：订阅对象，类型为 VKEventSubscription；null 时忽略。
     */
    public static void off(VKEventSubscription subscription);

    /**
     * 取消某事件类型的全部监听器。
     *
     * - eventType：事件类型，类型为 Class<?>；null 时忽略。
     */
    public static void offAll(Class<?> eventType);

    /**
     * 发布事件（唯一发布方法）。
     *
     * - event：事件对象，类型为 Object，不能为空。
     * - 返回值：VKEventPublishResult，包含命中监听器数、同步执行结果、异步提交结果与耗时。
     *
     * 执行规则：
     * 1) 命中事件类型监听器（支持父类型监听，如 BaseEvent 可接收其子类事件）。
     * 2) 先执行 SYNC 监听器（当前线程，按注册顺序）。
     * 3) 再提交 ASYNC 监听器（线程池异步执行）。
     */
    public static VKEventPublishResult publish(Object event);
}
```

## 8.2 使用 Demo

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.event.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class BaseEvent {}
class UserCreatedEvent extends BaseEvent {
    private final Long userId;
    private final String username;
    UserCreatedEvent(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
}

public class EventApiDemo {
    public static void main(String[] args) throws Exception {
        Vostok.Event.init(new VKEventConfig()
                .enabled(true)
                .asyncCoreThreads(2)
                .asyncMaxThreads(8)
                .asyncQueueCapacity(2000)
                .asyncKeepAliveMs(60_000)
                .rejectionPolicy(VKEventRejectionPolicy.CALLER_RUNS)
                .listenerErrorStrategy(VKEventListenerErrorStrategy.CONTINUE)
                .shutdownWaitMs(3000));

        // 1) 默认注册为同步监听器（SYNC）
        VKEventSubscription auditSub = Vostok.Event.on(UserCreatedEvent.class, e -> {
            System.out.println("[sync-audit] userId=" + e.getUserId());
        });

        // 2) 指定异步监听器（ASYNC）
        CountDownLatch asyncDone = new CountDownLatch(1);
        VKEventSubscription welcomeSub = Vostok.Event.on(UserCreatedEvent.class, VKListenerMode.ASYNC, e -> {
            System.out.println("[async-mail] welcome " + e.getUsername());
            asyncDone.countDown();
        });

        // 3) 注册父类型监听器（可接收子类事件）
        Vostok.Event.on(BaseEvent.class, e -> {
            System.out.println("[sync-base] event=" + e.getClass().getSimpleName());
        });

        // 4) 统一发布（只有一个 publish 方法）
        VKEventPublishResult result = Vostok.Event.publish(new UserCreatedEvent(1001L, "neo"));
        System.out.println("matched=" + result.getMatchedListeners()
                + ", syncExecuted=" + result.getSyncExecuted()
                + ", syncFailed=" + result.getSyncFailed()
                + ", asyncSubmitted=" + result.getAsyncSubmitted()
                + ", asyncRejected=" + result.getAsyncRejected()
                + ", costMs=" + result.getCostMs());

        asyncDone.await(2, TimeUnit.SECONDS);

        // 5) 取消某个监听器
        Vostok.Event.off(welcomeSub);
        Vostok.Event.publish(new UserCreatedEvent(1002L, "trinity"));

        // 6) 取消某事件类型全部监听器
        Vostok.Event.offAll(UserCreatedEvent.class);
        Vostok.Event.publish(new UserCreatedEvent(1003L, "morpheus"));

        // 7) 运行中重载配置（reinit）
        Vostok.Event.reinit(new VKEventConfig()
                .asyncCoreThreads(4)
                .asyncMaxThreads(16)
                .asyncQueueCapacity(5000));

        // 8) 关闭
        Vostok.Event.close();
    }
}
```

## 8.3 配置详解（VKEventConfig）

```java
import yueyang.vostok.event.VKEventConfig;
import yueyang.vostok.event.VKEventListenerErrorStrategy;
import yueyang.vostok.event.VKEventRejectionPolicy;

VKEventConfig cfg = new VKEventConfig()
    // 是否启用事件分发。默认 true；false 时 publish(...) 会直接返回空结果。
    .enabled(true)
    // 异步线程池核心线程数。默认 max(1, CPU/2)。
    .asyncCoreThreads(2)
    // 异步线程池最大线程数。默认 max(core, CPU*2)。
    .asyncMaxThreads(8)
    // 异步任务队列容量。默认 4096。
    .asyncQueueCapacity(4096)
    // 非核心线程空闲回收时间（毫秒）。默认 60000。
    .asyncKeepAliveMs(60_000)
    // 队列满时拒绝策略：CALLER_RUNS / ABORT / DISCARD。默认 CALLER_RUNS。
    .rejectionPolicy(VKEventRejectionPolicy.CALLER_RUNS)
    // 监听器异常策略：CONTINUE / FAIL_FAST。默认 CONTINUE。
    .listenerErrorStrategy(VKEventListenerErrorStrategy.CONTINUE)
    // 关闭时等待异步线程池终止的毫秒数。默认 3000。
    .shutdownWaitMs(3000);
```

## 8.4 行为说明

- `publish(...)` 是唯一发布入口：
  - 同步监听器：当前线程执行，适合审计、轻量内存状态更新等需要“同请求可见”的逻辑。
  - 异步监听器：线程池执行，适合通知、非关键耗时任务等逻辑。
- 事件匹配支持父类型：
  - 对 `BaseEvent` 注册监听后，发布 `UserCreatedEvent extends BaseEvent` 会被命中。
- 顺序保证：
  - 同一次发布中的同步监听器按注册顺序执行。
  - 异步监听器提交到线程池后不保证完成顺序。
- 异常处理：
  - `CONTINUE`：单个同步监听器失败不影响后续同步监听器。
  - `FAIL_FAST`：同步监听器失败立即抛异常并中断后续同步监听器。
  - 异步监听器异常统一记录日志，不回抛给发布方。
- 背压与拒绝：
  - 当异步线程池饱和时，行为由 `rejectionPolicy` 决定，并在 `VKEventPublishResult.asyncRejected` 中体现。

---

# 9. Cache 模块

`Vostok.Cache` 为统一缓存门面，目标是提供可生产落地的缓存基线能力，当前支持：

- `MEMORY`：进程内缓存（开发/测试/降级场景）
- `REDIS`：RESP 协议 Redis 客户端（`SINGLE/SENTINEL/CLUSTER`）

核心原则：

- 不依赖 `Vostok.Config`，全部通过显式配置初始化
- 内置连接池（驱逐、校验、泄漏检测、指标）
- 内置高可用路由（端点失效隔离与恢复）
- 内置缓存治理（防穿透/击穿/雪崩）

## 9.1 生产能力总览

1. 高可用与拓扑能力
- Redis 模式：`SINGLE`、`SENTINEL`、`CLUSTER`
- 多 endpoint 路由与失效端点临时隔离
- Cluster 模式按 key hash 分片路由

2. 连接池生产化
- `minIdle/maxActive/maxWaitMs`
- 后台驱逐线程：idle 回收、定期健康校验、minIdle 自动补足
- 泄漏检测：借出超过阈值计数

3. 连接可靠性
- 半开检测：心跳 `PING`
- 自动重连：失败重建连接
- 退避重试：指数退避 + 抖动

4. 限流与降级
- 连接池级 QPS 限流
- 降级策略：`FAIL_FAST` / `RETURN_NULL` / `SKIP_WRITE`

5. 数据结构命令
- KV：`set/get/delete/exists/expire/incr/mset/mget`
- Hash：`hset/hget/hgetAll/hdel`
- List：`lpush/lrange`
- Set：`sadd/smembers`
- ZSet：`zadd/zrange`
- Key 扫描：`scan`

6. 缓存治理
- 防穿透：空值缓存 + BloomFilter 接口
- 防击穿：single-flight + key 级互斥
- 防雪崩：TTL 抖动（jitter）

7. 安全
- ACL：`username/password`（`AUTH`）
- TLS：`ssl(true)` 启用 SSL Socket

## 9.2 接口定义

```java
public interface Vostok.Cache {
    // lifecycle
    public static void init(VKCacheConfig config);
    public static void init(VKCacheConfigLoader loader);
    public static void initFromEnv(String prefix);
    public static void initFromProperties(Path path, String prefix);
    public static void reinit(VKCacheConfig config);
    public static boolean started();
    public static VKCacheConfig config();
    public static List<VKCachePoolMetrics> poolMetrics();
    public static void close();

    // multi cache / context
    public static void registerCache(String name, VKCacheConfig config);
    public static void withCache(String name, Runnable action);
    public static <T> T withCache(String name, Supplier<T> supplier);
    public static <T> T withKeyLock(String key, Supplier<T> supplier);

    // KV
    public static void set(String key, Object value);
    public static void set(String key, Object value, long ttlMs);
    public static <T> T get(String key, Class<T> type);
    public static <T> T getOrLoad(String key, Class<T> type, long ttlMs, Supplier<T> loader);
    public static long delete(String... keys);
    public static boolean exists(String key);
    public static boolean expire(String key, long ttlMs);
    public static long incrBy(String key, long delta);
    public static <T> List<T> mget(Class<T> type, String... keys);
    public static void mset(Map<String, ?> values, long ttlMs);

    // Hash/List/Set/ZSet/Scan
    public static long hset(String key, String field, Object value);
    public static <T> T hget(String key, String field, Class<T> type);
    public static <T> Map<String, T> hgetAll(String key, Class<T> type);
    public static long hdel(String key, String... fields);
    public static long lpush(String key, Object... values);
    public static <T> List<T> lrange(String key, long start, long stop, Class<T> type);
    public static long sadd(String key, Object... members);
    public static <T> Set<T> smembers(String key, Class<T> type);
    public static long zadd(String key, double score, Object member);
    public static <T> List<T> zrange(String key, long start, long stop, Class<T> type);
    public static List<String> scan(String pattern, int count);
}
```

## 9.3 使用 Demo（生产配置）

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.cache.*;

import java.util.Map;

public class CacheProdDemo {
    public static void main(String[] args) {
        VKCacheConfig cfg = new VKCacheConfig()
                .providerType(VKCacheProviderType.REDIS)
                .redisMode(VKRedisMode.SENTINEL)
                .endpoints("10.0.0.11:6379", "10.0.0.12:6379", "10.0.0.13:6379")
                .sentinelMaster("mymaster")

                // security
                .ssl(true)
                .username("app-user")
                .password("***")

                // pool
                .minIdle(4)
                .maxActive(64)
                .maxWaitMs(1500)
                .testOnBorrow(true)
                .idleValidationIntervalMs(15_000)
                .idleTimeoutMs(120_000)
                .leakDetectMs(60_000)

                // retry / heartbeat
                .heartbeatIntervalMs(10_000)
                .reconnectMaxAttempts(3)
                .retryEnabled(true)
                .maxRetries(2)
                .retryBackoffBaseMs(20)
                .retryBackoffMaxMs(800)
                .retryJitterEnabled(true)

                // anti avalanche / degradation
                .defaultTtlMs(60_000)
                .ttlJitterMs(15_000)
                .rateLimitQps(20_000)
                .degradePolicy(VKCacheDegradePolicy.RETURN_NULL)

                // anti penetration / breakdown
                .nullCacheEnabled(true)
                .nullCacheTtlMs(20_000)
                .singleFlightEnabled(true)
                .keyMutexEnabled(true)
                .bloomFilter(VKBloomFilter.noOp())

                .keyPrefix("app:")
                .codec("json");

        Vostok.Cache.init(cfg);

        // getOrLoad + single-flight + null-cache
        User user = Vostok.Cache.getOrLoad("user:1", User.class, 300_000, () -> loadUser(1L));

        // data structures
        Vostok.Cache.hset("user:1:profile", "nickname", "neo");
        String nick = Vostok.Cache.hget("user:1:profile", "nickname", String.class);

        Vostok.Cache.lpush("jobs", "j3", "j2", "j1");
        var jobs = Vostok.Cache.lrange("jobs", 0, 10, String.class);

        Vostok.Cache.sadd("roles:1", "admin", "dev");
        var roles = Vostok.Cache.smembers("roles:1", String.class);

        Vostok.Cache.zadd("rank", 98.5, "u1");
        Vostok.Cache.zadd("rank", 99.1, "u2");

        Vostok.Cache.mset(Map.of("k1", "v1", "k2", "v2"), 30_000);

        // metrics
        System.out.println(Vostok.Cache.poolMetrics());

        Vostok.Cache.close();
    }

    static User loadUser(long id) {
        return null;
    }

    static class User {
        public Long id;
        public String name;
    }
}
```

## 9.4 配置详解（VKCacheConfig）

```java
VKCacheConfig cfg = new VKCacheConfig()
    .providerType(VKCacheProviderType.REDIS)
    .redisMode(VKRedisMode.SINGLE)          // SINGLE / SENTINEL / CLUSTER
    .endpoints("127.0.0.1:6379")
    .sentinelMaster("mymaster")

    // TLS + ACL
    .ssl(false)
    .username(null)
    .password(null)
    .database(0)

    // network
    .connectTimeoutMs(2000)
    .readTimeoutMs(2000)
    .heartbeatIntervalMs(15000)
    .reconnectMaxAttempts(2)

    // pool
    .minIdle(1)
    .maxActive(8)
    .maxWaitMs(3000)
    .testOnBorrow(true)
    .testOnReturn(false)
    .idleValidationIntervalMs(30000)
    .idleTimeoutMs(120000)
    .leakDetectMs(60000)

    // retry
    .retryEnabled(true)
    .maxRetries(2)
    .retryBackoffBaseMs(30)
    .retryBackoffMaxMs(500)
    .retryJitterEnabled(true)

    // cache behavior
    .defaultTtlMs(0)
    .ttlJitterMs(0)
    .keyPrefix("app:")
    .codec("json")
    .metricsEnabled(true)

    // anti penetration / breakdown
    .nullCacheEnabled(true)
    .nullCacheTtlMs(30000)
    .singleFlightEnabled(true)
    .keyMutexEnabled(true)
    .keyMutexMaxSize(10000)
    .bloomFilter(VKBloomFilter.noOp())

    // anti avalanche / protection
    .rateLimitQps(0)
    .degradePolicy(VKCacheDegradePolicy.FAIL_FAST);
```

## 9.5 显式 Loader（无 Config 依赖）

```java
import yueyang.vostok.cache.VKCacheConfigFactory;

var fromEnv = VKCacheConfigFactory.fromEnv("cache");
var fromMap = VKCacheConfigFactory.fromMap(Map.of("cache.provider", "memory"), "cache");
var fromProperties = VKCacheConfigFactory.fromProperties(Path.of("./cache.properties"), "cache");
```

常用 key（prefix=`cache`）示例：

- `cache.provider=redis`
- `cache.redisMode=cluster`
- `cache.endpoints=10.0.0.11:6379,10.0.0.12:6379`
- `cache.ssl=true`
- `cache.username=app`
- `cache.password=***`
- `cache.maxActive=64`
- `cache.idleValidationIntervalMs=15000`
- `cache.leakDetectMs=60000`
- `cache.rateLimitQps=20000`
- `cache.degradePolicy=RETURN_NULL`
- `cache.nullCacheEnabled=true`
- `cache.singleFlightEnabled=true`
- `cache.ttlJitterMs=15000`

## 9.6 指标说明

`poolMetrics()` 返回每个缓存实例的池状态：

- `total`：池内连接总数
- `active`：借出连接数
- `idle`：空闲连接数
- `borrowTimeouts`：借连接超时次数
- `leakedConnections`：泄漏检测计数
- `evictedConnections`：驱逐/失效销毁连接数
- `rejectedByRateLimit`：限流拒绝次数

## 9.7 说明与边界

- `SENTINEL/CLUSTER` 当前实现为客户端侧多 endpoint 路由与故障隔离；适合作为统一 API 层高可用基础能力。
- `SCAN` 当前返回单次扫描批次（`count` 限制），如需全量遍历请循环调用。
- BloomFilter 由业务注入，默认 `noOp`（始终放行）。
- `RETURN_NULL` / `SKIP_WRITE` 降级策略仅在命中池限流时生效。

---

# 10. Http 模块

`Vostok.Http` 提供面向第三方 API 的统一调用能力，支持命名 Client、鉴权、重试、超时、表单/文件上传、JSON 序列化与指标。

## 10.0 说明与注意事项

- `Vostok.Http` 是客户端调用模块，不是 Web Server；服务端能力请使用 `Vostok.Web`。
- 建议在应用启动时显式 `Vostok.Http.init(...)`，避免按默认配置懒初始化。
- 当使用相对路径（如 `/users/{id}`）时，必须通过 `.client(\"name\")` 指定已注册且包含 `baseUrl` 的命名 Client。
- 默认 `failOnNon2xx=true`，非 `2xx` 会抛出 `VKHttpException(HTTP_STATUS)`；如需手动处理响应可在请求级关闭。
- 重试默认只覆盖幂等方法（`GET/HEAD/OPTIONS/PUT/DELETE`）；若对 `POST` 重试，请配合业务幂等键。
- `VKJson` 适合常见对象映射，复杂泛型响应建议业务侧自行解析/转换。

## 10.1 接口定义

```java
public interface Vostok.Http {
    // 生命周期
    public static void init();
    public static void init(VKHttpConfig config);
    public static void reinit(VKHttpConfig config);
    public static boolean started();
    public static VKHttpConfig config();
    public static void close();

    // 命名 Client
    public static void registerClient(String name, VKHttpClientConfig config);
    public static void withClient(String name, Runnable action);
    public static <T> T withClient(String name, Supplier<T> supplier);
    public static Set<String> clientNames();
    public static String currentClientName();

    // 请求构建
    public static VKHttpRequestBuilder request();
    public static VKHttpRequestBuilder get(String urlOrPath);
    public static VKHttpRequestBuilder post(String urlOrPath);
    public static VKHttpRequestBuilder put(String urlOrPath);
    public static VKHttpRequestBuilder patch(String urlOrPath);
    public static VKHttpRequestBuilder delete(String urlOrPath);
    public static VKHttpRequestBuilder head(String urlOrPath);

    // 执行
    public static VKHttpResponse execute(VKHttpRequest request);
    public static <T> T executeJson(VKHttpRequest request, Class<T> type);
    public static CompletableFuture<VKHttpResponse> executeAsync(VKHttpRequest request);
    public static <T> CompletableFuture<T> executeJsonAsync(VKHttpRequest request, Class<T> type);

    // 指标
    public static VKHttpMetrics metrics();
    public static void resetMetrics();
}
```

## 10.2 使用 Demo

### 10.2.1 基础调用（GET + Path + Query）

```java
Vostok.Http.init(new VKHttpConfig()
        .requestTimeoutMs(3000)
        .maxRetries(1));

Vostok.Http.registerClient("github", new VKHttpClientConfig()
        .baseUrl("https://api.github.com")
        .putHeader("Accept", "application/json"));

var user = Vostok.Http.get("/users/{name}")
        .client("github")
        .path("name", "octocat")
        .query("per_page", 20)
        .executeJson(MyUser.class);
```

### 10.2.2 POST JSON

```java
var response = Vostok.Http.post("/orders")
        .client("erp")
        .bodyJson(Map.of("bizId", "A-1001", "amount", 99.5))
        .execute();

int status = response.statusCode();
String body = response.bodyText();
```

### 10.2.3 鉴权

```java
Vostok.Http.registerClient("openai", new VKHttpClientConfig()
        .baseUrl("https://api.openai.com")
        .auth(new VKBearerAuth("<TOKEN>")));

Vostok.Http.registerClient("internal", new VKHttpClientConfig()
        .baseUrl("https://example.com")
        .auth(VKApiKeyAuth.query("api_key", "k-xxx")));
```

### 10.2.4 表单与文件上传

```java
// x-www-form-urlencoded
Vostok.Http.post("/oauth/token")
        .client("auth")
        .form("grant_type", "client_credentials")
        .form("client_id", "abc")
        .form("client_secret", "***")
        .execute();

// multipart/form-data
Vostok.Http.post("/upload")
        .client("file")
        .multipart("desc", "avatar")
        .multipart("file", "a.png", "image/png", bytes)
        .execute();
```

### 10.2.5 异步调用

```java
CompletableFuture<MyResp> future = Vostok.Http.get("/jobs/{id}")
        .client("job")
        .path("id", 1001)
        .executeJsonAsync(MyResp.class);

MyResp resp = future.join();
```

### 10.2.6 HTTPS 证书配置（代码内）

```java
Vostok.Http.registerClient("secure-api", new VKHttpClientConfig()
        .baseUrl("https://api.example.com")
        // 校验服务端证书（单向 TLS）
        .trustStore("/path/to/truststore.p12", "changeit", "PKCS12")
        // 可选：客户端证书（双向 TLS / mTLS）
        .keyStore("/path/to/client-keystore.p12", "changeit", "changeit", "PKCS12"));

String body = Vostok.Http.get("/v1/ping")
        .client("secure-api")
        .execute()
        .bodyText();
```

如需完全自定义 TLS（协议、TrustManager、KeyManager），可直接注入：

```java
SSLContext ssl = buildYourSslContext();
Vostok.Http.registerClient("secure-api", new VKHttpClientConfig()
        .baseUrl("https://api.example.com")
        .sslContext(ssl));
```

## 10.3 配置详解

### 10.3.1 VKHttpConfig（全局）

- `connectTimeoutMs`：连接超时（默认 `3000`）
- `totalTimeoutMs`：总超时（默认 `10000`）
- `requestTimeoutMs`：兼容别名，等价于 `totalTimeoutMs(...)`
- `readTimeoutMs`：读取响应超时（默认 `0`，表示不单独限制）
- `maxRetries`：默认重试次数（默认 `1`）
- `retryBackoffBaseMs/retryBackoffMaxMs/retryJitterEnabled`：退避策略
- `maxRetryDelayMs`：单次重试等待上限
- `retryOnNetworkError`：网络异常是否重试（默认 `true`）
- `retryOnTimeout`：超时异常是否重试（默认 `true`）
- `respectRetryAfter`：是否优先遵循响应头 `Retry-After`（默认 `true`）
- `retryOnStatuses`：可重试状态码（默认 `429/502/503/504`）
- `retryMethods`：允许重试的方法（默认 `GET/HEAD/OPTIONS/PUT/DELETE`）
- `requireIdempotencyKeyForUnsafeRetry`：对非幂等方法重试时是否要求幂等键（默认 `true`）
- `idempotencyKeyHeader`：幂等键请求头名（默认 `Idempotency-Key`）
- `failOnNon2xx`：是否将非 2xx 视为异常（默认 `true`）
- `followRedirects`：是否跟随重定向（默认 `true`）
- `maxResponseBodyBytes`：响应体大小上限（默认 `8MB`）
- `clientReuseIdleEvictMs`：复用 `HttpClient` 的空闲淘汰时间
- `rateLimitQps/rateLimitBurst`：客户端限流（QPS + 突发桶容量，`QPS<=0` 关闭）
- `circuitEnabled`：是否启用熔断器
- `circuitWindowSize/circuitMinCalls/circuitFailureRateThreshold`：熔断统计窗口与阈值
- `circuitOpenWaitMs/circuitHalfOpenMaxCalls`：熔断打开保持时间与半开探测请求数
- `circuitRecordStatuses`：计入熔断失败统计的状态码（默认 `429,500,502,503,504`）
- `bulkheadEnabled`：是否启用并发隔离
- `bulkheadMaxConcurrent/bulkheadQueueSize/bulkheadAcquireTimeoutMs`：并发槽、排队长度、排队等待时间
- `logEnabled`：是否输出调用日志
- `metricsEnabled`：是否启用指标采集
- `userAgent/defaultHeaders`：全局请求头

### 10.3.2 VKHttpClientConfig（命名 Client）

- `baseUrl`：基础地址（调用相对路径时必填）
- `connectTimeoutMs/totalTimeoutMs/readTimeoutMs`：覆盖全局超时
- `requestTimeoutMs(...)`：兼容别名，等价于 `totalTimeoutMs(...)`
- `maxRetries/retryOnStatuses/retryMethods`：覆盖全局重试
- `retryOnNetworkError/retryOnTimeout/respectRetryAfter`：覆盖全局重试行为
- `requireIdempotencyKeyForUnsafeRetry/idempotencyKeyHeader`：覆盖幂等重试约束
- `maxRetryDelayMs`：覆盖重试等待上限
- `failOnNon2xx/followRedirects`：覆盖全局行为
- `rateLimitQps/rateLimitBurst`：覆盖全局限流策略
- `circuitEnabled/circuitWindowSize/circuitMinCalls/circuitFailureRateThreshold/circuitOpenWaitMs/circuitHalfOpenMaxCalls/circuitRecordStatuses`：覆盖全局熔断策略
- `bulkheadEnabled/bulkheadMaxConcurrent/bulkheadQueueSize/bulkheadAcquireTimeoutMs`：覆盖全局并发隔离策略
- `maxResponseBodyBytes`：覆盖全局响应上限
- `defaultHeaders/userAgent`：Client 级请求头
- `auth`：Client 级鉴权策略
- `trustStore(path, password, type)`：Client 级信任库（服务端证书校验）
- `keyStore(path, storePassword, keyPassword, type)`：Client 级密钥库（mTLS 客户端证书）
- `sslContext(SSLContext)`：直接注入自定义 TLS 上下文（优先级高于 trustStore/keyStore）

## 10.4 错误模型

`Vostok.Http` 抛出 `VKHttpException`，并包含 `VKHttpErrorCode`：

- `INVALID_ARGUMENT`：非法参数
- `CONFIG_ERROR`：配置错误（如相对路径未配置 `baseUrl`）
- `NETWORK_ERROR`：网络 I/O 错误
- `CONNECT_TIMEOUT`：连接超时
- `READ_TIMEOUT`：读超时
- `TOTAL_TIMEOUT`：总超时
- `TIMEOUT`：兼容保留超时类型
- `RATE_LIMITED`：客户端限流拒绝
- `BULKHEAD_REJECTED`：并发隔离拒绝
- `CIRCUIT_OPEN`：熔断器打开拒绝
- `HTTP_STATUS`：非 2xx（在 `failOnNon2xx=true` 时）
- `RESPONSE_TOO_LARGE`：响应体超限
- `SERIALIZATION_ERROR`：JSON 序列化/反序列化失败

## 10.5 指标说明

通过 `Vostok.Http.metrics()` 获取快照：

- `totalCalls/successCalls/failedCalls`
- `retriedCalls`
- `timeoutCalls/networkErrorCalls`
- `totalCostMs`
- `statusCounts`（按状态码聚合）

可通过 `Vostok.Http.resetMetrics()` 清零。

## 10.6 说明与边界

- `Vostok.Http` 是 HTTP Client，不是 Web Server。
- 当前默认 JSON 能力使用 `VKJson`，复杂泛型反序列化建议业务侧自行转换。
- 重试默认仅覆盖幂等方法；如需对 `POST/PATCH` 重试，建议显式设置 `retryMethods(...)` 并提供 `Idempotency-Key`。
- 非 2xx 默认抛异常，可在请求级调用 `.failOnNon2xx(false)` 改为手动处理响应。
- HTTPS 可通过 `VKHttpClientConfig` 代码内配置 `trustStore/keyStore`，无需 JVM 全局 `-Djavax.net.ssl.*` 参数。
- 命名 Client 会复用底层 `HttpClient`；配置变更（`registerClient/reinit/close`）会触发复用缓存刷新。
- 默认执行顺序为：`RateLimit -> CircuitBreaker -> Bulkhead -> Execute`。
