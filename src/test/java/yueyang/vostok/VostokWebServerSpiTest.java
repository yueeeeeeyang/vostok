package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.VostokWeb;
import yueyang.vostok.web.core.VKBuiltinWebServerEngine;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKGet;
import yueyang.vostok.web.mvc.annotation.VKPath;
import yueyang.vostok.web.spi.VKWebRuntimeSupport;
import yueyang.vostok.web.spi.VKWebServerEngine;
import yueyang.vostok.web.websocket.VKWebSocketHandler;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebServerSpiTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @VKApi("/api/demo")
    static class DemoApi {
        @VKGet("/hello/{id}")
        public Map<String, Object> hello(@VKPath("id") long id) {
            return Map.of("id", id, "name", "demo");
        }
    }

    static final class FakeEngine implements VKWebServerEngine {
        private final VKWebConfig config;
        private final VKWebRuntimeSupport runtime;
        private boolean started;
        private int startCalls;
        private int stopCalls;

        FakeEngine(VKWebConfig config, VKWebRuntimeSupport runtime) {
            this.config = config;
            this.runtime = runtime;
        }

        @Override
        public void start() {
            started = true;
            startCalls++;
        }

        @Override
        public void stop() {
            started = false;
            stopCalls++;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public int port() {
            return config.getPort();
        }
    }

    @Test
    void testDefaultEngineFallsBackToBuiltin() throws Exception {
        Vostok.Web.init(0).get("/ping", (req, res) -> res.text("ok"));
        Object engine = currentEngine();
        assertNotNull(engine);
        assertTrue(engine instanceof VKBuiltinWebServerEngine);
    }

    @Test
    void testCustomFactoryUsesRuntimeAndDelegatesLifecycle() throws Exception {
        final FakeEngine[] holder = new FakeEngine[1];
        VKWebConfig config = new VKWebConfig()
                .port(18081)
                .serverFactory((cfg, runtime) -> {
                    FakeEngine engine = new FakeEngine(cfg, runtime);
                    holder[0] = engine;
                    return engine;
                });

        Vostok.Web.init(config)
                .use((req, res, chain) -> {
                    res.header("X-Test-MW", "1");
                    chain.next(req, res);
                })
                .get("/ping", (req, res) -> res.text("ok"))
                .health()
                .metrics()
                .websocket("/ws", new VKWebSocketHandler() {})
                .controller(new DemoApi());

        Vostok.Web.start();

        FakeEngine fake = holder[0];
        assertNotNull(fake);
        assertSame(fake, currentEngine());
        assertSame(fake.runtime, currentRuntime());
        assertTrue(fake.isStarted());
        assertEquals(1, fake.startCalls);
        assertEquals(18081, Vostok.Web.port());
        assertNotNull(fake.runtime.router().match("GET", "/ping"));
        assertNotNull(fake.runtime.router().match("GET", "/actuator/health"));
        assertNotNull(fake.runtime.router().match("GET", "/actuator/metrics"));
        assertNotNull(fake.runtime.findWebSocket("/ws"));
        assertNotNull(fake.runtime.router().match("GET", "/api/demo/hello/7"));

        VKRequest req = new VKRequest("GET", "/api/demo/hello/7", "", "HTTP/1.1", Map.of(), new byte[0], true, null);
        var result = fake.runtime.dispatchHttp(req);
        String body = new String(result.response().body(), StandardCharsets.UTF_8);
        assertEquals(200, result.response().status());
        assertEquals("1", result.response().headers().get("X-Test-MW"));
        assertTrue(body.contains("\"statusCode\":200"));
        assertTrue(body.contains("\"id\":7"));

        Vostok.Web.stop();
        assertEquals(1, fake.stopCalls);
        assertTrue(!fake.isStarted());
    }

    private Object currentEngine() throws Exception {
        Field field = VostokWeb.class.getDeclaredField("engine");
        field.setAccessible(true);
        return field.get(null);
    }

    private Object currentRuntime() throws Exception {
        Field field = VostokWeb.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return field.get(null);
    }
}
