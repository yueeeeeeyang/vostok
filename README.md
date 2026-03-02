# Vostok

面向 `JDK 17+` 的轻量 Java 框架，通过统一门面 `Vostok` 聚合十三个模块能力，一次初始化即可使用数据访问、Web 服务、缓存、日志、配置、安全、事件、游戏引擎、HTTP 客户端、AI 等全部功能。

> 当前版本定位为实验与技术验证，不建议直接用于生产环境。

**详细文档**：[docs/index.html](https://yueeeeeeyang.github.io/vostok/)

---

## 模块

| 模块 | 门面类 | 说明 |
|------|--------|------|
| `Vostok.Data` | `VostokData` | JDBC CRUD、事务、查询构建器、连接池、多数据源 |
| `Vostok.Web` | `VostokWeb` | NIO Reactor Web 服务器、路由、WebSocket、SSE、自动 CRUD |
| `Vostok.Cache` | `VostokCache` | Redis / 内存 / 分层缓存、布隆过滤器、Pipeline 批量 |
| `Vostok.File` | `VostokFile` | 本地文件读写、压缩解压、目录管理、文件监听 |
| `Vostok.Log` | `VostokLog` | 异步日志、滚动归档、MDC 上下文、命名 Logger |
| `Vostok.Config` | `VostokConfig` | 自动扫描配置文件、热监听、插值、多优先级覆盖 |
| `Vostok.Security` | `VostokSecurity` | SQL 注入、XSS、路径穿越等检测，AES/RSA 加密 |
| `Vostok.Event` | `VostokEvent` | 进程内事件总线、同步/异步监听器、优先级 |
| `Vostok.Game` | `VostokGame` | 房间 Tick 引擎、帧同步、匹配、断线重连 |
| `Vostok.Http` | `VostokHttp` | HTTP 客户端、命名 Client、重试、SSE 流 |
| `Vostok.AI` | `VostokAI` | 多模型 Chat、Session、RAG、向量检索、Tool Call |
| `Vostok.Util` | `VostokUtil` | JSON、字符串、ID 生成、时间工具 |

---

## 快速开始

### 构建

```bash
mvn compile        # 编译
mvn test           # 运行测试
mvn package        # 打包
```

### 统一初始化

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.log.VKLogConfig;
import yueyang.vostok.cache.VKCacheConfig;

Vostok.init(cfg -> cfg
    // 日志
    .logConfig(new VKLogConfig().outputDir("logs").consoleEnabled(true))

    // 数据库
    .dataConfig(new VKDataConfig()
        .url("jdbc:mysql://127.0.0.1:3306/demo")
        .username("root").password("123456")
        .maxActive(20))
    .dataPackages("com.example.entity")

    // 缓存（内存）
    .cacheConfig(new VKCacheConfig()
        .providerType("MEMORY").maxEntries(10000))

    // Web 服务器
    .webConfig(new VKWebConfig().port(8080))
    .webSetup(web -> web
        .get("/hello", (req, res) -> res.ok("Hello, Vostok!"))
    )
    .webStart(true)
);

// 关闭所有模块
Vostok.close();
```

配置了哪个模块就启动哪个，未配置的不会被触发。初始化顺序：`Config → Log → Security → Data → Cache → Event → Game → Http → File → AI → Web`。

---

## 模块简单示例

### Data — 数据访问

```java
// 实体定义
@VKEntity(table = "users")
public class User {
    private Long id;
    private String name;
    private String email;
}

// CRUD
Vostok.Data.insert(user);
User u = Vostok.Data.findById(User.class, 1L);

// 条件查询
List<User> list = Vostok.Data.query(User.class,
    VKQuery.where("status", "active").limit(10));

// 事务
Vostok.Data.tx(() -> {
    Vostok.Data.insert(order);
    Vostok.Data.update(inventory);
});
```

### Web — HTTP 服务

```java
Vostok.Web.init(new VKWebConfig().port(8080));

Vostok.Web.get("/users/{id}", (req, res) -> {
    String id = req.pathParam("id");
    res.json("{\"id\":" + id + "}");
});

Vostok.Web.cors();   // 允许跨域
Vostok.Web.gzip();   // 启用压缩
Vostok.Web.health(); // GET /health

Vostok.Web.start();
```

### Cache — 缓存

```java
Vostok.Cache.init(new VKCacheConfig()
    .providerType("REDIS").endpoints("127.0.0.1:6379"));

Vostok.Cache.set("user:1", user, 3600_000L);
User u = Vostok.Cache.get("user:1", User.class);

// 懒加载
User u = Vostok.Cache.getOrLoad("user:1", User.class, 3600_000L, () ->
    Vostok.Data.findById(User.class, 1L));
```

### Log — 日志

```java
Vostok.Log.info("server started on port {}", 8080);
Vostok.Log.warn("slow query: {} ms", elapsed);
Vostok.Log.error("unexpected error", exception);

// MDC
Vostok.Log.mdc("requestId", requestId);
Vostok.Log.info("handling request");
Vostok.Log.mdcClear();
```

### Config — 配置

```java
// 自动扫描 classpath 下 *.properties / *.yml
Vostok.Config.init(new VKConfigOptions().watchEnabled(true));

String host = Vostok.Config.getString("db.host", "localhost");
int    port = Vostok.Config.getInt("db.port", 3306);
```

### Security — 安全检测

```java
if (!Vostok.Security.checkSql(userInput)) {
    throw new SecurityException("SQL injection detected");
}
String clean = Vostok.Security.sanitizeHtml(userHtml);

// AES 加密
String cipher = Vostok.Security.AesCrypto.encrypt(plaintext, keyBase64);
String plain  = Vostok.Security.AesCrypto.decrypt(cipher, keyBase64);
```

### Event — 事件总线

```java
// 订阅
Vostok.Event.on(OrderCreatedEvent.class, event -> {
    System.out.println("order: " + event.orderId());
});

// 异步订阅
Vostok.Event.on(OrderCreatedEvent.class, VKListenerMode.ASYNC, event -> {
    sendEmail(event.userId(), "Order confirmed");
});

// 发布
Vostok.Event.publish(new OrderCreatedEvent(42L, "user-1", amount));
```

### Http — HTTP 客户端

```java
// GET
User user = Vostok.Http.get("https://api.example.com/users/1")
    .executeJson(User.class);

// POST JSON
VKHttpResponse res = Vostok.Http.post("https://api.example.com/users")
    .body(Vostok.Util.toJson(newUser))
    .header("Content-Type", "application/json")
    .execute();
```

### AI — AI 集成

```java
Vostok.AI.registerModel("gpt4", new VKAiModelConfig()
    .modelType(VKAiModelType.GPT4)
    .apiKey(System.getenv("OPENAI_KEY")));

// 单轮对话
VKAiChatResponse res = Vostok.AI.chat(VKAiChatRequest.builder()
    .model("gpt4").userMessage("用一句话解释量子纠缠").build());
System.out.println(res.content());

// 多轮会话
VKAiSession session = Vostok.AI.createSession("gpt4");
String reply = Vostok.AI.chatSession(session.sessionId(), "Java 有哪些特性？");
```

### Util — 工具

```java
// JSON
String json = Vostok.Util.toJson(user);
User u = Vostok.Util.fromJson(json, User.class);

// ID 生成
String uuid = VKIds.uuid();
long   sid  = VKIds.snowflakeId();

// 字符串
VKStrings.camelToSnake("userName");  // "user_name"
VKStrings.isBlank("  ");             // true
```
