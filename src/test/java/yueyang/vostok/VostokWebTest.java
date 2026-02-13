package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.VostokWeb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.Socket;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
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
            // Second connection should be rejected when maxConnections=1
            try (Socket s2 = new Socket("127.0.0.1", port)) {
                s2.setSoTimeout(200);
                OutputStream out = s2.getOutputStream();
                out.write("GET /ping HTTP/1.1\\r\\nHost: 127.0.0.1\\r\\n\\r\\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.flush();
                InputStream in = s2.getInputStream();
                int r = in.read();
                assertTrue(r == -1 || r == 0);
            } catch (IOException ignore) {
                // connect refused or closed is acceptable
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
}
