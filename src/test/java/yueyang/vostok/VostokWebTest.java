package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @Test
    void testGetAndPost() throws Exception {
        AtomicInteger middlewareHit = new AtomicInteger();

        Vostok.Web.init(0)
                .use((req, res, chain) -> {
                    middlewareHit.incrementAndGet();
                    res.header("X-MW", "1");
                    chain.next(req, res);
                })
                .get("/ping", (req, res) -> res.text("ok"))
                .get("/json", (req, res) -> res.json("{\"ok\":true}"))
                .post("/echo", (req, res) -> res.text(req.bodyText()));

        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/ping"))
                .GET()
                .build();
        HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getRes.statusCode());
        assertEquals("ok", getRes.body());
        assertEquals("1", getRes.headers().firstValue("X-MW").orElse(""));

        HttpRequest jsonReq = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/json"))
                .GET()
                .build();
        HttpResponse<String> jsonRes = client.send(jsonReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, jsonRes.statusCode());
        assertEquals("{\"ok\":true}", jsonRes.body());
        assertEquals("application/json; charset=utf-8", jsonRes.headers().firstValue("Content-Type").orElse(""));

        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/echo"))
                .POST(HttpRequest.BodyPublishers.ofString("hello"))
                .build();
        HttpResponse<String> postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, postRes.statusCode());
        assertEquals("hello", postRes.body());

        assertTrue(middlewareHit.get() >= 2);
    }

    @Test
    void testNotFound() throws Exception {
        Vostok.Web.init(0);
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/missing"))
                .GET()
                .build();
        HttpResponse<String> res = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, res.statusCode());
        assertEquals("Not Found", res.body());
    }

    @Test
    void testMaxConnections() throws Exception {
        Vostok.Web.init(new yueyang.vostok.web.VKWebConfig().port(0).maxConnections(1))
                .get("/ping", (req, res) -> res.text("ok"));
        Vostok.Web.start();
        int port = Vostok.Web.port();

        try (Socket s1 = new Socket("127.0.0.1", port)) {
            try (Socket s2 = new Socket("127.0.0.1", port)) {
                s2.setSoTimeout(200);
                OutputStream out = s2.getOutputStream();
                out.write("GET /ping HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
                InputStream in = s2.getInputStream();
                int r = in.read();
                assertTrue(r == -1 || r == 0);
            } catch (IOException ignore) {
            }
        }
    }

    @Test
    void testStaticAndTraceId() throws Exception {
        Path dir = Files.createTempDirectory("vkstatic");
        Path file = dir.resolve("hello.txt");
        Files.writeString(file, "hello");

        Vostok.Web.init(0)
                .staticDir("/static", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + port + "/static/hello.txt"))
                .header("X-Trace-Id", "trace-123")
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertEquals("hello", res.body());
        assertEquals("trace-123", res.headers().firstValue("X-Trace-Id").orElse(""));
    }

    @Test
    void testStaticEtagNotModified() throws Exception {
        Path dir = Files.createTempDirectory("vketag");
        Files.writeString(dir.resolve("app.js"), "console.log('ok');");

        Vostok.Web.init(0)
                .staticDir("/static", dir.toString());
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> r1 = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/static/app.js"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r1.statusCode());
        String etag = r1.headers().firstValue("ETag").orElse("");
        assertTrue(!etag.isEmpty());

        HttpResponse<String> r2 = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/static/app.js"))
                        .header("If-None-Match", etag)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(304, r2.statusCode());
        assertEquals("", r2.body());
    }

    @Test
    void testRouteParamAndWildcard() throws Exception {
        Vostok.Web.init(0)
                .get("/u/{id}/p/:pid", (req, res) -> res.text(req.param("id") + "-" + req.param("pid")))
                .get("/s/{*path}", (req, res) -> res.text(req.param("path")));
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> r1 = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/u/9/p/18"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r1.statusCode());
        assertEquals("9-18", r1.body());

        HttpResponse<String> r2 = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/s/a/b/c.txt"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r2.statusCode());
        assertEquals("a/b/c.txt", r2.body());
    }

    @Test
    void testReadTimeoutByWheel() throws Exception {
        yueyang.vostok.web.VKWebConfig cfg = new yueyang.vostok.web.VKWebConfig()
                .port(0)
                .readTimeoutMs(1000)
                .keepAliveTimeoutMs(5000)
                .accessLogQueueSize(1);

        Vostok.Web.init(cfg)
                .post("/echo", (req, res) -> res.text(req.bodyText()));
        Vostok.Web.start();
        int port = Vostok.Web.port();

        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(("POST /echo HTTP/1.1\r\n" +
                    "Host: 127.0.0.1\r\n" +
                    "Content-Length: 5\r\n\r\n" +
                    "he").getBytes(StandardCharsets.US_ASCII));
            out.flush();

            Thread.sleep(1500);

            InputStream in = s.getInputStream();
            byte[] buf = new byte[1024];
            int n = in.read(buf);
            String raw = n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.US_ASCII);
            assertTrue(raw.contains("408"));
        }
    }
}
