# Vostok

Vostok 是一个面向 JDK 17+ 的全能框架，当前包含高性能数据访问（Data）与轻量 Web 服务器（Web）两大模块，整体保持零依赖（测试依赖除外）。

**重要提醒**
当前项目仅用于实验和技术验证，不建议用于生产环境。

**模块一览**
- Common：通用注解、实体扫描与 JSON 序列化。
- Data：纯 JDBC 的零依赖 ORM/CRUD 组件，内建连接池、事务、SQL 构建与多数据源。
- Web：轻量高性能 Web 服务器，支持中间件与基础路由。

**运行环境**
- JDK 17+
- 纯 JDBC（生产环境需自行提供数据库驱动）

**Data 快速上手**
```java
import yueyang.vostok.common.annotation.VKEntity;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKIgnore;
import yueyang.vostok.data.*;
import yueyang.vostok.Vostok;
import yueyang.vostok.data.dialect.VKDialectType;

@VKEntity(table = "t_user")
public class User {
    @VKId
    private Long id;

    @VKColumn(name = "user_name")
    private String name;

    private Integer age;
}

VKDataConfig cfg = new VKDataConfig()
    .url("jdbc:mysql://localhost:3306/demo")
    .username("root")
    .password("123456")
    .driver("com.mysql.cj.jdbc.Driver")
    .dialect(VKDialectType.MYSQL)
    .minIdle(1)
    .maxActive(10)
    .maxWaitMs(30000)
    .validationQuery("SELECT 1");

Vostok.Data.init(cfg, "com.example.entity");
```

**Web 快速上手**
```java
import yueyang.vostok.Vostok;

Vostok.Web.init(8080)
    .get("/ping", (req, res) -> res.text("ok"))
    .get("/json", (req, res) -> res.json("{\"ok\":true}"))
    .post("/echo", (req, res) -> res.text(req.bodyText()));

Vostok.Web.start();
```

**Common 模块**

Common 模块提供通用注解、实体扫描与 JSON 能力，供 Data/Web 共同使用。

**VKEntity（通用实体注解）**
- 路径：`yueyang.vostok.common.annotation.VKEntity`
- 用于标记实体类，Data 与 Web 都会根据它进行扫描与解析。

**实体扫描（VKScanner）**
```java
import yueyang.vostok.common.scan.VKScanner;

// 扫描指定包
var entities = VKScanner.scan("com.example.entity");

// 不传包名则扫描全 classpath
var allEntities = VKScanner.scan();
```

**JSON 序列化 / 反序列化（支持嵌套对象）**
```java
import yueyang.vostok.common.json.VKJson;

String json = VKJson.toJson(obj);
MyType obj2 = VKJson.fromJson(json, MyType.class);
```
说明：
- 支持嵌套对象、数组、List、Map、基础类型。
- JSON 与实体字段名一一对应（无第三方依赖）。

**Data 模块**

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
Vostok.Data.insert(u);

u.setName("Tom");
Vostok.Data.update(u);

Vostok.Data.delete(User.class, 1L);

User one = Vostok.Data.findById(User.class, 1L);
List<User> all = Vostok.Data.findAll(User.class);
```

**分页查询与总数**
```java
import yueyang.vostok.data.query.*;

VKQuery q = VKQuery.create()
    .where(VKCondition.of("name", VKOperator.LIKE, "%tom%"))
    .where(VKCondition.of("age", VKOperator.GE, 18))
    .orderBy(VKOrder.desc("id"))
    .limit(20)
    .offset(0);

List<User> list = Vostok.Data.query(User.class, q);
long total = Vostok.Data.count(User.class, q);
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
List<Object[]> rows = Vostok.Data.aggregate(User.class, agg);

// 投影查询
VKQuery proj = VKQuery.create().orderBy(VKOrder.asc("id"));
List<User> names = Vostok.Data.queryColumns(User.class, proj, "name");
```

**子查询（EXISTS / IN）与白名单**
子查询与 raw 表达式必须注册白名单，以降低 SQL 注入风险。
```java
Vostok.Data.registerRawSql("COUNT(1)", "1");
Vostok.Data.registerSubquery("SELECT 1 FROM t_user u2 WHERE u2.id = t_user.id AND u2.age >= ?");
Vostok.Data.registerSubquery("SELECT id FROM t_user WHERE age >= ?");

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
Vostok.Data.registerRawSql("ds2", new String[]{"COUNT(1)"});
Vostok.Data.registerSubquery("ds2", new String[]{"SELECT id FROM t_user WHERE age >= ?"});
```

**事务（传播 / 隔离 / 只读）**
```java
Vostok.Data.tx(() -> {
    Vostok.Data.insert(user);
    Vostok.Data.tx(() -> {
        Vostok.Data.insert(user2);
    }, VKTxPropagation.REQUIRES_NEW, VKTxIsolation.READ_COMMITTED, false);
}, VKTxPropagation.REQUIRED, VKTxIsolation.REPEATABLE_READ, false);

Vostok.Data.tx(() -> {
    Vostok.Data.findAll(User.class);
}, VKTxPropagation.SUPPORTS, VKTxIsolation.DEFAULT, true);
```
说明：事务上下文仅限当前线程，异步线程不会自动传播事务。

**Savepoint（默认开启）**
```java
Vostok.Data.tx(() -> {
    Vostok.Data.insert(u1);
    try {
        Vostok.Data.tx(() -> {
            Vostok.Data.insert(u2);
            throw new RuntimeException("inner");
        }, VKTxPropagation.REQUIRED, VKTxIsolation.DEFAULT);
    } catch (Exception ignore) {
    }
    Vostok.Data.insert(u3);
});
```

**批量操作与明细**
```java
List<User> users = List.of(u1, u2, u3);
int insertCount = Vostok.Data.batchInsert(users);

var detail = Vostok.Data.batchInsertDetail(users);
int ok = detail.totalSuccess();
int fail = detail.totalFail();
```

**多数据源**
```java
VKDataConfig cfg2 = new VKDataConfig()
    .url("jdbc:h2:mem:ds2")
    .username("sa")
    .password("")
    .driver("org.h2.Driver");

Vostok.Data.registerDataSource("ds2", cfg2);
Vostok.Data.withDataSource("ds2", () -> Vostok.Data.insert(user));
```

**异步上下文（VostokContext）**
```java
ExecutorService pool = Executors.newFixedThreadPool(4);

Vostok.Data.withDataSource("ds2", () -> {
    Vostok.Data.insert(user);
    VostokContext ctx = Vostok.Data.captureContext();
    CompletableFuture<Integer> future = CompletableFuture.supplyAsync(
        Vostok.Data.wrap(ctx, () -> Vostok.Data.findAll(User.class).size()), pool
    );
});
```
说明：
- `VostokContext` 仅传播数据源上下文，不传播事务。
- 事务需在异步线程内显式 `Vostok.Data.tx(...)`。

**多数据源 + 异步传递（组合示例）**
```java
VKDataConfig cfg = new VKDataConfig()
        .url("jdbc:h2:mem:devkit;MODE=MySQL;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .driver("org.h2.Driver")
        .dialect(VKDialectType.MYSQL);
Vostok.Data.init(cfg, "yueyang.vostok");

VKDataConfig cfg2 = new VKDataConfig()
        .url("jdbc:h2:mem:devkit2;MODE=MySQL;DB_CLOSE_DELAY=-1")
        .username("sa")
        .password("")
        .driver("org.h2.Driver")
        .dialect(VKDialectType.MYSQL);
Vostok.Data.registerDataSource("ds2", cfg2);

ExecutorService es = Executors.newFixedThreadPool(2);

// 主线程默认使用 default 数据源
Vostok.Data.insert(user("D1", 1));

// 在 ds2 上异步执行，并确保上下文被正确传递
CompletableFuture<Integer> f = Vostok.Data.withDataSource("ds2", () -> {
    Vostok.Data.insert(user("D2", 2));
    return CompletableFuture.supplyAsync(
            Vostok.Data.wrap(() -> Vostok.Data.findAll(UserEntity.class).size()), es
    );
});

Integer ds2Count = f.get(3, TimeUnit.SECONDS);
Integer ds1Count = Vostok.Data.findAll(UserEntity.class).size();

System.out.println("ds2 size=" + ds2Count + ", default size=" + ds1Count);

es.shutdown();
```

**插件拦截器**
```java
Vostok.Data.registerInterceptor(new VKInterceptor() {
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
var metrics = Vostok.Data.poolMetrics();
String report = Vostok.Data.report();
```
说明：
- 监控开关关闭时不会执行采集逻辑，避免额外开销。
- 慢 SQL TopN 默认关闭（`slowSqlTopN=0`）。

**慢 SQL TopN（可选）**
```java
VKDataConfig cfg = new VKDataConfig()
    .slowSqlTopN(10)
    .slowSqlMs(200);
```

**元数据热更新**
```java
Vostok.Data.refreshMeta(); // 使用初始化时的包名
Vostok.Data.refreshMeta("com.example.entity");
```

**异常分层**
```java
try {
    Vostok.Data.insert(user);
} catch (yueyang.vostok.data.exception.VKSqlException e) {
    // SQL 异常（含 SQLState/错误码）
} catch (yueyang.vostok.data.exception.VKMetaException e) {
    // 元数据异常
}
```

**错误码与异常说明**
详见 `docs/errors.md`。

**可插拔扫描器**
在复杂 ClassLoader/容器环境下可替换默认扫描器：
```java
import yueyang.vostok.common.scan.VKScanner;

Vostok.Data.setScanner((pkgs) -> Set.of(UserEntity.class, TaskEntity.class));
Vostok.Data.init(cfg, "ignored.pkg");

// 恢复默认扫描器
Vostok.Data.setScanner(VKScanner::scan);
```

**配置参考（VKDataConfig）**
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

**性能对比报告**
单位：ops/s（越高越好）
说明：若误差为 N/A，表示该基准仅运行 1 次测量（误差不可用）。

| 指标 | Vostok | MyBatis-Plus | Spring Data JPA |
|---|---|---|---|
| 单行插入 | 30,428.29 | 28,072.37 | 23,888.42 |
| 批量插入 | 110.33 | 349.31 | 108.25 |
| 按主键查询 | 123,299.99 | 113,422.30 | 60,881.78 |
| 条件分页查询 | 553.13 | 609.30 | N/A |
| 更新 | 46,044.14 | 37,491.13 | 19,038.95 |
| 删除 | 131,281.90 | 63,672.31 | N/A |

**全量扫描**
如果 `Vostok.Data.init(cfg)` 不传入包名，将扫描整个 classpath。

**Web 模块**

**核心特性**
- 纯 Java NIO Reactor 模式，低开销高并发
- 多 Reactor 线程模型（Accept + IO 分离）
- 路由 + 中间件链
- 支持 Keep-Alive 与基础错误处理
- 支持 Chunked 请求体与 `Expect: 100-continue`
- 内置访问日志（AccessLog）
- TraceId 贯穿请求链
- 支持静态资源目录映射

**使用方式**
见上方 **Web 快速上手** 示例。

**自动 CRUD API（零代码）**
在实体类添加 `@VKEntity` 后，直接开启自动 CRUD：
```java
Vostok.Data.init(cfg, "com.example.entity");

// 不传包名：扫描全 classpath
Vostok.Web.init(8080).autoCrudApi();

// 仅扫描指定包
Vostok.Web.init(8080).autoCrudApi("com.example.entity");
```

**风格切换**
```java
import yueyang.vostok.web.auto.VKCrudStyle;

// 标准 RESTful（默认）
Vostok.Web.init(8080).autoCrudApi(VKCrudStyle.RESTFUL);

// 传统风格（仅 GET/POST）
Vostok.Web.init(8080).autoCrudApi(VKCrudStyle.TRADITIONAL);
```

**路由规则**
- 若 `@VKEntity(path = "...")` 填写了 `path`，则使用该值作为 API 前缀。
- 未填写 `path` 时，路由前缀默认来自实体类名：去掉 `Entity` 后缀并转小驼峰。
- 例如 `UserEntity` → `/user`，`TaskEntity` → `/task`。

**自动 CRUD 映射（RESTful）**
- `GET /user`：查询列表（`Vostok.Data.findAll`）
- `GET /user/{id}`：查询单条（`Vostok.Data.findById`）
- `POST /user`：新增（`Vostok.Data.insert`）
- `PUT /user/{id}`：更新（`Vostok.Data.update`）
- `DELETE /user/{id}`：删除（`Vostok.Data.delete`）

**自动 CRUD 映射（传统风格，仅 GET/POST）**
- `GET /user/list`：查询列表
- `GET /user/get?id=1`：查询单条
- `POST /user/create`：新增
- `POST /user/update?id=1`：更新
- `POST /user/delete?id=1`：删除

**返回格式**
- `GET` 返回 JSON 对象或数组。
- `POST` 返回 `{"inserted":n}` 且状态码 201。
- `PUT` 返回 `{"updated":n}`。
- `DELETE` 返回 `{"deleted":n}`。

**路由创建示例**
```java
import yueyang.vostok.Vostok;

Vostok.Web.init(8080)
    .get("/ping", (req, res) -> res.text("ok"))
    .get("/users", (req, res) -> res.json("[{\"id\":1,\"name\":\"Tom\"}]"))
    .post("/users", (req, res) -> res.json("{\"ok\":true}"))
    .route("PUT", "/users/1", (req, res) -> res.text("updated"))
    .route("DELETE", "/users/1", (req, res) -> res.text("deleted"));

Vostok.Web.start();
```

**静态资源**
```java
Vostok.Web.init(8080)
    .staticDir("/static", "/var/www");
```
访问 `/static/app.js` 将映射到 `/var/www/app.js`。

**TraceId**
- 默认生成 `X-Trace-Id` 返回头。
- 如果请求中携带 `X-Trace-Id`，将原样透传。

**AccessLog**
- 默认开启，可通过 `VKWebConfig.accessLogEnabled(false)` 关闭。
**中间件示例**
```java
import yueyang.vostok.Vostok;

Vostok.Web.init(8080)
    .use((req, res, chain) -> {
        long start = System.currentTimeMillis();
        res.header("X-Trace-Id", String.valueOf(start));
        chain.next(req, res);
        long cost = System.currentTimeMillis() - start;
        System.out.println(req.method() + " " + req.path() + " cost=" + cost + "ms");
    })
    .use((req, res, chain) -> {
        String token = req.header("x-token");
        if (token == null || token.isEmpty()) {
            res.status(401).text("Unauthorized");
            return;
        }
        chain.next(req, res);
    })
    .get("/secure", (req, res) -> res.text("ok"));

Vostok.Web.start();
```

**配置参考（VKWebConfig）**
- `port`：监听端口。可传 `0` 让系统自动分配空闲端口。
- `ioThreads`：IO 线程数（Reactor 数量）。建议 1 或少量。
- `workerThreads`：业务线程池线程数。默认按 CPU 核心数 * 2。
- `backlog`：ServerSocket backlog 队列长度。
- `readBufferSize`：每连接读缓冲区大小（字节）。
- `maxHeaderBytes`：请求头最大字节数，超过返回 431。
- `maxBodyBytes`：请求体最大字节数，超过返回 413。
- `keepAliveTimeoutMs`：Keep-Alive 空闲超时（毫秒）。
- `maxConnections`：最大连接数，超过直接拒绝。
- `readTimeoutMs`：读取请求体超时（毫秒）。
- `workerQueueSize`：业务线程池队列长度。
- `accessLogEnabled`：AccessLog 开关（默认开启）。
