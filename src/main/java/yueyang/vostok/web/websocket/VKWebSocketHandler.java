package yueyang.vostok.web.websocket;

public interface VKWebSocketHandler {
    default void onOpen(VKWebSocketSession session) {
    }

    default void onText(VKWebSocketSession session, String text) {
    }

    default void onBinary(VKWebSocketSession session, byte[] data) {
    }

    default void onClose(VKWebSocketSession session, int code, String reason) {
    }

    default void onError(VKWebSocketSession session, Throwable error) {
    }
}
