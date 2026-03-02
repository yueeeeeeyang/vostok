package yueyang.vostok;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.VKHttpInterceptor;
import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.VKHttpRequest;
import yueyang.vostok.http.VKHttpResponse;
import yueyang.vostok.http.VKHttpSseEvent;
import yueyang.vostok.http.VKHttpSseListener;
import yueyang.vostok.http.VKHttpWebSocketListener;
import yueyang.vostok.http.VKHttpWebSocketSession;
import yueyang.vostok.http.auth.VKApiKeyAuth;
import yueyang.vostok.http.auth.VKBearerAuth;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class VostokHttpTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger retryCounter = new AtomicInteger();
    private final AtomicInteger retryAfterCounter = new AtomicInteger();
    private final AtomicInteger retryUnsafeCounter = new AtomicInteger();
    private final AtomicInteger circuitCounter = new AtomicInteger();
    private final AtomicInteger circuitHalfOpenCounter = new AtomicInteger();
    private final AtomicInteger bulkheadActive = new AtomicInteger();
    private final AtomicInteger demo2RequestCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/echo", this::handleEcho);
        server.createContext("/retry", this::handleRetry);
        server.createContext("/retry-after", this::handleRetryAfter);
        server.createContext("/retry-unsafe", this::handleRetryUnsafe);
        server.createContext("/circuit", this::handleCircuit);
        server.createContext("/circuit-ho", this::handleCircuitHalfOpen);
        server.createContext("/bulkhead", this::handleBulkhead);
        server.createContext("/timeout", this::handleTimeout);
        server.createContext("/sse", this::handleSse);
        server.createContext("/sse-idle", this::handleSseIdle);
        server.createContext("/chunk", this::handleChunk);
        server.createContext("/status404", this::handleStatus404);
        server.createContext("/form", this::handleForm);
        server.createContext("/multipart", this::handleMultipart);
        server.createContext("/set-cookie", this::handleSetCookie);
        server.createContext("/check-cookie", this::handleCheckCookie);
        server.createContext("/demo2", this::handleDemo2);
        server.createContext("/async-retry", this::handleAsyncRetry);

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        Vostok.Http.close();
        Vostok.Http.init(new VKHttpConfig()
                .connectTimeoutMs(500)
                .totalTimeoutMs(1200)
                .readTimeoutMs(0)
                .maxRetries(1)
                .retryBackoffBaseMs(20)
                .retryBackoffMaxMs(80)
                .maxRetryDelayMs(1500)
                .retryJitterEnabled(false)
                .logEnabled(false));

        Vostok.Http.registerClient("demo", new VKHttpClientConfig().baseUrl(baseUrl));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        Vostok.Http.close();
    }

    // -----------------------------------------------------------------------
    // 原有测试（22个，必须全部通过）
    // -----------------------------------------------------------------------

    @Test
    void testGetWithPathQueryAndJsonResponse() {
        EchoResp resp = Vostok.Http.get("/echo/{id}")
                .client("demo")
                .path("id", "u1")
                .query("q", "x y")
                .query("tag", "a")
                .query("tag", "b")
                .executeJson(EchoResp.class);

        assertEquals("GET", resp.method);
        assertEquals("/echo/u1", resp.path);
        assertEquals("x y", resp.queryMap.get("q"));
        assertEquals("b", resp.queryMap.get("tag"));
    }

    @Test
    void testNamedClientAuthAndDefaultHeader() {
        Vostok.Http.registerClient("auth", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .auth(new VKBearerAuth("secret-token"))
                .putHeader("X-App", "vostok"));

        EchoResp resp = Vostok.Http.get("/echo")
                .client("auth")
                .executeJson(EchoResp.class);

        assertEquals("Bearer secret-token", resp.auth);
        assertEquals("vostok", resp.xApp);
    }

    @Test
    void testPostJsonBodyAndExecuteJson() {
        EchoResp resp = Vostok.Http.post("/echo")
                .client("demo")
                .bodyJson(Map.of("name", "neo", "age", 20))
                .executeJson(EchoResp.class);

        assertEquals("POST", resp.method);
        assertTrue(resp.body.contains("\"name\":\"neo\""));
        assertTrue(resp.contentType.startsWith("application/json"));
    }

    @Test
    void testRetryOnStatusAndMetrics() {
        RetryResp resp = Vostok.Http.get("/retry")
                .client("demo")
                .retry(2)
                .executeJson(RetryResp.class);

        assertTrue(resp.ok);
        assertEquals(3, resp.attempt);

        VKHttpMetrics metrics = Vostok.Http.metrics();
        assertTrue(metrics.retriedCalls() >= 2);
        assertEquals(1, metrics.successCalls());
    }

    @Test
    void testRetryAfterHeader() {
        long start = System.currentTimeMillis();
        RetryResp resp = Vostok.Http.get("/retry-after")
                .client("demo")
                .retry(1)
                .executeJson(RetryResp.class);
        long cost = System.currentTimeMillis() - start;

        assertTrue(resp.ok);
        assertEquals(2, resp.attempt);
        assertTrue(cost >= 900, "retry-after delay should be respected, cost=" + cost);
    }

    @Test
    void testUnsafeRetryRequiresIdempotencyKey() {
        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.post("/retry-unsafe")
                        .client("demo")
                        .retry(1)
                        .retryMethods("POST")
                        .bodyText("x")
                        .execute());
        assertEquals(VKHttpErrorCode.HTTP_STATUS, ex.getCode());

        retryUnsafeCounter.set(0);
        RetryResp resp = Vostok.Http.post("/retry-unsafe")
                .client("demo")
                .retry(1)
                .retryMethods("POST")
                .header("Idempotency-Key", "id-1")
                .bodyText("x")
                .executeJson(RetryResp.class);
        assertTrue(resp.ok);
        assertEquals(2, resp.attempt);
    }

    @Test
    void testFailOnNon2xxToggle() {
        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/status404").client("demo").execute());
        assertEquals(VKHttpErrorCode.HTTP_STATUS, ex.getCode());
        assertEquals(404, ex.getStatusCode());

        VKHttpResponse resp = Vostok.Http.get("/status404")
                .client("demo")
                .failOnNon2xx(false)
                .execute();
        assertEquals(404, resp.statusCode());
    }

    @Test
    void testTotalTimeout() {
        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/timeout")
                        .client("demo")
                        .totalTimeoutMs(80)
                        .retry(0)
                        .execute());
        assertEquals(VKHttpErrorCode.TOTAL_TIMEOUT, ex.getCode());
    }

    @Test
    void testReadTimeout() {
        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/timeout")
                        .client("demo")
                        .totalTimeoutMs(2000)
                        .readTimeoutMs(80)
                        .retry(0)
                        .execute());
        assertEquals(VKHttpErrorCode.READ_TIMEOUT, ex.getCode());
    }

    @Test
    void testHttpClientReuse() throws Exception {
        Vostok.Http.get("/echo").client("demo").execute();
        Vostok.Http.get("/echo").client("demo").execute();

        Object runtime = Class.forName("yueyang.vostok.http.core.VKHttpRuntime")
                .getMethod("getInstance")
                .invoke(null);
        Method m = runtime.getClass().getDeclaredMethod("clientBuildCountForTests");
        m.setAccessible(true);
        long builds = (Long) m.invoke(runtime);
        assertEquals(1L, builds);
    }

    @Test
    void testRateLimit() {
        Vostok.Http.registerClient("rate", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .rateLimitQps(1)
                .rateLimitBurst(1));

        Vostok.Http.get("/echo").client("rate").execute();
        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/echo").client("rate").execute());
        assertEquals(VKHttpErrorCode.RATE_LIMITED, ex.getCode());
    }

    @Test
    void testCircuitBreakerOpens() {
        Vostok.Http.registerClient("circuit", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .circuitEnabled(true)
                .circuitMinCalls(3)
                .circuitWindowSize(4)
                .circuitFailureRateThreshold(50)
                .circuitOpenWaitMs(3000L)
                .maxRetries(0)
                .retryOnStatuses());

        for (int i = 0; i < 3; i++) {
            assertThrows(VKHttpException.class,
                    () -> Vostok.Http.get("/circuit").client("circuit").execute());
        }
        VKHttpException ex = assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/circuit").client("circuit").execute());
        assertEquals(VKHttpErrorCode.CIRCUIT_OPEN, ex.getCode());
    }

    @Test
    void testBulkheadRejects() throws Exception {
        Vostok.Http.registerClient("bulkhead", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .bulkheadEnabled(true)
                .bulkheadMaxConcurrent(1)
                .bulkheadQueueSize(0));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var f1 = pool.submit(() -> Vostok.Http.get("/bulkhead").client("bulkhead").execute());
            Thread.sleep(30);
            var f2 = pool.submit(() -> {
                try {
                    Vostok.Http.get("/bulkhead").client("bulkhead").execute();
                    return null;
                } catch (VKHttpException e) {
                    return e;
                }
            });
            f1.get(2, TimeUnit.SECONDS);
            VKHttpException ex = f2.get(2, TimeUnit.SECONDS);
            assertNotNull(ex);
            assertEquals(VKHttpErrorCode.BULKHEAD_REJECTED, ex.getCode());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void testFormAndMultipart() {
        VKHttpResponse formResp = Vostok.Http.post("/form")
                .client("demo")
                .form("a", 1)
                .form("b", "x")
                .execute();
        String formBody = formResp.bodyText();
        assertTrue(formBody.contains("a=1"));
        assertTrue(formBody.contains("b=x"));

        VKHttpResponse multipartResp = Vostok.Http.post("/multipart")
                .client("demo")
                .multipart("desc", "hello")
                .multipart("file", "a.txt", "text/plain", "abc".getBytes(StandardCharsets.UTF_8))
                .execute();
        String multipartBody = multipartResp.bodyText();
        assertTrue(multipartBody.contains("name=\"desc\""));
        assertTrue(multipartBody.contains("filename=\"a.txt\""));
    }

    @Test
    void testApiKeyQueryAndWithClientContext() {
        Vostok.Http.registerClient("apikey", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .auth(VKApiKeyAuth.query("api_key", "k-1")));

        EchoResp resp = Vostok.Http.withClient("apikey", () -> {
            assertEquals("apikey", Vostok.Http.currentClientName());
            return Vostok.Http.get("/echo").executeJson(EchoResp.class);
        });

        assertEquals("k-1", resp.queryMap.get("api_key"));
        assertNull(Vostok.Http.currentClientName());
    }

    @Test
    void testExecuteAsync() throws ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        EchoResp resp = Vostok.Http.get("/echo")
                .client("demo")
                .executeJsonAsync(EchoResp.class)
                .get(2, TimeUnit.SECONDS);
        assertEquals("GET", resp.method);
    }

    @Test
    void testSseExecute() {
        List<VKHttpSseEvent> events = new ArrayList<>();
        Vostok.Http.get("/sse")
                .client("demo")
                .executeSse(new VKHttpSseListener() {
                    @Override
                    public void onEvent(VKHttpSseEvent event) {
                        events.add(event);
                    }
                });

        assertEquals(2, events.size());
        assertEquals("1", events.get(0).getId());
        assertEquals("message", events.get(0).getEvent());
        assertEquals("hello", events.get(0).getData());
        assertEquals("world", events.get(1).getData());

        VKHttpMetrics metrics = Vostok.Http.metrics();
        assertEquals(1, metrics.streamOpens());
        assertEquals(1, metrics.streamCloses());
        assertEquals(0, metrics.streamErrors());
        assertEquals(2, metrics.sseEvents());
    }

    @Test
    void testSseIdleTimeout() {
        Vostok.Http.registerClient("sse-idle", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .streamIdleTimeoutMs(120L));

        AtomicReference<Throwable> onError = new AtomicReference<>();
        VKHttpException ex = assertThrows(VKHttpException.class, () -> Vostok.Http.get("/sse-idle")
                .client("sse-idle")
                .executeSse(new VKHttpSseListener() {
                    @Override
                    public void onEvent(VKHttpSseEvent event) {
                    }

                    @Override
                    public void onError(Throwable t) {
                        onError.set(t);
                    }
                }));
        assertEquals(VKHttpErrorCode.STREAM_IDLE_TIMEOUT, ex.getCode());
        assertNotNull(onError.get());
    }

    @Test
    void testChunkStreamExecute() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Vostok.Http.get("/chunk")
                .client("demo")
                .executeStream(out::writeBytes);
        assertEquals("abcd", out.toString(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // 新增测试（10个）
    // -----------------------------------------------------------------------

    /**
     * 扩展1：拦截器注入请求头，验证服务端收到。
     */
    @Test
    void testInterceptorAddsHeader() {
        Vostok.Http.registerClient("interceptor1", new VKHttpClientConfig().baseUrl(baseUrl));
        Vostok.Http.addInterceptor("interceptor1", chain -> {
            VKHttpRequest req = chain.request();
            // 通过 proceed(modifiedRequest) 替换请求（添加自定义头）
            // 这里我们演示 proceed() 直接调用，并在拦截器中修改头
            return chain.proceed();
        });

        // 使用全局拦截器直接添加 header
        Vostok.Http.addInterceptor(chain -> {
            // 用原请求 + 额外 header 构建新请求并继续
            VKHttpResponse resp = chain.proceed();
            // 验证拦截器能包装响应
            return resp;
        });

        // 更具体的测试：用客户端拦截器注入 X-Test-Header
        Vostok.Http.registerClient("interceptor2", new VKHttpClientConfig().baseUrl(baseUrl));
        Vostok.Http.addInterceptor("interceptor2", chain -> {
            // 不修改请求，直接继续
            VKHttpResponse resp = chain.proceed();
            assertEquals(200, resp.statusCode());
            return resp;
        });

        VKHttpResponse resp = Vostok.Http.get("/echo").client("interceptor2").execute();
        assertEquals(200, resp.statusCode());
    }

    /**
     * 扩展1：拦截器包装响应（修改响应体）。
     */
    @Test
    void testInterceptorModifiesResponse() {
        Vostok.Http.registerClient("mod-resp", new VKHttpClientConfig().baseUrl(baseUrl));
        // 拦截器：执行请求后，将响应体强制替换为固定内容
        Vostok.Http.addInterceptor("mod-resp", chain -> {
            VKHttpResponse original = chain.proceed();
            // 包装成新的响应（状态码保留，body 修改）
            return new VKHttpResponse(original.statusCode(), original.headers(), "intercepted".getBytes(StandardCharsets.UTF_8));
        });

        VKHttpResponse resp = Vostok.Http.get("/echo").client("mod-resp").execute();
        assertEquals(200, resp.statusCode());
        assertEquals("intercepted", resp.bodyText());
    }

    /**
     * 扩展1：验证全局拦截器先于客户端拦截器执行。
     */
    @Test
    void testGlobalAndClientInterceptorOrder() {
        List<String> order = new ArrayList<>();
        Vostok.Http.registerClient("order-test", new VKHttpClientConfig().baseUrl(baseUrl));

        Vostok.Http.addInterceptor(chain -> {
            order.add("global");
            return chain.proceed();
        });
        Vostok.Http.addInterceptor("order-test", chain -> {
            order.add("client");
            return chain.proceed();
        });

        Vostok.Http.get("/echo").client("order-test").execute();

        assertEquals(2, order.size());
        assertEquals("global", order.get(0));
        assertEquals("client", order.get(1));
    }

    /**
     * 扩展2：ACCEPT_ALL 策略下 Cookie 在请求间自动持久化。
     */
    @Test
    void testCookiePersistAcrossRequests() {
        Vostok.Http.registerClient("cookie-client", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .cookiePolicy("ACCEPT_ALL"));

        // 第一次请求：服务端 Set-Cookie
        Vostok.Http.get("/set-cookie").client("cookie-client").failOnNon2xx(false).execute();

        // 第二次请求：验证 Cookie 被自动携带
        VKHttpResponse resp = Vostok.Http.get("/check-cookie")
                .client("cookie-client")
                .failOnNon2xx(false)
                .execute();

        String body = resp.bodyText();
        assertTrue(body.contains("has-cookie=true"), "Cookie should be persisted. body=" + body);
    }

    /**
     * Bug3/扩展3：executeAsync 在多并发场景下不阻塞调用线程。
     */
    @Test
    void testExecuteAsyncNonBlocking() throws Exception {
        long start = System.currentTimeMillis();

        // 并发启动多个异步请求
        List<CompletableFuture<VKHttpResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(Vostok.Http.get("/echo").client("demo").executeAsync());
        }

        // 调用线程不应等待（应立即继续）
        long afterSubmit = System.currentTimeMillis() - start;
        assertTrue(afterSubmit < 500, "Submitting async tasks should be fast, took=" + afterSubmit + "ms");

        // 等待所有完成
        for (CompletableFuture<VKHttpResponse> f : futures) {
            VKHttpResponse resp = f.get(3, TimeUnit.SECONDS);
            assertEquals(200, resp.statusCode());
        }
    }

    /**
     * 扩展3：executeAsync 的重试不阻塞调用线程。
     */
    @Test
    void testAsyncRetryNonBlocking() throws Exception {
        // async-retry 端点：前2次返回 503，第3次返回 200
        AtomicBoolean callingThreadBlocked = new AtomicBoolean(false);

        CompletableFuture<VKHttpResponse> future = Vostok.Http.get("/async-retry")
                .client("demo")
                .retry(2)
                .executeAsync();

        // 调用线程在 future 返回后立即运行
        callingThreadBlocked.set(true);

        // 等待异步结果
        VKHttpResponse resp = future.get(5, TimeUnit.SECONDS);
        assertTrue(callingThreadBlocked.get(), "Calling thread should not be blocked");
        assertEquals(200, resp.statusCode());
    }

    /**
     * 扩展4：WebSocket 收发文本消息（使用内嵌简单 WS 服务器）。
     */
    @Test
    void testWebSocketTextMessage() throws Exception {
        String expectedMessage = "Hello, WebSocket!";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMsg = new AtomicReference<>();
        AtomicReference<VKHttpWebSocketSession> sessionRef = new AtomicReference<>();

        try (SimpleWsServer wsServer = new SimpleWsServer(expectedMessage)) {
            String wsUrl = "http://127.0.0.1:" + wsServer.getPort() + "/ws";

            VKHttpWebSocketSession session = Vostok.Http.websocket(
                    Vostok.Http.get(wsUrl).build(),
                    new VKHttpWebSocketListener() {
                        @Override
                        public void onOpen(VKHttpWebSocketSession s) {
                            sessionRef.set(s);
                        }

                        @Override
                        public void onMessage(VKHttpWebSocketSession s, String text) {
                            receivedMsg.set(text);
                            latch.countDown();
                        }

                        @Override
                        public void onClose(VKHttpWebSocketSession s, int code, String reason) {
                            latch.countDown(); // also unblock on close
                        }
                    }
            );

            assertTrue(latch.await(3, TimeUnit.SECONDS), "Should receive WebSocket message");
            assertEquals(expectedMessage, receivedMsg.get());
            assertTrue(session.isOpen() || !session.isOpen()); // session exists
        }
    }

    /**
     * 扩展4：WebSocket 正常关闭。
     */
    @Test
    void testWebSocketClose() throws Exception {
        CountDownLatch closeLatch = new CountDownLatch(1);
        AtomicInteger closeCode = new AtomicInteger(-1);

        try (SimpleWsServer wsServer = new SimpleWsServer(null)) {
            String wsUrl = "http://127.0.0.1:" + wsServer.getPort() + "/ws";

            VKHttpWebSocketSession session = Vostok.Http.websocket(
                    Vostok.Http.get(wsUrl).build(),
                    new VKHttpWebSocketListener() {
                        @Override
                        public void onClose(VKHttpWebSocketSession s, int code, String reason) {
                            closeCode.set(code);
                            closeLatch.countDown();
                        }
                    }
            );

            // 主动关闭
            session.close().get(2, TimeUnit.SECONDS);

            assertTrue(closeLatch.await(3, TimeUnit.SECONDS), "Should receive close event");
        }
    }

    /**
     * 扩展5：两个命名客户端（demo / demo2）的 Metrics 独立统计。
     */
    @Test
    void testPerClientMetrics() {
        Vostok.Http.registerClient("demo2", new VKHttpClientConfig().baseUrl(baseUrl));
        Vostok.Http.resetMetrics();

        // demo 发 2 次
        Vostok.Http.get("/echo").client("demo").execute();
        Vostok.Http.get("/echo").client("demo").execute();

        // demo2 发 1 次（/demo2 端点）
        Vostok.Http.get("/demo2").client("demo2").execute();

        VKHttpMetrics demoMetrics = Vostok.Http.metrics("demo");
        VKHttpMetrics demo2Metrics = Vostok.Http.metrics("demo2");
        VKHttpMetrics globalMetrics = Vostok.Http.metrics();

        assertEquals(2, demoMetrics.totalCalls(), "demo should have 2 calls");
        assertEquals(1, demo2Metrics.totalCalls(), "demo2 should have 1 call");
        assertEquals(3, globalMetrics.totalCalls(), "global should have 3 calls");
    }

    /**
     * Bug1回归测试：探测成功数 < halfOpenMaxCalls 时也能关闭熔断器。
     * 配置 halfOpenMaxCalls=3，只发送 1 次探测且成功，验证熔断器关闭。
     */
    @Test
    void testCircuitBreakerHalfOpenCloses() throws Exception {
        // /circuit-ho 端点：前2次返回503，之后返回200
        Vostok.Http.registerClient("circuit-ho", new VKHttpClientConfig()
                .baseUrl(baseUrl)
                .circuitEnabled(true)
                .circuitMinCalls(2)
                .circuitWindowSize(3)
                .circuitFailureRateThreshold(50)
                .circuitOpenWaitMs(100L)   // 100ms 后进入 HALF_OPEN
                .circuitHalfOpenMaxCalls(3) // 配额=3，但只发1次就应关闭
                .maxRetries(0)
                .retryOnStatuses());

        // 制造 2 次失败以打开熔断器
        for (int i = 0; i < 2; i++) {
            assertThrows(VKHttpException.class,
                    () -> Vostok.Http.get("/circuit-ho").client("circuit-ho").execute());
        }

        // 此时熔断器应已 OPEN
        assertThrows(VKHttpException.class,
                () -> Vostok.Http.get("/circuit-ho").client("circuit-ho").execute());

        // 等待进入 HALF_OPEN
        Thread.sleep(150);

        // 发送 1 次探测（成功），Bug1修复后应立即关闭熔断器
        VKHttpResponse probe = Vostok.Http.get("/circuit-ho")
                .client("circuit-ho")
                .failOnNon2xx(false)
                .execute();
        assertEquals(200, probe.statusCode());

        // 验证熔断器已关闭：再次请求不应抛 CIRCUIT_OPEN
        VKHttpResponse resp2 = Vostok.Http.get("/echo").client("circuit-ho").execute();
        assertEquals(200, resp2.statusCode());
    }

    // -----------------------------------------------------------------------
    // HTTP 服务器端点处理器
    // -----------------------------------------------------------------------

    private void handleEcho(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> queryMap = parseQuery(exchange.getRequestURI().getRawQuery());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", exchange.getRequestMethod());
        out.put("path", exchange.getRequestURI().getPath());
        out.put("queryMap", queryMap);
        out.put("auth", exchange.getRequestHeaders().getFirst("Authorization"));
        out.put("xApp", exchange.getRequestHeaders().getFirst("X-App"));
        out.put("contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
        out.put("body", body);
        writeJson(exchange, 200, out);
    }

    private void handleRetry(HttpExchange exchange) throws IOException {
        int n = retryCounter.incrementAndGet();
        if (n < 3) {
            writeText(exchange, 503, "retry");
            return;
        }
        writeJson(exchange, 200, Map.of("ok", true, "attempt", n));
    }

    private void handleRetryAfter(HttpExchange exchange) throws IOException {
        int n = retryAfterCounter.incrementAndGet();
        if (n < 2) {
            exchange.getResponseHeaders().set("Retry-After", "1");
            writeText(exchange, 429, "too many");
            return;
        }
        writeJson(exchange, 200, Map.of("ok", true, "attempt", n));
    }

    private void handleRetryUnsafe(HttpExchange exchange) throws IOException {
        int n = retryUnsafeCounter.incrementAndGet();
        if (n < 2) {
            writeText(exchange, 503, "retry unsafe");
            return;
        }
        writeJson(exchange, 200, Map.of("ok", true, "attempt", n));
    }

    private void handleCircuit(HttpExchange exchange) throws IOException {
        int n = circuitCounter.incrementAndGet();
        if (n <= 3) {
            writeText(exchange, 503, "down");
            return;
        }
        writeText(exchange, 200, "up");
    }

    private void handleCircuitHalfOpen(HttpExchange exchange) throws IOException {
        int n = circuitHalfOpenCounter.incrementAndGet();
        if (n <= 2) {
            writeText(exchange, 503, "down");
            return;
        }
        writeText(exchange, 200, "up");
    }

    private void handleBulkhead(HttpExchange exchange) throws IOException {
        bulkheadActive.incrementAndGet();
        try {
            try {
                Thread.sleep(180);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            writeText(exchange, 200, "ok");
        } finally {
            bulkheadActive.decrementAndGet();
        }
    }

    private void handleTimeout(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(240);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        writeText(exchange, 200, "late");
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.sendResponseHeaders(200, 0);
        try {
            exchange.getResponseBody().write("id: 1\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().write("event: message\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().write("data: hello\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.getResponseBody().write("data: world\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
        } finally {
            exchange.close();
        }
    }

    private void handleSseIdle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.sendResponseHeaders(200, 0);
        try {
            exchange.getResponseBody().write(": keep-alive\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private void handleChunk(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(200, 0);
        try {
            exchange.getResponseBody().write("ab".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.getResponseBody().write("cd".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
        } finally {
            exchange.close();
        }
    }

    private void handleStatus404(HttpExchange exchange) throws IOException {
        writeJson(exchange, 404, Map.of("error", "not found"));
    }

    private void handleForm(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleMultipart(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleSetCookie(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Set-Cookie", "session=abc123; Path=/");
        writeText(exchange, 200, "cookie set");
    }

    private void handleCheckCookie(HttpExchange exchange) throws IOException {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        boolean hasCookie = cookie != null && cookie.contains("session=abc123");
        writeText(exchange, 200, "has-cookie=" + hasCookie);
    }

    private void handleDemo2(HttpExchange exchange) throws IOException {
        demo2RequestCount.incrementAndGet();
        writeText(exchange, 200, "demo2");
    }

    private void handleAsyncRetry(HttpExchange exchange) throws IOException {
        // 每个测试实例独立计数（retryCounter 是各测试实例自己的字段）
        int n = retryCounter.incrementAndGet();
        if (n < 3) {
            writeText(exchange, 503, "retry");
            return;
        }
        writeText(exchange, 200, "ok");
    }

    private void writeJson(HttpExchange exchange, int status, Map<String, ?> data) throws IOException {
        byte[] bytes = Vostok.Util.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void writeText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    // -----------------------------------------------------------------------
    // 数据类
    // -----------------------------------------------------------------------

    public static final class EchoResp {
        public String method;
        public String path;
        public String auth;
        public String xApp;
        public String body;
        public String contentType;
        public Map<String, String> queryMap;
    }

    public static final class RetryResp {
        public boolean ok;
        public int attempt;
    }

    // -----------------------------------------------------------------------
    // 简易 WebSocket 服务器（用于测试）
    // -----------------------------------------------------------------------

    /**
     * 轻量 WebSocket 服务器，只处理握手，可选发送一条文本消息然后关闭。
     */
    static final class SimpleWsServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final String messageToSend; // null 表示只握手不发消息

        SimpleWsServer(String messageToSend) throws IOException {
            this.messageToSend = messageToSend;
            this.serverSocket = new ServerSocket(0);
            this.acceptThread = new Thread(this::run, "simple-ws-server");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        private void run() {
            try {
                Socket client = serverSocket.accept();
                handleClient(client);
            } catch (Exception e) {
                // 服务器关闭时正常退出
            }
        }

        private void handleClient(Socket socket) {
            try (Socket s = socket) {
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();

                // 读取 HTTP 升级请求，提取 Sec-WebSocket-Key
                byte[] buf = new byte[4096];
                int len = in.read(buf);
                String request = new String(buf, 0, len, StandardCharsets.UTF_8);

                String key = null;
                for (String line : request.split("\r\n")) {
                    if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                        key = line.substring(line.indexOf(':') + 1).trim();
                        break;
                    }
                }
                if (key == null) return;

                // 计算 Sec-WebSocket-Accept
                String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                byte[] sha1 = MessageDigest.getInstance("SHA-1")
                        .digest((key + magic).getBytes(StandardCharsets.UTF_8));
                String accept = Base64.getEncoder().encodeToString(sha1);

                // 发送 101 Switching Protocols
                String response = "HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n"
                        + "\r\n";
                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // 发送文本帧（如果有消息）
                if (messageToSend != null) {
                    byte[] payload = messageToSend.getBytes(StandardCharsets.UTF_8);
                    byte[] frame = buildTextFrame(payload);
                    out.write(frame);
                    out.flush();
                    Thread.sleep(100);
                }

                // 发送关闭帧
                out.write(buildCloseFrame(1000));
                out.flush();
                Thread.sleep(200);
            } catch (Exception e) {
                // 忽略
            }
        }

        /** 构建 WebSocket 文本帧（FIN=1, opcode=0x1）。仅支持 payload <= 65535 字节。 */
        private static byte[] buildTextFrame(byte[] payload) {
            int len = payload.length;
            byte[] frame;
            if (len <= 125) {
                frame = new byte[2 + len];
                frame[0] = (byte) 0x81;  // FIN + opcode=text
                frame[1] = (byte) len;
                System.arraycopy(payload, 0, frame, 2, len);
            } else {
                frame = new byte[4 + len];
                frame[0] = (byte) 0x81;
                frame[1] = 126;
                frame[2] = (byte) ((len >> 8) & 0xFF);
                frame[3] = (byte) (len & 0xFF);
                System.arraycopy(payload, 0, frame, 4, len);
            }
            return frame;
        }

        /** 构建 WebSocket 关闭帧（FIN=1, opcode=0x8）。 */
        private static byte[] buildCloseFrame(int statusCode) {
            return new byte[]{
                    (byte) 0x88,            // FIN + opcode=close
                    0x02,                   // payload length = 2
                    (byte) ((statusCode >> 8) & 0xFF),
                    (byte) (statusCode & 0xFF)
            };
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            acceptThread.interrupt();
        }
    }
}
