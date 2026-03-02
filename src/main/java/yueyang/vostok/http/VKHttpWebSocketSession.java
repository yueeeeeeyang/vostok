package yueyang.vostok.http;

import java.util.concurrent.CompletableFuture;

/**
 * WebSocket 会话接口，封装与远端的双向通信能力。
 * 通过 {@link VKHttpWebSocketListener#onOpen} 回调获得实例。
 */
public interface VKHttpWebSocketSession {

    /**
     * 异步发送文本消息。
     *
     * @param text 要发送的文本内容，不得为 null
     * @return 发送完成的 Future，失败时 completeExceptionally
     */
    CompletableFuture<Void> sendText(String text);

    /**
     * 异步发送二进制消息。
     *
     * @param data 要发送的字节数组，不得为 null
     * @return 发送完成的 Future，失败时 completeExceptionally
     */
    CompletableFuture<Void> sendBinary(byte[] data);

    /**
     * 使用正常状态码（1000）关闭 WebSocket 连接。
     *
     * @return 关闭完成的 Future
     */
    CompletableFuture<Void> close();

    /**
     * 使用指定状态码和原因关闭 WebSocket 连接。
     *
     * @param statusCode 关闭状态码（1000-4999）
     * @param reason     关闭原因（不超过 123 字节 UTF-8）
     * @return 关闭完成的 Future
     */
    CompletableFuture<Void> close(int statusCode, String reason);

    /**
     * 查询连接是否仍然处于开放状态。
     *
     * @return true 表示连接开放，false 表示已关闭或出现错误
     */
    boolean isOpen();
}
