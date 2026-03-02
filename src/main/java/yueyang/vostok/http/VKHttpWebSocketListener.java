package yueyang.vostok.http;

/**
 * WebSocket 事件监听器接口。
 * 所有方法均为 default 方法，用户只需实现感兴趣的事件，未覆写的方法为空实现。
 */
public interface VKHttpWebSocketListener {

    /**
     * WebSocket 连接建立成功后回调。
     *
     * @param session 当前 WebSocket 会话，可用于发送消息
     */
    default void onOpen(VKHttpWebSocketSession session) {
    }

    /**
     * 收到完整文本消息后回调（已累积所有分片）。
     *
     * @param session 当前 WebSocket 会话
     * @param text    完整的文本消息内容
     */
    default void onMessage(VKHttpWebSocketSession session, String text) {
    }

    /**
     * 收到完整二进制消息后回调（已累积所有分片）。
     *
     * @param session 当前 WebSocket 会话
     * @param data    完整的二进制消息字节
     */
    default void onBinary(VKHttpWebSocketSession session, byte[] data) {
    }

    /**
     * WebSocket 连接关闭后回调。
     *
     * @param session    当前 WebSocket 会话
     * @param statusCode 关闭状态码（如 1000 表示正常关闭）
     * @param reason     关闭原因描述
     */
    default void onClose(VKHttpWebSocketSession session, int statusCode, String reason) {
    }

    /**
     * 发生错误时回调（连接错误或协议错误）。
     *
     * @param session 当前 WebSocket 会话
     * @param error   错误原因
     */
    default void onError(VKHttpWebSocketSession session, Throwable error) {
    }
}
