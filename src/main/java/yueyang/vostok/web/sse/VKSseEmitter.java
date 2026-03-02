package yueyang.vostok.web.sse;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Server-Sent Events 发射器，用于向已建立的 SSE 连接推送事件。
 *
 * 线程安全：send/close 可在任意线程调用。
 * sender 通过 reactor.pending 队列投递到 reactor 线程，确保 I/O 操作在单线程内执行。
 * closer 通过 reactor.requestClose() 投递，同样线程安全。
 *
 * SSE 事件格式（RFC 8895）：
 * <pre>
 *   id: optional-id\n
 *   event: optional-event\n
 *   data: line1\n
 *   data: line2\n
 *   \n
 * </pre>
 */
public final class VKSseEmitter {
    /** 加密输出回调：将 SSE 事件字节投递到 reactor 的写队列。 */
    private final Consumer<byte[]> sender;
    /** 关闭连接回调：通过 reactor 的 pending 队列安全地关闭连接。 */
    private final Runnable closer;
    /** 连接是否已关闭（volatile 保证跨线程可见性）。 */
    private volatile boolean closed = false;

    /**
     * 创建 SSE 发射器。
     *
     * @param sender 字节发送回调，由 reactor 内部提供（线程安全投递到写队列）
     * @param closer 连接关闭回调，由 reactor 内部提供（线程安全）
     */
    public VKSseEmitter(Consumer<byte[]> sender, Runnable closer) {
        this.sender = sender;
        this.closer = closer;
    }

    /**
     * 推送仅含 data 字段的 SSE 事件。
     *
     * @param data 事件数据内容
     */
    public void send(String data) {
        send(null, data, null);
    }

    /**
     * 推送带 event 和 data 字段的 SSE 事件。
     *
     * @param event 事件类型名
     * @param data  事件数据内容
     */
    public void send(String event, String data) {
        send(event, data, null);
    }

    /**
     * 推送完整的 SSE 事件（可选 id、event、data 字段）。
     * 多行 data 会自动按行分割，每行独立写入 "data: ..." 字段。
     *
     * @param event 事件类型名（null 或空则省略 event 字段）
     * @param data  事件数据内容（null 视为空字符串）
     * @param id    事件 ID（null 或空则省略 id 字段）
     */
    public void send(String event, String data, String id) {
        if (closed) {
            return;
        }
        StringBuilder sb = new StringBuilder(64);
        if (id != null && !id.isEmpty()) {
            sb.append("id: ").append(id).append('\n');
        }
        if (event != null && !event.isEmpty()) {
            sb.append("event: ").append(event).append('\n');
        }
        // 多行 data 分拆处理：每行都需要 "data: " 前缀
        String dataStr = data == null ? "" : data;
        String[] lines = dataStr.split("\n", -1);
        for (String line : lines) {
            sb.append("data: ").append(line).append('\n');
        }
        // 事件以空行结束
        sb.append('\n');

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try {
            sender.accept(bytes);
        } catch (Throwable ignore) {
            // sender 投递失败（连接已断开），忽略异常
        }
    }

    /**
     * 主动关闭 SSE 连接。
     * 幂等：多次调用只执行一次关闭。
     */
    public void close() {
        if (!closed) {
            closed = true;
            try {
                closer.run();
            } catch (Throwable ignore) {
            }
        }
    }

    /**
     * 检查 SSE 连接是否仍处于开放状态。
     *
     * @return true 表示连接正常，可以继续发送事件
     */
    public boolean isOpen() {
        return !closed;
    }

    /**
     * 由 reactor 在连接关闭时调用，标记 emitter 为已关闭。
     * public 以允许 reactor 包（跨包）调用。
     */
    public void markClosed() {
        closed = true;
    }
}
