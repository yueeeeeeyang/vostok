# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build (compile all sources)
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=VostokCacheTest

# Run a single test method
mvn test -Dtest=VostokCacheTest#testPutAndGet

# Build JAR
mvn package

# Build without running tests
mvn package -DskipTests

# Clean build
mvn clean package
```

Tests use H2 in-memory database by default. Tests requiring PostgreSQL or Redis (e.g., `PostgresConcurrencyPressureTest`, `VostokCacheRedisTest`) need live external services and will fail without them.

## Architecture Overview

Vostok is a multi-module Java framework (JDK 17+) that exposes all functionality through a single unified entry class.

### Entry Point & Initialization

**`Vostok.java`** is the sole public entry point. It contains 13 static inner classes (`Vostok.Data`, `Vostok.Web`, `Vostok.Cache`, etc.), each extending the corresponding module facade class (`VostokData`, `VostokWeb`, etc.).

Initialization is done through `VKInitConfig` (builder pattern) — only modules whose config is provided get initialized. Initialization is synchronized and idempotent:

```java
Vostok.init(cfg -> cfg
    .dataConfig(VKDataConfig.builder()...build())
    .dataPackages("com.example.entities")
    .webConfig(VKWebConfig.builder()...build())
    .webSetup(web -> web.route("GET", "/hello", ctx -> ctx.ok("hi")))
    .webStart(true)
);
```

Initialization order: `Config → Log → Security → Data → Cache → Event → Game → Http → File → AI → Web`

On init failure, all already-initialized modules are rolled back in reverse order. `Vostok.close()` shuts down all modules in reverse order.

### Module Facade Pattern

Each module follows the same pattern:
- **Public API class** (e.g., `VostokData`, `VostokWeb`) — abstract class or class with static methods, located at `src/main/java/yueyang/vostok/<module>/`
- **Runtime singleton** (e.g., `VKDataRuntime`, `VKCacheRuntime`) — holds initialized state, accessed via `getInstance()`
- **Config class** (e.g., `VKDataConfig`, `VKWebConfig`) — builder-constructed configuration object
- The `Vostok.<Module>` inner class simply extends the public API class to expose it via the unified entry

### Module Locations & Key Classes

| Module | Package | Runtime | Config |
|--------|---------|---------|--------|
| Data | `data/` | `VKDataRuntime` / `VostokBootstrap` | `VKDataConfig` |
| Web | `web/` | `VKWebServer` / `VKReactor` | `VKWebConfig` |
| Cache | `cache/` | `VKCacheRuntime` | `VKCacheConfig` |
| Game | `game/` | `VKGameRuntime` | `VKGameConfig` |
| AI | `ai/` | `VKAiRuntime` | `VKAiConfig` |
| Security | `security/` | — (stateless scanners) | `VKSecurityConfig` |
| Config | `config/` | `VKConfigRuntime` | `VKConfigOptions` |
| Event | `event/` | `VKEventBus` | `VKEventConfig` |
| File | `file/` | `VKFileRuntime` | `VKFileConfig` |
| Http | `http/` | `VKHttpRuntime` | `VKHttpConfig` |
| Log | `log/` | `VKLogRuntime` | `VKLogConfig` |
| Util | `util/` | `VostokUtil` | — |

### Data Module Internals

The Data module is the most complex. Key sub-components:
- **`VostokBootstrap`** — scans `@VKEntity`-annotated classes, builds `MetaRegistry`
- **`VostokCrudOps`** — CRUD operations (insert, update, delete, select, batch)
- **`VostokTxOps`** — transaction management (propagation, isolation levels)
- **`VKConnectionPool`** — custom connection pool (not HikariCP)
- **`VKTransactionManager`** — per-thread transaction state via `ThreadLocal`
- **`VKDialect`** — SQL dialect per DB type (MySQL, PostgreSQL, Oracle, DB2, SQL Server)
- **`SqlTemplate`** — pre-compiled SQL bound to entity metadata
- **`VostokContext`** — captures datasource context for async thread propagation

Entity classes must be annotated with `@VKEntity`. The `dataPackages` parameter in init tells the framework which packages to scan for entities.

### Web Module Internals

NIO-based server using a Reactor pattern:
- **`VKReactor`** — Java NIO `Selector` event loop
- **`VKRouter`** — route matching and dispatch
- **`VKMiddleware`** — request/response middleware chain
- **`VKWorkerPool`** — thread pool for handler execution
- **`VKRateLimiter`** — per-route or global rate limiting
- **`VKWebSocketEndpoint`** — WebSocket with named rooms/groups
- **`VKAutoCrud`** — auto-generates REST CRUD APIs from `@VKEntity` classes

### Exception Hierarchy

All exceptions extend `VKException`. Module-specific subclasses follow the pattern `VK<Module>Exception` (e.g., `VKSqlException`, `VKPoolException`, `VKTxException`). Error codes are documented in `docs/errors.md`.

### Test Structure

All tests are in `src/test/java/yueyang/vostok/`. Test entity classes (`UserEntity`, `TaskEntity`, etc.) are co-located with tests (not in main sources). Tests requiring external services (Redis, PostgreSQL) are independent test classes that can be skipped.

The `VostokIntegrationTest` is the primary data module integration test and uses H2 in-memory database via `@BeforeAll` setup.

## Development Requirements

### 测试覆盖要求

新增或修改任何功能，必须同步编写或更新对应的测试用例，要求覆盖以下场景：

- **正常路径**：功能的标准使用场景，验证预期输出
- **边界条件**：空值、零值、最大值、最小值等边界输入
- **异常路径**：非法参数、资源不存在、状态错误等，验证抛出正确的异常类型和错误信息
- **并发场景**：涉及线程池、连接池、共享状态的代码，必须包含并发压测用例（参考 `H2ConcurrencyPressureDetailTest`、`PoolBenchmarkTest`）

测试命名规范：`test<被测功能><场景描述>`，例如 `testInsertWithNullFieldThrowsException`。

新增模块的测试类命名规范：`Vostok<Module>Test`，对应已有的 `VostokCacheTest`、`VostokWebTest` 等。

### README 补全要求

新增功能、配置项或模块后，必须同步更新 `README.md`，补全以下内容：

- 功能说明与使用示例（代码片段）
- 所有新增的公共 API 方法签名及其参数说明
- 新增的配置项（参数名、类型、默认值、用途）
- 若引入新的异常类型，在 `docs/errors.md` 中补录对应的错误码和说明

### 注释规范

复杂逻辑和非自明代码必须添加详细的中文注释，具体要求：

- **类级注释**：说明该类的职责、在模块中的角色、与其他关键类的协作关系
- **方法级注释**：说明方法的业务意图、关键参数的含义、返回值语义、可能抛出的异常及触发条件
- **行内注释**：算法步骤、位运算、状态机转换、锁边界、资源释放时机等非显而易见的逻辑，逐步说明原因

以下类型的代码必须有注释，不得省略：
- NIO Selector 事件循环（`VKReactor`）
- 连接池借用/归还/超时逻辑（`VKConnectionPool`）
- 事务传播与隔离级别处理（`VKTransactionManager`、`VostokTxOps`）
- 游戏 Tick 调度与分片均衡（`VKGameTick`、`VKGameShardBalancer`）
- 安全扫描规则匹配逻辑（`security/` 下各 Scanner）
- 所有 `synchronized` 块、`volatile` 字段、`CAS` 操作的用途说明
