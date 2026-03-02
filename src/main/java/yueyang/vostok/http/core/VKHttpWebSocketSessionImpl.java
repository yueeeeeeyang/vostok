package yueyang.vostok.http.core;

import yueyang.vostok.http.VKHttpWebSocketListener;
import yueyang.vostok.http.VKHttpWebSocketSession;

import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VKHttpWebSocketSession 的内部实现，包装 {@link java.net.http.WebSocket}。
 * <p>
 * 职责：
 * 1. 实现 WebSocket.Listener，接收来自 JDK WebSocket 的各类事件
 * 2. 累积文本/二进制分片，组装完整消息后再回调用户监听器
 * 3. 提供 sendText/sendBinary/close 等操作
 * <p>
 * 与 VKHttpRuntime 的协作：由 websocket() 方法创建并注入到 JDK buildAsync() 中
 */
final class VKHttpWebSocketSessionImpl implements VKHttpWebSocketSession, WebSocket.Listener {

    private final VKHttpWebSocketListener listener;
    private final AtomicBoolean open = new AtomicBoolean(true);
    // 当前底层 JDK WebSocket 句柄，由 onOpen 注入
    private volatile WebSocket webSocket;

    // 累积文本分片（WebSocket 允许分片传输）
    private final StringBuilder textAccumulator = new StringBuilder();
    // 累积二进制分片
    private ByteArrayOutputStream binaryAccumulator = new ByteArrayOutputStream();

    VKHttpWebSocketSessionImpl(VKHttpWebSocketListener listener) {
        this.listener = listener;
    }

    // -----------------------------------------------------------------------
    // VKHttpWebSocketSession 实现
    // -----------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> sendText(String text) {
        WebSocket ws = webSocket;
        if (ws == null || !open.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket is not open"));
        }
        return ws.sendText(text, true).thenApply(w -> null);
    }

    @Override
    public CompletableFuture<Void> sendBinary(byte[] data) {
        WebSocket ws = webSocket;
        if (ws == null || !open.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("WebSocket is not open"));
        }
        return ws.sendBinary(ByteBuffer.wrap(data), true).thenApply(w -> null);
    }

    @Override
    public CompletableFuture<Void> close() {
        return close(WebSocket.NORMAL_CLOSURE, "");
    }

    @Override
    public CompletableFuture<Void> close(int statusCode, String reason) {
        if (!open.get()) {
            return CompletableFuture.completedFuture(null);
        }
        WebSocket ws = webSocket;
        if (ws == null) {
            return CompletableFuture.completedFuture(null);
        }
        return ws.sendClose(statusCode, reason == null ? "" : reason).thenApply(w -> null);
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    // -----------------------------------------------------------------------
    // WebSocket.Listener 实现（JDK 回调）
    // -----------------------------------------------------------------------

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        try {
            listener.onOpen(this);
        } catch (Throwable t) {
            // 忽略监听器异常，避免影响 WebSocket 连接
        }
        // 请求接收第 1 条消息；后续每次处理完再 request(1) 实现流量控制
        webSocket.request(1);
    }

    @Override
    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        // 累积分片
        textAccumulator.append(data);
        if (last) {
            // 分片全部到达，组装完整消息后回调用户
            String text = textAccumulator.toString();
            textAccumulator.setLength(0);
            try {
                listener.onMessage(this, text);
            } catch (Throwable t) {
                // 忽略
            }
        }
        // 请求下一条消息
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletableFuture<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        // 提取字节并累积
        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        binaryAccumulator.writeBytes(bytes);
        if (last) {
            byte[] full = binaryAccumulator.toByteArray();
            binaryAccumulator = new ByteArrayOutputStream();
            try {
                listener.onBinary(this, full);
            } catch (Throwable t) {
                // 忽略
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        open.set(false);
        try {
            listener.onClose(this, statusCode, reason);
        } catch (Throwable t) {
            // 忽略
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open.set(false);
        try {
            listener.onError(this, error);
        } catch (Throwable t) {
            // 忽略
        }
    }
}
