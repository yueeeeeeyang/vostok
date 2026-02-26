package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.web.websocket.VKWsAuthResult;
import yueyang.vostok.web.websocket.VKWebSocketConfig;
import yueyang.vostok.web.websocket.VKWebSocketSession;

import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void testRoomGroupBroadcastAndSessionAttributes() throws Exception {
        Vostok.Web.init(0)
                .websocket("/ws-room", new VKWebSocketConfig()
                        .handshakeAuthenticator(ctx -> {
                            String token = ctx.queryParam("token");
                            if (!"ok".equals(token)) {
                                return VKWsAuthResult.reject(403, "Forbidden");
                            }
                            return VKWsAuthResult.allow(Map.of(
                                    "uid", ctx.queryParam("uid"),
                                    "grp", ctx.queryParam("grp")
                            ));
                        }), new yueyang.vostok.web.websocket.VKWebSocketHandler() {
                    @Override
                    public void onOpen(VKWebSocketSession session) {
                        session.joinRoom("room-1");
                        String grp = session.getAttribute("grp", String.class);
                        if (grp != null) {
                            session.joinGroup(grp);
                        }
                        session.sendText("open:" + session.getAttribute("uid", String.class));
                    }

                    @Override
                    public void onText(VKWebSocketSession session, String text) {
                        session.setAttribute("last", text);
                        session.sendText("echo:" + session.getAttribute("uid", String.class)
                                + ":" + session.getAttribute("last", String.class));
                    }
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        QueueListener l1 = new QueueListener();
        QueueListener l2 = new QueueListener();

        WebSocket ws1 = client.newWebSocketBuilder()
                .buildAsync(new URI("ws://127.0.0.1:" + port + "/ws-room?token=ok&uid=u1&grp=g1"), l1)
                .get(3, TimeUnit.SECONDS);
        WebSocket ws2 = client.newWebSocketBuilder()
                .buildAsync(new URI("ws://127.0.0.1:" + port + "/ws-room?token=ok&uid=u2&grp=g2"), l2)
                .get(3, TimeUnit.SECONDS);

        assertEquals("open:u1", l1.awaitText(3));
        assertEquals("open:u2", l2.awaitText(3));

        ws1.sendText("hello", true).join();
        assertEquals("echo:u1:hello", l1.awaitText(3));

        int roomSent = Vostok.Web.websocketBroadcastRoom("/ws-room", "room-1", "room-msg");
        assertEquals(2, roomSent);
        assertEquals("room-msg", l1.awaitText(3));
        assertEquals("room-msg", l2.awaitText(3));

        int groupSent = Vostok.Web.websocketBroadcastGroup("/ws-room", "g1", "group-msg");
        assertEquals(1, groupSent);
        assertEquals("group-msg", l1.awaitText(3));

        int rgSent = Vostok.Web.websocketBroadcastRoomAndGroup("/ws-room", "room-1", "g2", "rg-msg");
        assertEquals(1, rgSent);
        assertEquals("rg-msg", l2.awaitText(3));

        ws1.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        ws2.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }

    @Test
    void testHandshakeAuthReject() throws Exception {
        Vostok.Web.init(0)
                .websocket("/ws-auth", new VKWebSocketConfig()
                        .handshakeAuthenticator(ctx -> {
                            if (!"ok".equals(ctx.queryParam("token"))) {
                                return VKWsAuthResult.reject(403, "Forbidden");
                            }
                            return VKWsAuthResult.allow();
                        }), new yueyang.vostok.web.websocket.VKWebSocketHandler() {
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        QueueListener listener = new QueueListener();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build()
                        .newWebSocketBuilder()
                        .buildAsync(new URI("ws://127.0.0.1:" + port + "/ws-auth?token=bad"), listener)
                        .get(3, TimeUnit.SECONDS));
        assertInstanceOf(WebSocketHandshakeException.class, ex.getCause());
        WebSocketHandshakeException hs = (WebSocketHandshakeException) ex.getCause();
        assertEquals(403, hs.getResponse().statusCode());
    }

    @Test
    void testHandshakeHookCallbacks() throws Exception {
        AtomicBoolean beforeCalled = new AtomicBoolean(false);
        AtomicBoolean afterCalled = new AtomicBoolean(false);
        AtomicBoolean rejectCalled = new AtomicBoolean(false);

        Vostok.Web.init(0)
                .websocket("/ws-hook", new VKWebSocketConfig()
                        .handshakeHook(new yueyang.vostok.web.websocket.VKWsHandshakeHook() {
                            @Override
                            public void beforeUpgrade(yueyang.vostok.web.websocket.VKWsHandshakeContext context) {
                                if ("/ws-hook".equals(context.path())) {
                                    beforeCalled.set(true);
                                }
                            }

                            @Override
                            public void afterAuth(VKWebSocketSession session, yueyang.vostok.web.websocket.VKWsHandshakeContext context) {
                                session.setAttribute("hook", "ok");
                                afterCalled.set(true);
                            }

                            @Override
                            public void onReject(yueyang.vostok.web.websocket.VKWsHandshakeContext context,
                                                 yueyang.vostok.web.websocket.VKWsAuthResult result) {
                                rejectCalled.set(true);
                            }
                        })
                        .handshakeAuthenticator(ctx -> {
                            if ("deny".equals(ctx.queryParam("token"))) {
                                return VKWsAuthResult.reject(401, "Denied");
                            }
                            return VKWsAuthResult.allow(Map.of("uid", "u-hook"));
                        }), new yueyang.vostok.web.websocket.VKWebSocketHandler() {
                    @Override
                    public void onOpen(VKWebSocketSession session) {
                        session.sendText(session.getAttribute("hook", String.class)
                                + ":" + session.getAttribute("uid", String.class));
                    }
                });
        Vostok.Web.start();
        int port = Vostok.Web.port();

        QueueListener listener = new QueueListener();
        WebSocket ws = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build()
                .newWebSocketBuilder()
                .buildAsync(new URI("ws://127.0.0.1:" + port + "/ws-hook?token=ok"), listener)
                .get(3, TimeUnit.SECONDS);
        assertEquals("ok:u-hook", listener.awaitText(3));
        assertTrue(beforeCalled.get());
        assertTrue(afterCalled.get());
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();

        QueueListener rejectListener = new QueueListener();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build()
                        .newWebSocketBuilder()
                        .buildAsync(new URI("ws://127.0.0.1:" + port + "/ws-hook?token=deny"), rejectListener)
                        .get(3, TimeUnit.SECONDS));
        assertInstanceOf(WebSocketHandshakeException.class, ex.getCause());
        assertTrue(rejectCalled.get());
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

    private static final class QueueListener implements WebSocket.Listener {
        private final BlockingQueue<String> texts = new LinkedBlockingQueue<>();

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

        String awaitText(int seconds) throws InterruptedException {
            String value = texts.poll(seconds, TimeUnit.SECONDS);
            if (value == null) {
                throw new AssertionError("Timeout waiting websocket text");
            }
            return value;
        }
    }
}
