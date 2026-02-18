# Vostok

Vostok 是一个面向 JDK 17+ 的全能框架，当前包含高性能数据访问（Data）、轻量 Web 服务器（Web）、文件能力（File）与日志能力（Log）四大模块，整体保持零依赖（测试依赖除外）。

**重要提醒**
当前项目仅用于实验和技术验证，不建议用于生产环境。

**模块一览**
- Common：通用注解、实体扫描与 JSON 序列化。
- Data：纯 JDBC 的零依赖 ORM/CRUD 组件，内建连接池、事务、SQL 构建与多数据源。
- Web：高性能 Web 服务器，支持中间件、静态资源、TraceId、异步 AccessLog、动态路由与自动 CRUD API。
- File：统一文件门面，默认本地文本文件操作，支持后续扩展 OSS/对象存储实现。
- Log：高性能异步日志，支持统一入口、自动调用类识别、文件滚动分割与输出目录配置。

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

**File 模块**

**核心特性**
- 统一入口：`Vostok.File`
- 生命周期：`init(config)` 初始化，`started()` 查询状态，`close()` 释放资源
- 默认模式：本地文本文件（UTF-8）
- 覆盖常见文件能力：增删改查、追加、按行读写、二进制读写、范围读取、复制、移动、目录创建、递归列举、时间戳与大小查询、哈希、目录冲突策略复制/移动、文件监听
- 支持压缩/解压：可压缩文件或目录，解压支持覆盖策略和 zip bomb 限制
- 可扩展文件模式：通过 `registerStore` 接入 OSS/对象存储
- 统一异常体系：`VKFileException` + `VKFileErrorCode`

**本地文本快速上手**
```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.VKFileConfig;

Vostok.File.init(new VKFileConfig()
    .mode("local")
    .baseDir("/tmp/vostok-files")
    .watchRecursiveDefault(false));

Vostok.File.create("notes/a.txt", "hello");
Vostok.File.append("notes/a.txt", "\nworld");
String text = Vostok.File.read("notes/a.txt");

Vostok.File.writeLines("notes/b.txt", java.util.List.of("L1", "L2"));
java.util.List<String> lines = Vostok.File.readLines("notes/b.txt");

boolean exists = Vostok.File.exists("notes/a.txt");
long size = Vostok.File.size("notes/a.txt");
java.time.Instant modified = Vostok.File.lastModified("notes/a.txt");

Vostok.File.copy("notes/a.txt", "backup/a.txt");
Vostok.File.move("notes/b.txt", "archive/b.txt");
var files = Vostok.File.list("notes", true);

Vostok.File.delete("archive/b.txt");
Vostok.File.close();
```

**压缩 / 解压示例**
```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.VKUnzipOptions;

Vostok.File.init(new VKFileConfig().baseDir("/tmp/vostok-files"));

// 压缩单文件
Vostok.File.zip("notes/a.txt", "zip/a.zip");

// 压缩目录（包含子目录）
Vostok.File.zip("notes", "zip/notes.zip");

// 解压（默认覆盖）
Vostok.File.unzip("zip/notes.zip", "unzip");

// 解压（不覆盖）
Vostok.File.unzip("zip/notes.zip", "unzip", false);

// 解压安全限制（防 zip bomb）
Vostok.File.unzip("zip/notes.zip", "safe-unzip", VKUnzipOptions.builder()
    .replaceExisting(true)
    .maxEntries(1000)
    .maxTotalUncompressedBytes(512L * 1024 * 1024)
    .maxEntryUncompressedBytes(64L * 1024 * 1024)
    .build());
```

**高级接口示例**
```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.VKFileConflictStrategy;
import yueyang.vostok.file.VKFileWatchEventType;
import yueyang.vostok.file.exception.VKFileException;

Vostok.File.init(new VKFileConfig().baseDir("/tmp/vostok-files"));

// hash
String sha256 = Vostok.File.hash("notes/a.txt", "SHA-256");

// 二进制读写 + 范围读取
Vostok.File.writeBytes("data/a.bin", new byte[]{1, 2, 3, 4, 5});
Vostok.File.appendBytes("data/a.bin", new byte[]{6, 7});
byte[] all = Vostok.File.readBytes("data/a.bin");
byte[] part = Vostok.File.readRange("data/a.bin", 2, 3); // 3,4,5

// 大块范围读取（流式输出，避免大 byte[] 占用堆内存）
try (var os = new java.io.FileOutputStream("/tmp/range-part.bin")) {
    long copied = Vostok.File.readRangeTo("data/a.bin", 0, 10_000_000L, os);
    System.out.println("copied=" + copied);
}

// mkdir / isFile / isDirectory / rename
Vostok.File.mkdirs("data");
Vostok.File.mkdir("data/tmp");
boolean file = Vostok.File.isFile("notes/a.txt");
boolean dir = Vostok.File.isDirectory("data/tmp");
Vostok.File.rename("notes/a.txt", "a-renamed.txt");

// walk + filter
var txtFiles = Vostok.File.walk("data", true, info -> !info.directory() && info.path().endsWith(".txt"));

// deleteIfExists / deleteRecursively
Vostok.File.deleteIfExists("data/old.txt");
Vostok.File.deleteRecursively("data/tmp");

// copyDir / moveDir（带冲突策略）
Vostok.File.copyDir("projectA", "backup/projectA", VKFileConflictStrategy.OVERWRITE);
Vostok.File.moveDir("staging", "archive/staging", VKFileConflictStrategy.FAIL);

// watch（目录监听）
try (var handle = Vostok.File.watch("projectA", event -> {
    if (event.type() == VKFileWatchEventType.CREATE) {
        System.out.println("create: " + event.path());
    } else if (event.type() == VKFileWatchEventType.MODIFY) {
        System.out.println("modify: " + event.path());
    } else if (event.type() == VKFileWatchEventType.DELETE) {
        System.out.println("delete: " + event.path());
    } else {
        System.out.println("overflow: " + event.path());
    }
})) {
    Vostok.File.write("projectA/new.txt", "hello");
    Vostok.File.append("projectA/new.txt", "\nworld");
    Vostok.File.deleteIfExists("projectA/new.txt");
}

// watch（递归监听，子目录变化也可收到事件）
try (var recursiveHandle = Vostok.File.watch("projectA", true, event -> {
    System.out.println("recursive: " + event.type() + " -> " + event.path());
})) {
    Vostok.File.mkdirs("projectA/sub");
    Vostok.File.write("projectA/sub/new.txt", "ok");
}

// watch（单文件监听：实际监听父目录并过滤为该文件）
try (var fileHandle = Vostok.File.watch("projectA/config.yml", event -> {
    System.out.println("config changed: " + event.type() + " -> " + event.path());
})) {
    Vostok.File.write("projectA/config.yml", "k: v");
}

// 统一异常处理
try {
    Vostok.File.read("missing.txt");
} catch (VKFileException e) {
    System.out.println(e.getCode() + " " + e.getMessage());
}

boolean started = Vostok.File.started();
VKFileConfig cfg = Vostok.File.config();
Vostok.File.close();
```

**扩展 OSS/对象存储（示例）**
```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.VKFileInfo;
import yueyang.vostok.file.VKFileStore;

import java.time.Instant;
import java.util.List;

public class OssFileStore implements VKFileStore {
    @Override public String mode() { return "oss"; }
    @Override public void create(String path, String content) { throw new UnsupportedOperationException(); }
    @Override public void write(String path, String content) { throw new UnsupportedOperationException(); }
    @Override public void update(String path, String content) { throw new UnsupportedOperationException(); }
    @Override public String read(String path) { throw new UnsupportedOperationException(); }
    @Override public byte[] readBytes(String path) { throw new UnsupportedOperationException(); }
    @Override public byte[] readRange(String path, long offset, int length) { throw new UnsupportedOperationException(); }
    @Override public long readRangeTo(String path, long offset, long length, java.io.OutputStream output) { throw new UnsupportedOperationException(); }
    @Override public void writeBytes(String path, byte[] content) { throw new UnsupportedOperationException(); }
    @Override public void appendBytes(String path, byte[] content) { throw new UnsupportedOperationException(); }
    @Override public String hash(String path, String algorithm) { throw new UnsupportedOperationException(); }
    @Override public boolean delete(String path) { throw new UnsupportedOperationException(); }
    @Override public boolean deleteIfExists(String path) { throw new UnsupportedOperationException(); }
    @Override public boolean deleteRecursively(String path) { throw new UnsupportedOperationException(); }
    @Override public boolean exists(String path) { throw new UnsupportedOperationException(); }
    @Override public boolean isFile(String path) { throw new UnsupportedOperationException(); }
    @Override public boolean isDirectory(String path) { throw new UnsupportedOperationException(); }
    @Override public void append(String path, String content) { throw new UnsupportedOperationException(); }
    @Override public List<String> readLines(String path) { throw new UnsupportedOperationException(); }
    @Override public void writeLines(String path, List<String> lines) { throw new UnsupportedOperationException(); }
    @Override public List<VKFileInfo> list(String path, boolean recursive) { throw new UnsupportedOperationException(); }
    @Override public List<VKFileInfo> walk(String path, boolean recursive, java.util.function.Predicate<VKFileInfo> filter) { throw new UnsupportedOperationException(); }
    @Override public void mkdir(String path) { throw new UnsupportedOperationException(); }
    @Override public void mkdirs(String path) { throw new UnsupportedOperationException(); }
    @Override public void rename(String path, String newName) { throw new UnsupportedOperationException(); }
    @Override public void copy(String sourcePath, String targetPath, boolean replaceExisting) { throw new UnsupportedOperationException(); }
    @Override public void move(String sourcePath, String targetPath, boolean replaceExisting) { throw new UnsupportedOperationException(); }
    @Override public void copyDir(String sourceDir, String targetDir, yueyang.vostok.file.VKFileConflictStrategy strategy) { throw new UnsupportedOperationException(); }
    @Override public void moveDir(String sourceDir, String targetDir, yueyang.vostok.file.VKFileConflictStrategy strategy) { throw new UnsupportedOperationException(); }
    @Override public void touch(String path) { throw new UnsupportedOperationException(); }
    @Override public long size(String path) { throw new UnsupportedOperationException(); }
    @Override public Instant lastModified(String path) { throw new UnsupportedOperationException(); }
    @Override public void zip(String sourcePath, String zipPath) { throw new UnsupportedOperationException(); }
    @Override public void unzip(String zipPath, String targetDir, boolean replaceExisting) { throw new UnsupportedOperationException(); }
    @Override public void unzip(String zipPath, String targetDir, yueyang.vostok.file.VKUnzipOptions options) { throw new UnsupportedOperationException(); }
    @Override public yueyang.vostok.file.VKFileWatchHandle watch(String path, yueyang.vostok.file.VKFileWatchListener listener) { throw new UnsupportedOperationException(); }
    @Override public yueyang.vostok.file.VKFileWatchHandle watch(String path, boolean recursive, yueyang.vostok.file.VKFileWatchListener listener) { throw new UnsupportedOperationException(); }
}

Vostok.File.registerStore("oss", new OssFileStore());
Vostok.File.withMode("oss", () -> {
    // 在 OSS 模式下执行文件操作
    // Vostok.File.write("bucket/path/a.txt", "content");
});
```

**Log 模块**

**核心特性**
- 统一入口：`Vostok.Log`
- 异步写日志：业务线程只入队，后台线程批量落盘
- 队列满策略：`DROP` / `BLOCK` / `SYNC_FALLBACK`
- 自动调用类识别：无需手动传 logger/class
- 日志滚动分割：按时间周期（小时/天）和文件大小双触发
- 文件不可写自动降级：自动回退 `stderr` 并周期重试文件恢复
- 输出配置可调：目录、文件前缀、单文件大小、备份数量/天数/总大小、压缩

**快速上手**
```java
import yueyang.vostok.Vostok;
import yueyang.vostok.log.VKLogConfig;
import yueyang.vostok.log.VKLogLevel;

Vostok.Log.init(new VKLogConfig()
    .level(VKLogLevel.INFO)
    .outputDir("/tmp/vostok-log")
    .filePrefix("app"));

Vostok.Log.info("service started");
Vostok.Log.warn("cache miss: key={}", "user:1001");

try {
    throw new RuntimeException("boom");
} catch (Exception e) {
    Vostok.Log.error("request failed", e);
}
```

**日志配置**
```java
import yueyang.vostok.Vostok;
import yueyang.vostok.log.VKLogConfig;
import yueyang.vostok.log.VKLogLevel;
import yueyang.vostok.log.VKLogQueueFullPolicy;
import yueyang.vostok.log.VKLogFsyncPolicy;
import yueyang.vostok.log.VKLogRollInterval;

VKLogConfig cfg = new VKLogConfig()
    .level(VKLogLevel.INFO)
    .outputDir("/tmp/vostok-log")
    .filePrefix("app")
    .maxFileSizeMb(128)
    .maxBackups(30)
    .maxBackupDays(7)
    .maxTotalSizeMb(2048)
    .consoleEnabled(true)
    .rollInterval(VKLogRollInterval.DAILY)
    .compressRolledFiles(true)
    .queueFullPolicy(VKLogQueueFullPolicy.BLOCK)
    .queueCapacity(65536)
    .flushIntervalMs(1000)
    .flushBatchSize(512)
    .fsyncPolicy(VKLogFsyncPolicy.EVERY_FLUSH)
    .shutdownTimeoutMs(8000)
    .fileRetryIntervalMs(3000);

Vostok.Log.init(cfg);
```

**生命周期**
```java
Vostok.Log.init();          // 使用默认配置初始化（幂等）
boolean ok = Vostok.Log.initialized();
Vostok.Log.reinit(cfg);     // 运行期替换配置
Vostok.Log.close();         // 优雅关闭 Log 模块
```

**滚动与文件命名**
- 当前写入文件：`<filePrefix>.log`（如 `app.log`）
- 滚动后文件：`<filePrefix>-yyyyMMdd-HHmmss-<seq>.log`
- 触发条件：
  - 时间周期变化（按 `setRollInterval`）
  - 当前文件大小超过 `setMaxFileSizeMb` 阈值
- 启用压缩后滚动文件会变为：`<filePrefix>-yyyyMMdd-HHmmss-<seq>.log.gz`

**队列满策略说明**
- `DROP`：丢弃当前日志并累计到 `droppedLogs()`
- `BLOCK`：阻塞调用线程等待入队，尽量不丢日志
- `SYNC_FALLBACK`：当前线程同步写日志（不经异步队列）

**刷新与关闭（可选）**
```java
Vostok.Log.flush();    // 强制刷新队列到磁盘
Vostok.Log.shutdown(); // 主动关闭日志线程（通常用于进程退出前）
```

**说明**
- Data/Web/File 内部日志也统一通过 `Vostok.Log` 输出。
- 文件系统不可写时会自动降级到 `stderr`，并按 `setFileRetryIntervalMs` 自动重试恢复。
- 可通过以下指标观察日志健康状态：
  - `Vostok.Log.droppedLogs()`
  - `Vostok.Log.fallbackWrites()`
  - `Vostok.Log.fileWriteErrors()`

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
- `autoCreateTable`：初始化/refreshMeta 时自动创建缺失表（默认关闭）
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
    .get("/user/{id}", (req, res) -> res.text(req.param("id")))
    .get("/assets/{*path}", (req, res) -> res.text(req.param("path")))
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
静态资源默认返回 `ETag`，当请求携带 `If-None-Match` 且命中时返回 `304 Not Modified`。

**TraceId**
- 默认生成 `X-Trace-Id` 返回头。
- 如果请求中携带 `X-Trace-Id`，将原样透传。

**AccessLog**
- 默认开启，可通过 `VKWebConfig.accessLogEnabled(false)` 关闭。
- AccessLog 使用独立线程异步写入，默认有界队列，避免阻塞业务线程。
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
- `accessLogQueueSize`：AccessLog 异步队列长度，队列满时会丢弃并输出告警。
