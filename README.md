# Vostok

面向 `JDK 17+` 的轻量 Java 框架，通过统一门面 `Vostok` 聚合多个模块能力。
各模块可独立初始化、按需使用。

**当前版本：`1.9.2.2`**

**详细文档**：[Vostok Docs](https://yueeeeeeyang.github.io/vostok/)

---

## Maven 依赖

```xml
<dependency>
  <groupId>yueyang</groupId>
  <artifactId>vostok</artifactId>
  <version>1.9.2.2</version>
</dependency>
```

---

## 模块概览（12 个）

| 模块 | 门面类 | 说明 |
|------|--------|------|
| `Vostok.Data` | `VostokData` | JDBC CRUD、事务、查询构建器、多数据源 |
| `Vostok.Web` | `VostokWeb` | NIO Web 服务、路由、中间件、WebSocket、SSE、自动 CRUD |
| `Vostok.Cache` | `VostokCache` | Memory / Redis / 两级缓存、Pipeline、统计 |
| `Vostok.File` | `VostokFile` | 文件读写、压缩解压、目录操作、监听、文件加解密 |
| `Vostok.Office` | `VostokOffice` | Office 能力入口（支持 Excel/Word/PPT/PDF 读写/统计） |
| `Vostok.Log` | `VostokLog` | 异步日志、滚动压缩、命名 logger、MDC |
| `Vostok.Config` | `VostokConfig` | 配置加载、热更新、变更监听、类型绑定 |
| `Vostok.Security` | `VostokSecurity` | SQL/XSS/路径等安全检测、加解密、签名验签 |
| `Vostok.Event` | `VostokEvent` | 事件总线（同步/异步、优先级、一次性监听） |
| `Vostok.Http` | `VostokHttp` | HTTP 客户端、命名 client、重试、SSE/流式 |
| `Vostok.AI` | `VostokAI` | Chat、Session、Embedding、Rerank、RAG、Tool |
| `Vostok.Util` | `VostokUtil` | JSON、字符串、集合、时间、编码等工具 |

---

## 构建

```bash
mvn compile
mvn test
mvn package
```

---

## 快速开始

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.log.VKLogConfig;

// Log
Vostok.Log.init(new VKLogConfig()
    .outputDir("logs")
    .consoleEnabled(true));

// Data
Vostok.Data.init(
    new VKDataConfig()
        .url("jdbc:mysql://127.0.0.1:3306/demo")
        .username("root")
        .password("123456")
        .driver("com.mysql.cj.jdbc.Driver")
        .maxActive(20),
    "com.example.entity"
);

// Cache
Vostok.Cache.init(new VKCacheConfig()
    .providerType(VKCacheProviderType.MEMORY)
    .maxEntries(10_000));

// Web
Vostok.Web.init(new VKWebConfig().port(8080))
    .get("/hello", (req, res) -> res.text("Hello, Vostok!"))
    .health()
    .cors();
Vostok.Web.start();

// shutdown
Vostok.Web.stop();
Vostok.Cache.close();
Vostok.Data.close();
Vostok.Log.close();
```

---

## 模块示例

### Data

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.data.query.VKCondition;
import yueyang.vostok.data.query.VKOperator;
import yueyang.vostok.data.query.VKQuery;
import yueyang.vostok.util.annotation.VKEntity;

@VKEntity(table = "users")
public class User {
    @VKId(auto = true)
    private Long id;
    private String name;
    private String email;
    // getter/setter
}

Vostok.Data.insert(user);
User u = Vostok.Data.findById(User.class, 1L);

VKQuery q = VKQuery.create()
    .where(VKCondition.of("email", VKOperator.EQ, "alice@example.com"))
    .limit(10)
    .offset(0);

var list = Vostok.Data.query(User.class, q);

Vostok.Data.tx(() -> {
    Vostok.Data.insert(user);
    Vostok.Data.update(user);
});
```

### Web

```java
import yueyang.vostok.Vostok;

Vostok.Web.init(8080)
    .get("/users/{id}", (req, res) -> {
        String id = req.param("id");
        res.json("{\"id\":\"" + id + "\"}");
    })
    .post("/echo", (req, res) -> res.text(req.bodyText()))
    .gzip()
    .cors()
    .health();

Vostok.Web.start();
```

### Cache

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;

Vostok.Cache.init(new VKCacheConfig()
    .providerType(VKCacheProviderType.REDIS)
    .endpoints("127.0.0.1:6379"));

Vostok.Cache.set("user:1", "tom", 60_000);
String name = Vostok.Cache.get("user:1");

long n = Vostok.Cache.incr("counter");
```

### File

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.VKFileConfig;

Vostok.File.init(new VKFileConfig().baseDir("./data"));
Vostok.File.write("notes/a.txt", "hello");
String text = Vostok.File.read("notes/a.txt");

Vostok.File.gzip("notes/a.txt", "notes/a.txt.gz");
Vostok.File.gunzip("notes/a.txt.gz", "notes/a.copy.txt");
```

### Office

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.excel.VKExcelCell;
import yueyang.vostok.office.excel.VKExcelReadOptions;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.word.VKWordImageLoadMode;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteRequest;
import yueyang.vostok.office.convert.VKOfficeConvertOptions;
import yueyang.vostok.office.template.VKOfficeTemplateData;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.job.*;
import yueyang.vostok.office.ppt.VKPptImageLoadMode;
import yueyang.vostok.office.ppt.VKPptReadOptions;
import yueyang.vostok.office.ppt.VKPptWriteRequest;
import yueyang.vostok.office.pdf.VKPdfImageLoadMode;
import yueyang.vostok.office.pdf.VKPdfReadOptions;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;

Vostok.File.init(new VKFileConfig().baseDir("./data"));
Vostok.Office.init(new VKOfficeConfig());

// Excel
VKExcelWorkbook wb = new VKExcelWorkbook()
    .addSheet(new VKExcelSheet("Orders")
        .addCell(VKExcelCell.stringCell(1, 1, "orderId"))
        .addCell(VKExcelCell.numberCell(2, 2, "99.5")));

Vostok.Office.writeExcel("excel/orders.xlsx", wb);
Vostok.Office.readExcelRows("excel/orders.xlsx", "Orders",
    VKExcelReadOptions.defaults(), row -> {
        // 处理每一行
    });

// Word 生成（文本 + 图片）
VKWordWriteRequest wordReq = new VKWordWriteRequest()
    .addParagraph("订单 A001")
    .addImageBytes("logo.png", logoBytes)
    .addImageFile("images/sign.png");
Vostok.Office.writeWord("word/orders.docx", wordReq);

// Word 读取与统计
String text = Vostok.Office.readWordText("word/orders.docx");
int chars = Vostok.Office.countWordChars("word/orders.docx");
int imageCount = Vostok.Office.countWordImages("word/orders.docx");

// 大文件建议 metadata-only 模式读取图片，避免 OOM
var readOpt = VKWordReadOptions.defaults().imageLoadMode(VKWordImageLoadMode.METADATA_ONLY);
var doc = Vostok.Office.readWord("word/orders.docx", readOpt);

// PPT（方法名中的 PPT 全大写）
VKPptWriteRequest pptReq = new VKPptWriteRequest();
pptReq.addSlide().addParagraph("季度总结 Q1").addImageBytes("chart.png", chartBytes);
Vostok.Office.writePPT("ppt/summary.pptx", pptReq);
String pptText = Vostok.Office.readPPTText("ppt/summary.pptx");
int pptSlides = Vostok.Office.countPPTSlides("ppt/summary.pptx");
var pptReadOpt = VKPptReadOptions.defaults().imageLoadMode(VKPptImageLoadMode.METADATA_ONLY);
var pptDoc = Vostok.Office.readPPT("ppt/summary.pptx", pptReadOpt);

// PDF（方法名中的 PDF 全大写）
VKPdfWriteRequest pdfReq = new VKPdfWriteRequest();
pdfReq.addPage().addParagraph("账单 A001").addImageBytes("logo.png", logoBytes);
Vostok.Office.writePDF("pdf/bill.pdf", pdfReq);
String pdfText = Vostok.Office.readPDFText("pdf/bill.pdf");
int pdfPages = Vostok.Office.countPDFPages("pdf/bill.pdf");
var pdfReadOpt = VKPdfReadOptions.defaults().imageLoadMode(VKPdfImageLoadMode.METADATA_ONLY);
var pdfDoc = Vostok.Office.readPDF("pdf/bill.pdf", pdfReadOpt);

// 模板渲染（支持 {{var}} / {{#list as item}} / {{?cond}}）
Vostok.Office.renderWordTemplate("tpl/order.docx", "out/order.docx",
    VKOfficeTemplateData.create()
        .put("name", "Tom")
        .put("vip", true));
// renderPPTTemplate / renderPDFTemplate 的参数与语法和 Word 模板一致

// Excel 模板渲染（行级循环）
// 模板中：
//   起始行单元格: {{#items as item keepPlaceholderRows=false}}
//   结束行单元格: {{/items}}
Vostok.Office.renderExcelTemplate(
    "tpl/order.xlsx",
    "out/order.xlsx",
    Map.of(
        "orderNo", "A20260304001",
        "items", List.of(
            Map.of("name", "可乐", "qty", 2, "amount", "8.00"),
            Map.of("name", "薯片", "qty", 1, "amount", "6.00")
        ),
        "total", "14.00"
    ),
    VKExcelTemplateOptions.defaults().defaultKeepPlaceholderRows(true)
);

// 转换能力
Vostok.Office.convertToPDF("word/orders.docx", "pdf/orders.pdf");
Vostok.Office.convertExcelToCSV("excel/orders.xlsx", "csv/orders.csv",
    VKOfficeConvertOptions.defaults().csvSheetName("Orders"));
Vostok.Office.convertCSVToExcel("csv/orders.csv", "excel/orders-back.xlsx");

// 流式读取（块回调）
Vostok.Office.readWordStream("word/orders.docx", block -> {
    // block.type()/text()/image()
});
Vostok.Office.readPPTStream("ppt/summary.pptx", block -> {});
Vostok.Office.readPDFStream("pdf/bill.pdf", block -> {});

// 结构化提取
var wordStructured = Vostok.Office.readWordStructured("word/orders.docx");
var pptStructured = Vostok.Office.readPPTStructured("ppt/summary.pptx");
var pdfStructured = Vostok.Office.readPDFStructured("pdf/bill.pdf");

// 异步任务 + 类 Event 风格回调（不依赖 Vostok.Event）
Vostok.Office.onJobCompleted(n ->
    System.out.println("job completed: " + n.jobId() + ", result=" + n.resultPath()));
Vostok.Office.onJobDeadLetter(n ->
    System.out.println("unhandled office job notification: " + n.status()));

String jobId = Vostok.Office.submitJob(VKOfficeJobRequest.create(() -> {
    Vostok.Office.convertToPDF("word/orders.docx", "pdf/orders-async.pdf");
    return VKOfficeJobExecutionResult.ofPath("pdf/orders-async.pdf");
}).type(VKOfficeJobType.CONVERT).tag("order-batch"));

VKOfficeJobResult result = Vostok.Office.awaitJob(jobId, 30_000);
System.out.println(result.status());
```

### Log

```java
import yueyang.vostok.Vostok;

Vostok.Log.info("server started, port={}", 8080);
Vostok.Log.warn("slow query: {}ms", 123);

Vostok.Log.mdcPut("traceId", "t-001");
Vostok.Log.info("processing request");
Vostok.Log.mdcClear();

Vostok.Log.logger("sql").info("select * from t_user where id=?");
```

### Config

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.config.VKConfigOptions;

Vostok.Config.init(new VKConfigOptions()
    .watchEnabled(true)
    .scanUserDir(true)
    .scanClasspath(true));

String host = Vostok.Config.getString("app.host", "127.0.0.1");
int port = Vostok.Config.getInt("app.port", 8080);
boolean enabled = Vostok.Config.getBool("feature.demo", false);

Vostok.Config.onChange("app.port", (oldV, newV) ->
    System.out.println("app.port changed: " + oldV + " -> " + newV));
```

### Security

```java
import yueyang.vostok.Vostok;

var sqlCheck = Vostok.Security.checkSql("SELECT * FROM user WHERE id = ?", 1L);
if (!sqlCheck.isSafe()) {
    throw new IllegalArgumentException(sqlCheck.getReasons().toString());
}

String safeHtml = Vostok.Security.sanitizeXss("<script>alert(1)</script>");

String key = Vostok.Security.generateAesKey();
String cipher = Vostok.Security.encrypt("hello", key);
String plain = Vostok.Security.decrypt(cipher, key);
```

### Event

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.event.VKListenerMode;

record OrderCreatedEvent(Long orderId) {}

Vostok.Event.init();

Vostok.Event.on(OrderCreatedEvent.class, event -> {
    System.out.println("sync: " + event.orderId());
});

Vostok.Event.on(OrderCreatedEvent.class, VKListenerMode.ASYNC, event -> {
    System.out.println("async: " + event.orderId());
});

Vostok.Event.publish(new OrderCreatedEvent(1001L));
```

### Http

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.VKHttpResponse;

Vostok.Http.init(new VKHttpConfig().maxRetries(1));
Vostok.Http.registerClient("demo", new VKHttpClientConfig().baseUrl("https://api.example.com"));

User user = Vostok.Http.get("/users/{id}")
    .client("demo")
    .path("id", 1)
    .executeJson(User.class);

VKHttpResponse res = Vostok.Http.post("/users")
    .client("demo")
    .bodyJson(newUser)
    .failOnNon2xx(false)
    .execute();

String body = res.bodyText();
```

### AI

```java
import yueyang.vostok.Vostok;
import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.provider.VKAiModelConfig;
import yueyang.vostok.ai.provider.VKAiModelType;

Vostok.AI.registerModel("chat-model", new VKAiModelConfig()
    .type(VKAiModelType.CHAT)
    .baseUrl("https://api.openai.com")
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .model("gpt-4o-mini"));

var chatRes = Vostok.AI.chat(new VKAiChatRequest()
    .model("chat-model")
    .message("user", "用一句话解释量子纠缠"));

System.out.println(chatRes.getText());

var session = Vostok.AI.createSession("chat-model");
var sessionRes = Vostok.AI.chatSession(session.getSessionId(), "继续展开讲讲");
System.out.println(sessionRes.getText());
```

### Util

```java
import yueyang.vostok.Vostok;

String json = Vostok.Util.toJson(Map.of("name", "Tom", "age", 20));
Map<?, ?> m = Vostok.Util.fromJson(json, Map.class);

String snake = Vostok.Util.camelToSnake("userName"); // user_name
String traceId = Vostok.Util.traceId();
```

---

## 说明

- README 仅给出最小可用示例，完整能力请查看在线文档。
- 所有示例方法名均与当前源码门面类保持一致（`Vostok.*`）。
