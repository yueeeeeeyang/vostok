package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.VostokWeb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
}
