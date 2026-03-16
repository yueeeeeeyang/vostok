package yueyang.vostok.log;

/**
 * Backend 派发模式。
 * <p>
 * 用于控制 {@link VKLogBackend} 参与日志链路的时机：
 * </p>
 * <ul>
 *   <li>{@link #VOSTOK_ASYNC}：沿用 Vostok 现有异步队列与 worker 线程，Backend 在 worker 中被调用。</li>
 *   <li>{@link #DIRECT}：跳过 Vostok 自带异步队列，调用线程直接把事件委托给 Backend，
 *       适合已经具备官方异步能力的外部日志框架（例如 Log4j2 AsyncLogger）。</li>
 * </ul>
 */
public enum VKLogBackendDispatchMode {
    /**
     * Vostok 自己负责异步排队，Backend 仅作为最终输出通道。
     */
    VOSTOK_ASYNC,
    /**
     * 直接委托给外部 Backend，不再经过 Vostok 内建异步队列。
     */
    DIRECT
}
