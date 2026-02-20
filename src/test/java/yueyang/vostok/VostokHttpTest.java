package yueyang.vostok;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.VKHttpResponse;
import yueyang.vostok.http.auth.VKApiKeyAuth;
import yueyang.vostok.http.auth.VKBearerAuth;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VostokHttpTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger retryCounter = new AtomicInteger();
    private final AtomicInteger retryAfterCounter = new AtomicInteger();
    private final AtomicInteger retryUnsafeCounter = new AtomicInteger();
    private final AtomicInteger circuitCounter = new AtomicInteger();
    private final AtomicInteger bulkheadActive = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/echo", this::handleEcho);
        server.createContext("/retry", this::handleRetry);
        server.createContext("/retry-after", this::handleRetryAfter);
        server.createContext("/retry-unsafe", this::handleRetryUnsafe);
        server.createContext("/circuit", this::handleCircuit);
        server.createContext("/bulkhead", this::handleBulkhead);
        server.createContext("/timeout", this::handleTimeout);
        server.createContext("/status404", this::handleStatus404);
        server.createContext("/form", this::handleForm);
        server.createContext("/multipart", this::handleMultipart);

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
}
