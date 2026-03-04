package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.mvc.VKMvcConfig;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKGet;
import yueyang.vostok.web.mvc.annotation.VKQuery;
import yueyang.vostok.web.rate.VKRateLimitConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebMvcErrorTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @VKApi("/err")
    static class ErrorApi {
        @VKGet("/bind")
        public String bind(@VKQuery("name") String name) {
            return name;
        }

        @VKGet("/boom")
        public String boom() {
            throw new IllegalStateException("boom-hidden");
        }

        @VKGet("/ok")
        public String ok() {
            return "ok";
        }
    }

    @Test
    void testBindErrorAndInvokeErrorAndMiddlewareCompat() throws Exception {
        Vostok.Web.init(0)
                .mvcConfig(VKMvcConfig.defaults().exposeExceptionMessage(false))
                .use((req, res, chain) -> {
                    res.header("X-MW", "1");
                    chain.next(req, res);
                })
                .cors()
                .rateLimit(new VKRateLimitConfig().capacity(100).refillTokens(100))
                .controller(new ErrorApi());
        Vostok.Web.start();

        int port = Vostok.Web.port();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> bindErr = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/err/bind"))
                        .header("Origin", "https://a.example")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, bindErr.statusCode());
        assertEquals("1", bindErr.headers().firstValue("X-MW").orElse(""));
        assertTrue(bindErr.headers().firstValue("Access-Control-Allow-Origin").isPresent());
        assertTrue(bindErr.body().contains("\"statusCode\":400"));
        assertTrue(bindErr.body().contains("\"errorMessage\":"));

        HttpResponse<String> invokeErr = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/err/boom"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(500, invokeErr.statusCode());
        assertTrue(invokeErr.body().contains("Internal Server Error"));
        assertTrue(!invokeErr.body().contains("boom-hidden"));

        HttpResponse<String> ok = client.send(HttpRequest.newBuilder()
                        .uri(new URI("http://127.0.0.1:" + port + "/err/ok"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"data\":\"ok\""));
    }
}
