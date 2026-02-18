package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.websocket.VKWebSocketSession;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokWebSocketTest {
    @AfterEach
    void tearDown() {
        try {
            Vostok.Web.stop();
        } catch (Exception ignore) {
        }
    }

    @Test
    void testTextAndBinaryEcho() throws Exception {
        Vostok.Web.init(0)
                .websocket("/ws", new yueyang.vostok.web.websocket.VKWebSocketHandler() {
                    @Override
                    public void onText(VKWebSocketSession session, String text) {
                        session.sendText("echo:" + text);
                    }

                    @Override
                    public void onBinary(VKWebSocketSession session, byte[] data) {
                        session.sendBinary(data);
                    }
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        EchoListener listener = new EchoListener();
        WebSocket ws = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()
                .newWebSocketBuilder()
                .buildAsync(new URI("ws://127.0.0.1:" + port + "/ws"), listener)
                .get(3, TimeUnit.SECONDS);

        ws.sendText("hi", true).join();
        assertEquals("echo:hi", listener.text.get(3, TimeUnit.SECONDS));

        byte[] payload = new byte[]{1, 2, 3, 4};
        ws.sendBinary(ByteBuffer.wrap(payload), true).join();
        byte[] back = listener.binary.get(3, TimeUnit.SECONDS);
        assertEquals(payload.length, back.length);
        for (int i = 0; i < payload.length; i++) {
            assertEquals(payload[i], back[i]);
        }
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }

    @Test
    void testBadUpgradeRequest() throws Exception {
        Vostok.Web.init(0)
                .websocket("/ws", new yueyang.vostok.web.websocket.VKWebSocketHandler() {
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        try (Socket socket = new Socket("127.0.0.1", port)) {
            String req = "GET /ws HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n";
            socket.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            byte[] buf = new byte[512];
            int n = socket.getInputStream().read(buf);
            String raw = n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.US_ASCII);
            assertTrue(raw.contains("400"));
        }
    }

    private static final class EchoListener implements WebSocket.Listener {
        final CompletableFuture<String> text = new CompletableFuture<>();
        final CompletableFuture<byte[]> binary = new CompletableFuture<>();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                text.complete(data.toString());
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            if (last) {
                byte[] out = new byte[data.remaining()];
                data.get(out);
                binary.complete(out);
            }
            webSocket.request(1);
            return null;
        }
    }
}
