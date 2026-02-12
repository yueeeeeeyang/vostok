# Vostok

JDK 17+ 纯 JDBC 的零依赖 CRUD 组件（测试依赖除外），提供注解实体、SQL 构建、事务、连接池、方言、多数据源、批处理、监控与插件等能力。

**重要提醒**
当前项目仅用于实验和技术验证，不建议用于生产环境。

**核心特性**
- 注解实体即 CRUD（`@VKEntity` / `@VKId` / `@VKColumn` / `@VKIgnore`）
- 轻量连接池（预热、空闲回收、泄露检测、预编译缓存）
- 事务传播、隔离级别、只读、超时与保存点
- 条件查询 / 排序 / 分页 / 投影 / 聚合
- SQL 日志、慢 SQL、耗时分布与 TopN
- EXISTS / IN / NOT IN 子查询
- 多数据源切换与上下文传播
- DDL 校验（可选）
- 插件拦截器
- 元数据热更新

**运行环境**
- JDK 17+
- 纯 JDBC（生产环境需自行提供数据库驱动）

**快速上手**
```java
import yueyang.vostok.annotation.*;
import yueyang.vostok.config.*;
import yueyang.vostok.core.Vostok;
import yueyang.vostok.dialect.VKDialectType;

@VKEntity(table = "t_user")
public class User {
    @VKId
    private Long id;

    @VKColumn(name = "user_name")
    private String name;

    private Integer age;
}

DataSourceConfig cfg = new DataSourceConfig()
    .url("jdbc:mysql://localhost:3306/demo")
    .username("root")
    .password("123456")
    .driver("com.mysql.cj.jdbc.Driver")
    .dialect(VKDialectType.MYSQL)
    .minIdle(1)
    .maxActive(10)
    .maxWaitMs(30000)
    .validationQuery("SELECT 1");

Vostok.init(cfg, "com.example.entity");
```

**实体定义**
```java
@VKEntity(table = "t_task")
public class Task {
    @VKId
    private Long id;

    @VKColumn(name = "task_name")
    private String name;

    @VKIgnore
    private String transientField;
}
```

**CRUD 基础用法**
```java
User u = new User();
Vostok.insert(u);

u.setName("Tom");
Vostok.update(u);

Vostok.delete(User.class, 1L);

User one = Vostok.findById(User.class, 1L);
List<User> all = Vostok.findAll(User.class);
```

**分页查询与总数**
```java
import yueyang.vostok.query.*;

VKQuery q = VKQuery.create()
    .where(VKCondition.of("name", VKOperator.LIKE, "%tom%"))
    .where(VKCondition.of("age", VKOperator.GE, 18))
    .orderBy(VKOrder.desc("id"))
    .limit(20)
    .offset(0);

List<User> list = Vostok.query(User.class, q);
long total = Vostok.count(User.class, q);
```
说明：`count` 只统计当前条件，不受 `limit/offset` 影响。

**高级查询示例**
```java
// 复合条件（OR + BETWEEN + NOT IN）
VKQuery q = VKQuery.create()
    .or(
        VKCondition.of("name", VKOperator.LIKE, "%Tom%"),
        VKCondition.of("name", VKOperator.LIKE, "%Jack%")
    )
    .where(VKCondition.of("age", VKOperator.BETWEEN, 18, 30))
    .where(VKCondition.of("id", VKOperator.NOT_IN, 999, 1000));

// GROUP BY + HAVING + 聚合
VKQuery agg = VKQuery.create()
    .groupBy("age")
    .having(VKCondition.raw("COUNT(1)", VKOperator.GT, 1))
    .selectAggregates(VKAggregate.countAll("cnt"));
List<Object[]> rows = Vostok.aggregate(User.class, agg);

// 投影查询
VKQuery proj = VKQuery.create().orderBy(VKOrder.asc("id"));
List<User> names = Vostok.queryColumns(User.class, proj, "name");
```

**子查询（EXISTS / IN）与白名单**
子查询与 raw 表达式必须注册白名单，以降低 SQL 注入风险。
```java
Vostok.registerRawSql("COUNT(1)", "1");
Vostok.registerSubquery("SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?");
Vostok.registerSubquery("SELECT id FROM t_user WHERE age >= ?");

VKQuery q1 = VKQuery.create()
    .where(VKCondition.exists("SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?", 20));

VKQuery q2 = VKQuery.create()
    .where(VKCondition.inSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 20));

VKQuery q3 = VKQuery.create()
    .where(VKCondition.notInSubquery("id", "SELECT id FROM t_user WHERE age >= ?", 20));
```
规则：
- raw/subquery 必须完全匹配（忽略大小写、多余空白）。
- subquery `?` 占位符数量必须与参数数量一致。

多数据源隔离注册示例：
```java
Vostok.registerRawSql("ds2", new String[]{"COUNT(1)"});
Vostok.registerSubquery("ds2", new String[]{"SELECT id FROM t_user WHERE age >= ?"});
```

**事务（传播 / 隔离 / 只读）**
```java
Vostok.tx(() -> {
    Vostok.insert(user);
    Vostok.tx(() -> {
        Vostok.insert(user2);
    }, VKTxPropagation.REQUIRES_NEW, VKTxIsolation.READ_COMMITTED, false);
}, VKTxPropagation.REQUIRED, VKTxIsolation.REPEATABLE_READ, false);

Vostok.tx(() -> {
    Vostok.findAll(User.class);
}, VKTxPropagation.SUPPORTS, VKTxIsolation.DEFAULT, true);
```
说明：事务上下文仅限当前线程，异步线程不会自动传播事务。

**Savepoint（默认开启）**
```java
Vostok.tx(() -> {
    Vostok.insert(u1);
    try {
        Vostok.tx(() -> {
            Vostok.insert(u2);
            throw new RuntimeException("inner");
        }, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT);
    } catch (Exception ignore) {
    }
    Vostok.insert(u3);
});
```

**批量操作与明细**
```java
List<User> users = List.of(u1, u2, u3);
int insertCount = Vostok.batchInsert(users);

var detail = Vostok.batchInsertDetail(users);
int ok = detail.totalSuccess();
int fail = detail.totalFail();
```

**多数据源**
```java
DataSourceConfig cfg2 = new DataSourceConfig()
    .url("jdbc:h2:mem:ds2")
    .username("sa")
    .password("")
    .driver("org.h2.Driver");

Vostok.registerDataSource("ds2", cfg2);
Vostok.withDataSource("ds2", () -> Vostok.insert(user));
```

**异步上下文（VostokContext）**
```java
ExecutorService pool = Executors.newFixedThreadPool(4);

Vostok.withDataSource("ds2", () -> {
    Vostok.insert(user);
    VostokContext ctx = Vostok.captureContext();
    CompletableFuture<Integer> future = CompletableFuture.supplyAsync(
        Vostok.wrap(ctx, () -> Vostok.findAll(User.class).size()), pool
    );
});
```
说明：
- `VostokContext` 仅传播数据源上下文，不传播事务。
- 事务需在异步线程内显式 `Vostok.tx(...)`。

**多数据源 + 异步传递（组合示例）**
```java
DataSourceConfig cfg = new DataSourceConfig()
        .url("jdbc:h2:mem:devkit;MODE=MySQL;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .driver("org.h2.Driver")
        .dialect(VKDialectType.MYSQL);
Vostok.init(cfg, "yueyang.vostok");

DataSourceConfig cfg2 = new DataSourceConfig()
        .url("jdbc:h2:mem:devkit2;MODE=MySQL;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .driver("org.h2.Driver")
        .dialect(VKDialectType.MYSQL);
Vostok.registerDataSource("ds2", cfg2);

ExecutorService es = Executors.newFixedThreadPool(2);

// 主线程默认使用 default 数据源
Vostok.insert(user("D1", 1));

// 在 ds2 上异步执行，并确保上下文被正确传递
CompletableFuture<Integer> f = Vostok.withDataSource("ds2", () -> {
    Vostok.insert(user("D2", 2));
    return CompletableFuture.supplyAsync(
            Vostok.wrap(() -> Vostok.findAll(UserEntity.class).size()), es
    );
});

Integer ds2Count = f.get(3, TimeUnit.SECONDS);
Integer ds1Count = Vostok.findAll(UserEntity.class).size();

System.out.println("ds2 size=" + ds2Count + ", default size=" + ds1Count);

es.shutdown();
```

**插件拦截器**
```java
Vostok.registerInterceptor(new VKInterceptor() {
    @Override
    public void beforeExecute(String sql, Object[] params) {
        if (sql != null && sql.contains("t_secret")) {
            throw new IllegalStateException("blocked");
        }
    }

    @Override
    public void afterExecute(String sql, Object[] params, long costMs, boolean success, Throwable error) {
        if (costMs > 200) {
            System.out.println("[SLOW] " + costMs + "ms: " + sql);
        }
        if (!success && error != null) {
            System.out.println("[FAIL] " + error.getMessage());
        }
    }
});
```

**监控与诊断**
```java
var metrics = Vostok.poolMetrics();
String report = Vostok.report();
```
说明：
- 监控开关关闭时不会执行采集逻辑，避免额外开销。
- 慢 SQL TopN 默认关闭（`slowSqlTopN=0`）。

**慢 SQL TopN（可选）**
```java
DataSourceConfig cfg = new DataSourceConfig()
    .slowSqlTopN(10)
    .slowSqlMs(200);
```

**元数据热更新**
```java
Vostok.refreshMeta(); // 使用初始化时的包名
Vostok.refreshMeta("com.example.entity");
```

**异常分层**
```java
try {
    Vostok.insert(user);
} catch (yueyang.vostok.exception.VKSqlException e) {
    // SQL 异常（含 SQLState/错误码）
} catch (yueyang.vostok.exception.VKMetaException e) {
    // 元数据异常
}
```

**错误码与异常说明**
详见 `docs/errors.md`。

**可插拔扫描器**
在复杂 ClassLoader/容器环境下可替换默认扫描器：
```java
Vostok.setScanner((pkgs) -> Set.of(UserEntity.class, TaskEntity.class));
Vostok.init(cfg, "ignored.pkg");
```

**配置参考（DataSourceConfig）**
- `url` / `username` / `password` / `driver`：JDBC 基本配置
- `dialect`：方言（可选）
- `minIdle` / `maxActive` / `maxWaitMs`：连接池大小与等待
- `testOnBorrow` / `testOnReturn`：借出/归还时是否校验连接（默认关闭，开启会显著降低性能，生产建议仅在需要时开启）
- `validationQuery` / `validationTimeoutSec`：连接有效性探测
- `idleValidationIntervalMs`：空闲连接定期校验与回收间隔
- `preheatEnabled`：连接池预热开关（预创建 minIdle）
- `idleTimeoutMs` / `leakDetectMs`：空闲回收与泄露检测
- `statementCacheSize`：预编译 SQL 缓存大小（每个连接）
- `sqlTemplateCacheSize`：SQL 模板缓存大小（每个数据源）
- `retryEnabled` / `maxRetries`：可重试异常开关与次数
- `retryBackoffBaseMs` / `retryBackoffMaxMs`：指数退避配置
- `retrySqlStatePrefixes`：SQLState 白名单前缀
- `batchSize` / `batchFailStrategy`：批处理分片与失败策略
- `logSql` / `logParams`：SQL 日志与参数打印
- `slowSqlMs` / `slowSqlTopN`：慢 SQL 阈值与 TopN
- `sqlMetricsEnabled`：是否统计 SQL 耗时分布
- `validateDdl` / `ddlSchema`：DDL 校验
- `savepointEnabled`：是否启用 Savepoint
- `txTimeoutMs`：事务超时控制
- `queryTimeoutMs`：非事务 SQL 超时（毫秒）

**全量扫描**
如果 `Vostok.init(cfg)` 不传入包名，将扫描整个 classpath。
