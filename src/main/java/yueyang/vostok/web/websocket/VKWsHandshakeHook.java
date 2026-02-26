package yueyang.vostok.web.websocket;

public interface VKWsHandshakeHook {
    default void beforeUpgrade(VKWsHandshakeContext context) {
    }

    default void afterAuth(VKWebSocketSession session, VKWsHandshakeContext context) {
    }

    default void onReject(VKWsHandshakeContext context, VKWsAuthResult result) {
    }

    static VKWsHandshakeHook noop() {
        return new VKWsHandshakeHook() {
        };
    }
}
