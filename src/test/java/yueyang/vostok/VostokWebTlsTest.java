package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.tls.VKTlsConfig;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTPS/TLS 集成测试，使用预生成的自签名证书 test-server.p12。
 * HttpClient 配置为信任该自签名证书以绕过证书验证。
 */
public class VostokWebTlsTest {
    private static final String KEYSTORE_PATH =
            VostokWebTlsTest.class.getResource("/test-server.p12").getPath();
    private static final String KEYSTORE_PASS = "changeit";

    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    /**
     * 构建信任 test-server.p12 自签名证书的 SSLContext（客户端使用）。
     */
    private static SSLContext buildTrustingContext() throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (InputStream in = VostokWebTlsTest.class.getResourceAsStream("/test-server.p12")) {
            ts.load(in, KEYSTORE_PASS.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    @Test
    void testHttpsGet() throws Exception {
        Vostok.Web.init(new VKWebConfig()
                        .port(0)
                        .tls(new VKTlsConfig()
                                .keyStorePath(KEYSTORE_PATH)
                                .keyStorePassword(KEYSTORE_PASS)))
                .get("/hello", (req, res) -> res.text("tls-ok"));
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(buildTrustingContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(new URI("https://127.0.0.1:" + port + "/hello"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, res.statusCode());
        assertEquals("tls-ok", res.body());
    }

    @Test
    void testHttpsPost() throws Exception {
        Vostok.Web.init(new VKWebConfig()
                        .port(0)
                        .tls(new VKTlsConfig()
                                .keyStorePath(KEYSTORE_PATH)
                                .keyStorePassword(KEYSTORE_PASS)))
                .post("/echo", (req, res) -> res.text(req.bodyText()));
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(buildTrustingContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(new URI("https://127.0.0.1:" + port + "/echo"))
                        .POST(HttpRequest.BodyPublishers.ofString("hello-tls"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, res.statusCode());
        assertEquals("hello-tls", res.body());
    }

    @Test
    void testHttpsWebSocket() throws Exception {
        Vostok.Web.init(new VKWebConfig()
                        .port(0)
                        .tls(new VKTlsConfig()
                                .keyStorePath(KEYSTORE_PATH)
                                .keyStorePassword(KEYSTORE_PASS)))
                .websocket("/ws", new yueyang.vostok.web.websocket.VKWebSocketHandler() {
                    @Override
                    public void onText(yueyang.vostok.web.websocket.VKWebSocketSession session, String text) {
                        session.sendText("echo:" + text);
                    }
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        WsListener listener = new WsListener();
        WebSocket ws = HttpClient.newBuilder()
                .sslContext(buildTrustingContext())
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .newWebSocketBuilder()
                .buildAsync(new URI("wss://127.0.0.1:" + port + "/ws"), listener)
                .get(5, TimeUnit.SECONDS);

        ws.sendText("hello", true).join();
        String reply = listener.texts.poll(5, TimeUnit.SECONDS);
        assertEquals("echo:hello", reply);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }

    private static final class WsListener implements WebSocket.Listener {
        final BlockingQueue<String> texts = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                texts.offer(data.toString());
            }
            webSocket.request(1);
            return null;
        }
    }
}
