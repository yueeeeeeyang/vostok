# 1. 项目名称 + 项目介绍

## Vostok

Vostok 是一个面向 `JDK 17+` 的轻量 Java 框架，提供统一门面 `Vostok`，聚合四个模块能力：

- `Vostok.Data`：基于 JDBC 的数据访问（CRUD、事务、查询、多数据源、连接池）
- `Vostok.Web`：基于 NIO Reactor 的 Web 服务器（路由、中间件、静态资源、自动 CRUD API）
- `Vostok.File`：统一文件访问（本地文本存储默认实现，可扩展 Store）
- `Vostok.Log`：异步日志（滚动、队列策略、降级、指标）

项目构建方式为 Maven：`/Users/yueyang/Develop/code/codex/Vostok/pom.xml`。

---

# 2. 注意事项

- 当前项目定位为实验与技术验证，不建议直接用于生产环境。
- 运行环境为 `JDK 17+`。
- 数据模块为纯 JDBC 模式，生产环境需自行提供对应数据库驱动。
- `Vostok.Data` 在调用前必须先 `init(...)`。
- `Vostok.Web` 在调用 `start()` 前必须先 `init(...)` 并注册路由。
- `Vostok.File` 在文件操作前必须先 `init(...)`。
- `Vostok.Log` 可显式 `init(...)`，也支持首次写日志时懒加载。

---

# 3. Vostok 统一入口接口列表（代码接口形式）

> 入口类：`/Users/yueyang/Develop/code/codex/Vostok/src/main/java/yueyang/vostok/Vostok.java`
>
> 下面所有接口均通过 `Vostok.Data / Vostok.Web / Vostok.File / Vostok.Log` 调用。

## 3.1 Data 接口定义

```java
public interface Vostok.Data {
    /**
     * 方法描述：`Vostok.Data.init` 接口。
     * 参数说明：
     * @param config 参数 `config`。
     * @param basePackages 参数 `basePackages`。
     * 返回值说明：
     * 无
     */
    public static void init(VKDataConfig config, String... basePackages);

    /**
     * 方法描述：`Vostok.Data.registerDataSource` 接口。
     * 参数说明：
     * @param name 参数 `name`。
     * @param config 参数 `config`。
     * 返回值说明：
     * 无
     */
    public static void registerDataSource(String name, VKDataConfig config);

    /**
     * 方法描述：`Vostok.Data.refreshMeta` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void refreshMeta();

    /**
     * 方法描述：`Vostok.Data.refreshMeta` 接口。
     * 参数说明：
     * @param basePackages 参数 `basePackages`。
     * 返回值说明：
     * 无
     */
    public static void refreshMeta(String... basePackages);

    /**
     * 方法描述：`Vostok.Data.setScanner` 接口。
     * 参数说明：
     * @param scanner 参数 `scanner`。
     * 返回值说明：
     * 无
     */
    public static void setScanner(VKScanner.EntityScanner scanner);

    /**
     * 方法描述：`Vostok.Data.withDataSource` 接口。
     * 参数说明：
     * @param name 参数 `name`。
     * @param action 参数 `action`。
     * 返回值说明：
     * 无
     */
    public static void withDataSource(String name, Runnable action);

    /**
     * 方法描述：`Vostok.Data.withDataSource` 接口。
     * 参数说明：
     * @param name 参数 `name`。
     * @param supplier 参数 `supplier`。
     * 返回值说明：
     * @return 返回 `T` 类型结果。
     */
    public static <T> T withDataSource(String name, Supplier<T> supplier);

    /**
     * 方法描述：`Vostok.Data.captureContext` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `VostokContext` 类型结果。
     */
    public static VostokContext captureContext();

    /**
     * 方法描述：`Vostok.Data.wrap` 接口。
     * 参数说明：
     * @param action 参数 `action`。
     * 返回值说明：
     * @return 返回 `Runnable` 类型结果。
     */
    public static Runnable wrap(Runnable action);

    /**
     * 方法描述：`Vostok.Data.wrap` 接口。
     * 参数说明：
     * @param supplier 参数 `supplier`。
     * 返回值说明：
     * @return 返回 `Supplier<T>` 类型结果。
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier);

    /**
     * 方法描述：`Vostok.Data.wrap` 接口。
     * 参数说明：
     * @param context 参数 `context`。
     * @param action 参数 `action`。
     * 返回值说明：
     * @return 返回 `Runnable` 类型结果。
     */
    public static Runnable wrap(VostokContext context, Runnable action);

    /**
     * 方法描述：`Vostok.Data.wrap` 接口。
     * 参数说明：
     * @param context 参数 `context`。
     * @param supplier 参数 `supplier`。
     * 返回值说明：
     * @return 返回 `Supplier<T>` 类型结果。
     */
    public static <T> Supplier<T> wrap(VostokContext context, Supplier<T> supplier);

    /**
     * 方法描述：`Vostok.Data.registerInterceptor` 接口。
     * 参数说明：
     * @param interceptor 参数 `interceptor`。
     * 返回值说明：
     * 无
     */
    public static void registerInterceptor(VKInterceptor interceptor);

    /**
     * 方法描述：`Vostok.Data.registerRawSql` 接口。
     * 参数说明：
     * @param sqls 参数 `sqls`。
     * 返回值说明：
     * 无
     */
    public static void registerRawSql(String... sqls);

    /**
     * 方法描述：`Vostok.Data.registerRawSql` 接口。
     * 参数说明：
     * @param dataSourceName 参数 `dataSourceName`。
     * @param sqls 参数 `sqls`。
     * 返回值说明：
     * 无
     */
    public static void registerRawSql(String dataSourceName, String[] sqls);

    /**
     * 方法描述：`Vostok.Data.registerSubquery` 接口。
     * 参数说明：
     * @param sqls 参数 `sqls`。
     * 返回值说明：
     * 无
     */
    public static void registerSubquery(String... sqls);

    /**
     * 方法描述：`Vostok.Data.registerSubquery` 接口。
     * 参数说明：
     * @param dataSourceName 参数 `dataSourceName`。
     * @param sqls 参数 `sqls`。
     * 返回值说明：
     * 无
     */
    public static void registerSubquery(String dataSourceName, String[] sqls);

    /**
     * 方法描述：`Vostok.Data.clearInterceptors` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void clearInterceptors();

    /**
     * 方法描述：`Vostok.Data.close` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void close();

    /**
     * 方法描述：`Vostok.Data.tx` 接口。
     * 参数说明：
     * @param action 参数 `action`。
     * 返回值说明：
     * 无
     */
    public static void tx(Runnable action);

    /**
     * 方法描述：`Vostok.Data.tx` 接口。
     * 参数说明：
     * @param action 参数 `action`。
     * @param propagation 参数 `propagation`。
     * @param isolation 参数 `isolation`。
     * 返回值说明：
     * 无
     */
    public static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation);

    /**
     * 方法描述：`Vostok.Data.tx` 接口。
     * 参数说明：
     * @param action 参数 `action`。
     * @param propagation 参数 `propagation`。
     * @param isolation 参数 `isolation`。
     * @param readOnly 参数 `readOnly`。
     * 返回值说明：
     * 无
     */
    public static void tx(Runnable action, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly);

    /**
     * 方法描述：`Vostok.Data.tx` 接口。
     * 参数说明：
     * @param supplier 参数 `supplier`。
     * 返回值说明：
     * @return 返回 `T` 类型结果。
     */
    public static <T> T tx(Supplier<T> supplier);

    /**
     * 方法描述：`Vostok.Data.tx` 接口。
     * 参数说明：
     * @param supplier 参数 `supplier`。
     * @param propagation 参数 `propagation`。
     * @param isolation 参数 `isolation`。
     * 返回值说明：
     * @return 返回 `T` 类型结果。
     */
    public static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation);

    /**
     * 方法描述：`Vostok.Data.tx` 接口。
     * 参数说明：
     * @param supplier 参数 `supplier`。
     * @param propagation 参数 `propagation`。
     * @param isolation 参数 `isolation`。
     * @param readOnly 参数 `readOnly`。
     * 返回值说明：
     * @return 返回 `T` 类型结果。
     */
    public static <T> T tx(Supplier<T> supplier, VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly);

    /**
     * 方法描述：`Vostok.Data.beginTx` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void beginTx();

    /**
     * 方法描述：`Vostok.Data.beginTx` 接口。
     * 参数说明：
     * @param propagation 参数 `propagation`。
     * @param isolation 参数 `isolation`。
     * 返回值说明：
     * 无
     */
    public static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation);

    /**
     * 方法描述：`Vostok.Data.beginTx` 接口。
     * 参数说明：
     * @param propagation 参数 `propagation`。
     * @param isolation 参数 `isolation`。
     * @param readOnly 参数 `readOnly`。
     * 返回值说明：
     * 无
     */
    public static void beginTx(VKTxPropagation propagation, VKTxIsolation isolation, boolean readOnly);

    /**
     * 方法描述：`Vostok.Data.commitTx` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void commitTx();

    /**
     * 方法描述：`Vostok.Data.rollbackTx` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void rollbackTx();

    /**
     * 方法描述：`Vostok.Data.insert` 接口。
     * 参数说明：
     * @param entity 参数 `entity`。
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int insert(Object entity);

    /**
     * 方法描述：`Vostok.Data.batchInsert` 接口。
     * 参数说明：
     * @param entities 参数 `entities`。
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int batchInsert(List<?> entities);

    /**
     * 方法描述：`Vostok.Data.batchInsertDetail` 接口。
     * 参数说明：
     * @param entities 参数 `entities`。
     * 返回值说明：
     * @return 返回 `VKBatchDetailResult` 类型结果。
     */
    public static VKBatchDetailResult batchInsertDetail(List<?> entities);

    /**
     * 方法描述：`Vostok.Data.update` 接口。
     * 参数说明：
     * @param entity 参数 `entity`。
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int update(Object entity);

    /**
     * 方法描述：`Vostok.Data.batchUpdate` 接口。
     * 参数说明：
     * @param entities 参数 `entities`。
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int batchUpdate(List<?> entities);

    /**
     * 方法描述：`Vostok.Data.batchUpdateDetail` 接口。
     * 参数说明：
     * @param entities 参数 `entities`。
     * 返回值说明：
     * @return 返回 `VKBatchDetailResult` 类型结果。
     */
    public static VKBatchDetailResult batchUpdateDetail(List<?> entities);

    /**
     * 方法描述：`Vostok.Data.delete` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param idValue 参数 `idValue`。
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int delete(Class<?> entityClass, Object idValue);

    /**
     * 方法描述：`Vostok.Data.batchDelete` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param idValues 参数 `idValues`。
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int batchDelete(Class<?> entityClass, List<?> idValues);

    /**
     * 方法描述：`Vostok.Data.batchDeleteDetail` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param idValues 参数 `idValues`。
     * 返回值说明：
     * @return 返回 `VKBatchDetailResult` 类型结果。
     */
    public static VKBatchDetailResult batchDeleteDetail(Class<?> entityClass, List<?> idValues);

    /**
     * 方法描述：`Vostok.Data.findById` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param idValue 参数 `idValue`。
     * 返回值说明：
     * @return 返回 `T` 类型结果。
     */
    public static <T> T findById(Class<T> entityClass, Object idValue);

    /**
     * 方法描述：`Vostok.Data.findAll` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * 返回值说明：
     * @return 返回 `List<T>` 类型结果。
     */
    public static <T> List<T> findAll(Class<T> entityClass);

    /**
     * 方法描述：`Vostok.Data.query` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param query 参数 `query`。
     * 返回值说明：
     * @return 返回 `List<T>` 类型结果。
     */
    public static <T> List<T> query(Class<T> entityClass, VKQuery query);

    /**
     * 方法描述：`Vostok.Data.queryColumns` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param query 参数 `query`。
     * @param fields 参数 `fields`。
     * 返回值说明：
     * @return 返回 `List<T>` 类型结果。
     */
    public static <T> List<T> queryColumns(Class<T> entityClass, VKQuery query, String... fields);

    /**
     * 方法描述：`Vostok.Data.aggregate` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param query 参数 `query`。
     * @param aggregates 参数 `aggregates`。
     * 返回值说明：
     * @return 返回 `List<Object[]>` 类型结果。
     */
    public static List<Object[]> aggregate(Class<?> entityClass, VKQuery query, VKAggregate... aggregates);

    /**
     * 方法描述：`Vostok.Data.count` 接口。
     * 参数说明：
     * @param entityClass 参数 `entityClass`。
     * @param query 参数 `query`。
     * 返回值说明：
     * @return 返回 `long` 类型结果。
     */
    public static long count(Class<?> entityClass, VKQuery query);

    /**
     * 方法描述：`Vostok.Data.poolMetrics` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `List<VKPoolMetrics>` 类型结果。
     */
    public static List<VKPoolMetrics> poolMetrics();

    /**
     * 方法描述：`Vostok.Data.report` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `String` 类型结果。
     */
    public static String report();

}
```

## 3.2 Web 接口定义

```java
public interface Vostok.Web {
    /**
     * 方法描述：`Vostok.Web.init` 接口。
     * 参数说明：
     * @param port 参数 `port`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public static VostokWeb init(int port);

    /**
     * 方法描述：`Vostok.Web.init` 接口。
     * 参数说明：
     * @param config 参数 `config`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public static VostokWeb init(VKWebConfig config);

    /**
     * 方法描述：`Vostok.Web.start` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void start();

    /**
     * 方法描述：`Vostok.Web.stop` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void stop();

    /**
     * 方法描述：`Vostok.Web.started` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean started();

    /**
     * 方法描述：`Vostok.Web.port` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `int` 类型结果。
     */
    public static int port();

    /**
     * 方法描述：`Vostok.Web.get` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param handler 参数 `handler`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb get(String path, VKHandler handler);

    /**
     * 方法描述：`Vostok.Web.post` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param handler 参数 `handler`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb post(String path, VKHandler handler);

    /**
     * 方法描述：`Vostok.Web.route` 接口。
     * 参数说明：
     * @param method 参数 `method`。
     * @param path 参数 `path`。
     * @param handler 参数 `handler`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb route(String method, String path, VKHandler handler);

    /**
     * 方法描述：`Vostok.Web.autoCrudApi` 接口。
     * 参数说明：
     * @param basePackages 参数 `basePackages`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb autoCrudApi(String... basePackages);

    /**
     * 方法描述：`Vostok.Web.autoCrudApi` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb autoCrudApi();

    /**
     * 方法描述：`Vostok.Web.autoCrudApi` 接口。
     * 参数说明：
     * @param style 参数 `style`。
     * @param basePackages 参数 `basePackages`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb autoCrudApi(VKCrudStyle style, String... basePackages);

    /**
     * 方法描述：`Vostok.Web.use` 接口。
     * 参数说明：
     * @param middleware 参数 `middleware`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb use(VKMiddleware middleware);

    /**
     * 方法描述：`Vostok.Web.staticDir` 接口。
     * 参数说明：
     * @param urlPrefix 参数 `urlPrefix`。
     * @param directory 参数 `directory`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb staticDir(String urlPrefix, String directory);

    /**
     * 方法描述：`Vostok.Web.error` 接口。
     * 参数说明：
     * @param handler 参数 `handler`。
     * 返回值说明：
     * @return 返回 `VostokWeb` 类型结果。
     */
    public VostokWeb error(VKErrorHandler handler);

}
```

## 3.3 File 接口定义

```java
public interface Vostok.File {
    /**
     * 方法描述：`Vostok.File.init` 接口。
     * 参数说明：
     * @param fileConfig 参数 `fileConfig`。
     * 返回值说明：
     * 无
     */
    public static void init(VKFileConfig fileConfig);

    /**
     * 方法描述：`Vostok.File.started` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean started();

    /**
     * 方法描述：`Vostok.File.config` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `VKFileConfig` 类型结果。
     */
    public static VKFileConfig config();

    /**
     * 方法描述：`Vostok.File.close` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void close();

    /**
     * 方法描述：`Vostok.File.registerStore` 接口。
     * 参数说明：
     * @param mode 参数 `mode`。
     * @param store 参数 `store`。
     * 返回值说明：
     * 无
     */
    public static void registerStore(String mode, VKFileStore store);

    /**
     * 方法描述：`Vostok.File.setDefaultMode` 接口。
     * 参数说明：
     * @param mode 参数 `mode`。
     * 返回值说明：
     * 无
     */
    public static void setDefaultMode(String mode);

    /**
     * 方法描述：`Vostok.File.defaultMode` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `String` 类型结果。
     */
    public static String defaultMode();

    /**
     * 方法描述：`Vostok.File.modes` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `Set<String>` 类型结果。
     */
    public static Set<String> modes();

    /**
     * 方法描述：`Vostok.File.withMode` 接口。
     * 参数说明：
     * @param mode 参数 `mode`。
     * @param action 参数 `action`。
     * 返回值说明：
     * 无
     */
    public static void withMode(String mode, Runnable action);

    /**
     * 方法描述：`Vostok.File.withMode` 接口。
     * 参数说明：
     * @param mode 参数 `mode`。
     * @param supplier 参数 `supplier`。
     * 返回值说明：
     * @return 返回 `T` 类型结果。
     */
    public static <T> T withMode(String mode, Supplier<T> supplier);

    /**
     * 方法描述：`Vostok.File.currentMode` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `String` 类型结果。
     */
    public static String currentMode();

    /**
     * 方法描述：`Vostok.File.create` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param content 参数 `content`。
     * 返回值说明：
     * 无
     */
    public static void create(String path, String content);

    /**
     * 方法描述：`Vostok.File.write` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param content 参数 `content`。
     * 返回值说明：
     * 无
     */
    public static void write(String path, String content);

    /**
     * 方法描述：`Vostok.File.update` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param content 参数 `content`。
     * 返回值说明：
     * 无
     */
    public static void update(String path, String content);

    /**
     * 方法描述：`Vostok.File.read` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `String` 类型结果。
     */
    public static String read(String path);

    /**
     * 方法描述：`Vostok.File.readBytes` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `byte[]` 类型结果。
     */
    public static byte[] readBytes(String path);

    /**
     * 方法描述：`Vostok.File.readRange` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param offset 参数 `offset`。
     * @param length 参数 `length`。
     * 返回值说明：
     * @return 返回 `byte[]` 类型结果。
     */
    public static byte[] readRange(String path, long offset, int length);

    /**
     * 方法描述：`Vostok.File.readRangeTo` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param offset 参数 `offset`。
     * @param length 参数 `length`。
     * @param output 参数 `output`。
     * 返回值说明：
     * @return 返回 `long` 类型结果。
     */
    public static long readRangeTo(String path, long offset, long length, OutputStream output);

    /**
     * 方法描述：`Vostok.File.writeBytes` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param content 参数 `content`。
     * 返回值说明：
     * 无
     */
    public static void writeBytes(String path, byte[] content);

    /**
     * 方法描述：`Vostok.File.appendBytes` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param content 参数 `content`。
     * 返回值说明：
     * 无
     */
    public static void appendBytes(String path, byte[] content);

    /**
     * 方法描述：`Vostok.File.hash` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param algorithm 参数 `algorithm`。
     * 返回值说明：
     * @return 返回 `String` 类型结果。
     */
    public static String hash(String path, String algorithm);

    /**
     * 方法描述：`Vostok.File.delete` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean delete(String path);

    /**
     * 方法描述：`Vostok.File.deleteIfExists` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean deleteIfExists(String path);

    /**
     * 方法描述：`Vostok.File.deleteRecursively` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean deleteRecursively(String path);

    /**
     * 方法描述：`Vostok.File.exists` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean exists(String path);

    /**
     * 方法描述：`Vostok.File.isFile` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean isFile(String path);

    /**
     * 方法描述：`Vostok.File.isDirectory` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean isDirectory(String path);

    /**
     * 方法描述：`Vostok.File.append` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param content 参数 `content`。
     * 返回值说明：
     * 无
     */
    public static void append(String path, String content);

    /**
     * 方法描述：`Vostok.File.readLines` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `List<String>` 类型结果。
     */
    public static List<String> readLines(String path);

    /**
     * 方法描述：`Vostok.File.writeLines` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param lines 参数 `lines`。
     * 返回值说明：
     * 无
     */
    public static void writeLines(String path, List<String> lines);

    /**
     * 方法描述：`Vostok.File.list` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `List<VKFileInfo>` 类型结果。
     */
    public static List<VKFileInfo> list(String path);

    /**
     * 方法描述：`Vostok.File.list` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param recursive 参数 `recursive`。
     * 返回值说明：
     * @return 返回 `List<VKFileInfo>` 类型结果。
     */
    public static List<VKFileInfo> list(String path, boolean recursive);

    /**
     * 方法描述：`Vostok.File.walk` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param recursive 参数 `recursive`。
     * @param filter 参数 `filter`。
     * 返回值说明：
     * @return 返回 `List<VKFileInfo>` 类型结果。
     */
    public static List<VKFileInfo> walk(String path, boolean recursive, Predicate<VKFileInfo> filter);

    /**
     * 方法描述：`Vostok.File.walk` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param recursive 参数 `recursive`。
     * 返回值说明：
     * @return 返回 `List<VKFileInfo>` 类型结果。
     */
    public static List<VKFileInfo> walk(String path, boolean recursive);

    /**
     * 方法描述：`Vostok.File.mkdir` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * 无
     */
    public static void mkdir(String path);

    /**
     * 方法描述：`Vostok.File.mkdirs` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * 无
     */
    public static void mkdirs(String path);

    /**
     * 方法描述：`Vostok.File.rename` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param newName 参数 `newName`。
     * 返回值说明：
     * 无
     */
    public static void rename(String path, String newName);

    /**
     * 方法描述：`Vostok.File.copy` 接口。
     * 参数说明：
     * @param sourcePath 参数 `sourcePath`。
     * @param targetPath 参数 `targetPath`。
     * 返回值说明：
     * 无
     */
    public static void copy(String sourcePath, String targetPath);

    /**
     * 方法描述：`Vostok.File.copy` 接口。
     * 参数说明：
     * @param sourcePath 参数 `sourcePath`。
     * @param targetPath 参数 `targetPath`。
     * @param replaceExisting 参数 `replaceExisting`。
     * 返回值说明：
     * 无
     */
    public static void copy(String sourcePath, String targetPath, boolean replaceExisting);

    /**
     * 方法描述：`Vostok.File.move` 接口。
     * 参数说明：
     * @param sourcePath 参数 `sourcePath`。
     * @param targetPath 参数 `targetPath`。
     * 返回值说明：
     * 无
     */
    public static void move(String sourcePath, String targetPath);

    /**
     * 方法描述：`Vostok.File.move` 接口。
     * 参数说明：
     * @param sourcePath 参数 `sourcePath`。
     * @param targetPath 参数 `targetPath`。
     * @param replaceExisting 参数 `replaceExisting`。
     * 返回值说明：
     * 无
     */
    public static void move(String sourcePath, String targetPath, boolean replaceExisting);

    /**
     * 方法描述：`Vostok.File.copyDir` 接口。
     * 参数说明：
     * @param sourceDir 参数 `sourceDir`。
     * @param targetDir 参数 `targetDir`。
     * @param strategy 参数 `strategy`。
     * 返回值说明：
     * 无
     */
    public static void copyDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy);

    /**
     * 方法描述：`Vostok.File.moveDir` 接口。
     * 参数说明：
     * @param sourceDir 参数 `sourceDir`。
     * @param targetDir 参数 `targetDir`。
     * @param strategy 参数 `strategy`。
     * 返回值说明：
     * 无
     */
    public static void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy);

    /**
     * 方法描述：`Vostok.File.touch` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * 无
     */
    public static void touch(String path);

    /**
     * 方法描述：`Vostok.File.size` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `long` 类型结果。
     */
    public static long size(String path);

    /**
     * 方法描述：`Vostok.File.lastModified` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * 返回值说明：
     * @return 返回 `Instant` 类型结果。
     */
    public static Instant lastModified(String path);

    /**
     * 方法描述：`Vostok.File.zip` 接口。
     * 参数说明：
     * @param sourcePath 参数 `sourcePath`。
     * @param zipPath 参数 `zipPath`。
     * 返回值说明：
     * 无
     */
    public static void zip(String sourcePath, String zipPath);

    /**
     * 方法描述：`Vostok.File.unzip` 接口。
     * 参数说明：
     * @param zipPath 参数 `zipPath`。
     * @param targetDir 参数 `targetDir`。
     * 返回值说明：
     * 无
     */
    public static void unzip(String zipPath, String targetDir);

    /**
     * 方法描述：`Vostok.File.unzip` 接口。
     * 参数说明：
     * @param zipPath 参数 `zipPath`。
     * @param targetDir 参数 `targetDir`。
     * @param replaceExisting 参数 `replaceExisting`。
     * 返回值说明：
     * 无
     */
    public static void unzip(String zipPath, String targetDir, boolean replaceExisting);

    /**
     * 方法描述：`Vostok.File.unzip` 接口。
     * 参数说明：
     * @param zipPath 参数 `zipPath`。
     * @param targetDir 参数 `targetDir`。
     * @param options 参数 `options`。
     * 返回值说明：
     * 无
     */
    public static void unzip(String zipPath, String targetDir, VKUnzipOptions options);

    /**
     * 方法描述：`Vostok.File.watch` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param listener 参数 `listener`。
     * 返回值说明：
     * @return 返回 `VKFileWatchHandle` 类型结果。
     */
    public static VKFileWatchHandle watch(String path, VKFileWatchListener listener);

    /**
     * 方法描述：`Vostok.File.watch` 接口。
     * 参数说明：
     * @param path 参数 `path`。
     * @param recursive 参数 `recursive`。
     * @param listener 参数 `listener`。
     * 返回值说明：
     * @return 返回 `VKFileWatchHandle` 类型结果。
     */
    public static VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener);

}
```

## 3.4 Log 接口定义

```java
public interface Vostok.Log {
    /**
     * 方法描述：`Vostok.Log.init` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void init();

    /**
     * 方法描述：`Vostok.Log.init` 接口。
     * 参数说明：
     * @param config 参数 `config`。
     * 返回值说明：
     * 无
     */
    public static void init(VKLogConfig config);

    /**
     * 方法描述：`Vostok.Log.reinit` 接口。
     * 参数说明：
     * @param config 参数 `config`。
     * 返回值说明：
     * 无
     */
    public static void reinit(VKLogConfig config);

    /**
     * 方法描述：`Vostok.Log.close` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void close();

    /**
     * 方法描述：`Vostok.Log.initialized` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `boolean` 类型结果。
     */
    public static boolean initialized();

    /**
     * 方法描述：`Vostok.Log.trace` 接口。
     * 参数说明：
     * @param msg 参数 `msg`。
     * 返回值说明：
     * 无
     */
    public static void trace(String msg);

    /**
     * 方法描述：`Vostok.Log.debug` 接口。
     * 参数说明：
     * @param msg 参数 `msg`。
     * 返回值说明：
     * 无
     */
    public static void debug(String msg);

    /**
     * 方法描述：`Vostok.Log.info` 接口。
     * 参数说明：
     * @param msg 参数 `msg`。
     * 返回值说明：
     * 无
     */
    public static void info(String msg);

    /**
     * 方法描述：`Vostok.Log.warn` 接口。
     * 参数说明：
     * @param msg 参数 `msg`。
     * 返回值说明：
     * 无
     */
    public static void warn(String msg);

    /**
     * 方法描述：`Vostok.Log.error` 接口。
     * 参数说明：
     * @param msg 参数 `msg`。
     * 返回值说明：
     * 无
     */
    public static void error(String msg);

    /**
     * 方法描述：`Vostok.Log.error` 接口。
     * 参数说明：
     * @param msg 参数 `msg`。
     * @param t 参数 `t`。
     * 返回值说明：
     * 无
     */
    public static void error(String msg, Throwable t);

    /**
     * 方法描述：`Vostok.Log.trace` 接口。
     * 参数说明：
     * @param template 参数 `template`。
     * @param args 参数 `args`。
     * 返回值说明：
     * 无
     */
    public static void trace(String template, Object... args);

    /**
     * 方法描述：`Vostok.Log.debug` 接口。
     * 参数说明：
     * @param template 参数 `template`。
     * @param args 参数 `args`。
     * 返回值说明：
     * 无
     */
    public static void debug(String template, Object... args);

    /**
     * 方法描述：`Vostok.Log.info` 接口。
     * 参数说明：
     * @param template 参数 `template`。
     * @param args 参数 `args`。
     * 返回值说明：
     * 无
     */
    public static void info(String template, Object... args);

    /**
     * 方法描述：`Vostok.Log.warn` 接口。
     * 参数说明：
     * @param template 参数 `template`。
     * @param args 参数 `args`。
     * 返回值说明：
     * 无
     */
    public static void warn(String template, Object... args);

    /**
     * 方法描述：`Vostok.Log.error` 接口。
     * 参数说明：
     * @param template 参数 `template`。
     * @param args 参数 `args`。
     * 返回值说明：
     * 无
     */
    public static void error(String template, Object... args);

    /**
     * 方法描述：`Vostok.Log.setLevel` 接口。
     * 参数说明：
     * @param level 参数 `level`。
     * 返回值说明：
     * 无
     */
    public static void setLevel(VKLogLevel level);

    /**
     * 方法描述：`Vostok.Log.level` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `VKLogLevel` 类型结果。
     */
    public static VKLogLevel level();

    /**
     * 方法描述：`Vostok.Log.setOutputDir` 接口。
     * 参数说明：
     * @param outputDir 参数 `outputDir`。
     * 返回值说明：
     * 无
     */
    public static void setOutputDir(String outputDir);

    /**
     * 方法描述：`Vostok.Log.setFilePrefix` 接口。
     * 参数说明：
     * @param filePrefix 参数 `filePrefix`。
     * 返回值说明：
     * 无
     */
    public static void setFilePrefix(String filePrefix);

    /**
     * 方法描述：`Vostok.Log.setMaxFileSizeMb` 接口。
     * 参数说明：
     * @param mb 参数 `mb`。
     * 返回值说明：
     * 无
     */
    public static void setMaxFileSizeMb(long mb);

    /**
     * 方法描述：`Vostok.Log.setMaxFileSizeBytes` 接口。
     * 参数说明：
     * @param bytes 参数 `bytes`。
     * 返回值说明：
     * 无
     */
    public static void setMaxFileSizeBytes(long bytes);

    /**
     * 方法描述：`Vostok.Log.setMaxBackups` 接口。
     * 参数说明：
     * @param maxBackups 参数 `maxBackups`。
     * 返回值说明：
     * 无
     */
    public static void setMaxBackups(int maxBackups);

    /**
     * 方法描述：`Vostok.Log.setMaxBackupDays` 接口。
     * 参数说明：
     * @param maxBackupDays 参数 `maxBackupDays`。
     * 返回值说明：
     * 无
     */
    public static void setMaxBackupDays(int maxBackupDays);

    /**
     * 方法描述：`Vostok.Log.setMaxTotalSizeMb` 接口。
     * 参数说明：
     * @param mb 参数 `mb`。
     * 返回值说明：
     * 无
     */
    public static void setMaxTotalSizeMb(long mb);

    /**
     * 方法描述：`Vostok.Log.setConsoleEnabled` 接口。
     * 参数说明：
     * @param enabled 参数 `enabled`。
     * 返回值说明：
     * 无
     */
    public static void setConsoleEnabled(boolean enabled);

    /**
     * 方法描述：`Vostok.Log.setQueueFullPolicy` 接口。
     * 参数说明：
     * @param policy 参数 `policy`。
     * 返回值说明：
     * 无
     */
    public static void setQueueFullPolicy(VKLogQueueFullPolicy policy);

    /**
     * 方法描述：`Vostok.Log.setQueueCapacity` 接口。
     * 参数说明：
     * @param capacity 参数 `capacity`。
     * 返回值说明：
     * 无
     */
    public static void setQueueCapacity(int capacity);

    /**
     * 方法描述：`Vostok.Log.setFlushIntervalMs` 接口。
     * 参数说明：
     * @param flushIntervalMs 参数 `flushIntervalMs`。
     * 返回值说明：
     * 无
     */
    public static void setFlushIntervalMs(long flushIntervalMs);

    /**
     * 方法描述：`Vostok.Log.setFlushBatchSize` 接口。
     * 参数说明：
     * @param flushBatchSize 参数 `flushBatchSize`。
     * 返回值说明：
     * 无
     */
    public static void setFlushBatchSize(int flushBatchSize);

    /**
     * 方法描述：`Vostok.Log.setShutdownTimeoutMs` 接口。
     * 参数说明：
     * @param shutdownTimeoutMs 参数 `shutdownTimeoutMs`。
     * 返回值说明：
     * 无
     */
    public static void setShutdownTimeoutMs(long shutdownTimeoutMs);

    /**
     * 方法描述：`Vostok.Log.setFsyncPolicy` 接口。
     * 参数说明：
     * @param fsyncPolicy 参数 `fsyncPolicy`。
     * 返回值说明：
     * 无
     */
    public static void setFsyncPolicy(VKLogFsyncPolicy fsyncPolicy);

    /**
     * 方法描述：`Vostok.Log.setRollInterval` 接口。
     * 参数说明：
     * @param interval 参数 `interval`。
     * 返回值说明：
     * 无
     */
    public static void setRollInterval(VKLogRollInterval interval);

    /**
     * 方法描述：`Vostok.Log.setCompressRolledFiles` 接口。
     * 参数说明：
     * @param compress 参数 `compress`。
     * 返回值说明：
     * 无
     */
    public static void setCompressRolledFiles(boolean compress);

    /**
     * 方法描述：`Vostok.Log.setFileRetryIntervalMs` 接口。
     * 参数说明：
     * @param retryIntervalMs 参数 `retryIntervalMs`。
     * 返回值说明：
     * 无
     */
    public static void setFileRetryIntervalMs(long retryIntervalMs);

    /**
     * 方法描述：`Vostok.Log.droppedLogs` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `long` 类型结果。
     */
    public static long droppedLogs();

    /**
     * 方法描述：`Vostok.Log.fallbackWrites` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `long` 类型结果。
     */
    public static long fallbackWrites();

    /**
     * 方法描述：`Vostok.Log.fileWriteErrors` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * @return 返回 `long` 类型结果。
     */
    public static long fileWriteErrors();

    /**
     * 方法描述：`Vostok.Log.flush` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void flush();

    /**
     * 方法描述：`Vostok.Log.shutdown` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void shutdown();

    /**
     * 方法描述：`Vostok.Log.resetDefaults` 接口。
     * 参数说明：
     * 无
     * 返回值说明：
     * 无
     */
    public static void resetDefaults();

}
```

---
# 4. 按模块对每个接口的调用 demo

> 以下 demo 重点展示“接口覆盖调用方式”。

## 4.1 Data 模块 demo（覆盖全部 Data 入口）

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

        // metrics / report
        var metrics = Vostok.Data.poolMetrics();
        String report = Vostok.Data.report();

        Vostok.Data.close();
    }
}
```

## 4.2 Web 模块 demo（覆盖全部 Web 入口）

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.auto.VKCrudStyle;

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

## 4.3 File 模块 demo（覆盖全部 File 入口）

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

        Vostok.File.registerStore("local2", new LocalTextFileStore(java.nio.file.Path.of("/tmp/vostok-files-2")));
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
        Vostok.File.appendBytes("b.bin", new byte[]{6,7});

        String sha = Vostok.File.hash("b.bin", "SHA-256");

        boolean ex = Vostok.File.exists("a.txt");
        boolean isF = Vostok.File.isFile("a.txt");
        boolean isD = Vostok.File.isDirectory("dir");

        Vostok.File.append("a.txt", "\nline2");
        List<String> lines = Vostok.File.readLines("a.txt");
        Vostok.File.writeLines("c.txt", List.of("l1", "l2"));

        List<VKFileInfo> l1 = Vostok.File.list(".");
        List<VKFileInfo> l2 = Vostok.File.list(".", true);
        List<VKFileInfo> w1 = Vostok.File.walk(".", true, info -> !info.directory());
        List<VKFileInfo> w2 = Vostok.File.walk(".", false);

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

## 4.4 Log 模块 demo（覆盖全部 Log 入口）

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
