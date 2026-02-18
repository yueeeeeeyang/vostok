package yueyang.vostok.web.websocket;

public record VKWebSocketEndpoint(String path, VKWebSocketConfig config, VKWebSocketHandler handler) {
}
