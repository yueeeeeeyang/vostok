package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.rate.VKRateLimitKeyStrategy;
import yueyang.vostok.web.spi.VKWebRuntimeSupport;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebDispatcherTest {
    @Test
    void testMiddlewareAlsoRunsOn404() {
        VKWebRuntimeSupport runtime = new VKWebRuntimeSupport(new VKWebConfig());
        runtime.addMiddleware((req, res, chain) -> {
            res.header("X-MW", "1");
            chain.next(req, res);
        });

        VKRequest req = new VKRequest("GET", "/missing", "", "HTTP/1.1", Map.of(), new byte[0], true, null);
        var result = runtime.dispatchHttp(req);

        assertEquals(404, result.response().status());
        assertEquals("1", result.response().headers().get("X-MW"));
        assertEquals("Not Found", new String(result.response().body(), StandardCharsets.UTF_8));
    }

    @Test
    void testErrorHandlerAndRateLimitStillWorkThroughDispatcher() {
        VKWebRuntimeSupport runtime = new VKWebRuntimeSupport(new VKWebConfig());
        runtime.setErrorHandler((error, req, res) -> res.status(590).text("handled:" + error.getClass().getSimpleName()));
        runtime.setGlobalRateLimit(new VKRateLimitConfig()
                .capacity(1)
                .refillTokens(1)
                .refillPeriodMs(60_000)
                .keyStrategy(VKRateLimitKeyStrategy.TRACE_ID));
        runtime.addRoute("GET", "/boom", (req, res) -> {
            throw new IllegalStateException("boom");
        });
        runtime.addRoute("GET", "/ok", (req, res) -> res.text("ok"));

        VKRequest boomReq = new VKRequest(
                "GET", "/boom", "", "HTTP/1.1", Map.of("x-trace-id", "boom-1"), new byte[0], true, null);
        var boom = runtime.dispatchHttp(boomReq);
        assertEquals(590, boom.response().status());
        assertTrue(new String(boom.response().body(), StandardCharsets.UTF_8).contains("handled:IllegalStateException"));

        VKRequest ok1 = new VKRequest(
                "GET", "/ok", "", "HTTP/1.1", Map.of("x-trace-id", "same-client"), new byte[0], true, null);
        VKRequest ok2 = new VKRequest(
                "GET", "/ok", "", "HTTP/1.1", Map.of("x-trace-id", "same-client"), new byte[0], true, null);
        var first = runtime.dispatchHttp(ok1);
        var second = runtime.dispatchHttp(ok2);

        assertEquals(200, first.response().status());
        assertEquals(429, second.response().status());
    }
}
