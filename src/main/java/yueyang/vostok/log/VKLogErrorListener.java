package yueyang.vostok.log;

/**
 * ERROR 级别日志事件监听器。
 * <p>
 * 每次向文件/stderr 写入 ERROR 级别日志后，由 worker 线程调用此接口，
 * 可用于接入外部告警系统（如企业微信机器人、PagerDuty、监控平台等）。
 * <p>
 * <b>重要：</b>此接口在日志 worker 线程上调用，实现必须<b>非阻塞、低延迟</b>，
 * 不应执行 IO、网络请求或长时间计算，否则会阻塞日志队列消费。
 * 建议将告警投递到独立的异步队列或线程池中处理。
 * <p>
 * 示例：
 * <pre>{@code
 * VKLogConfig.defaults().errorListener((loggerName, message, error, timestamp) -> {
 *     alertQueue.offer(new AlertEvent(loggerName, message, timestamp));
 * });
 * }</pre>
 *
 * @see VKLogConfig#errorListener(VKLogErrorListener)
 * @see VKLogSinkConfig#errorListener(VKLogErrorListener)
 */
@FunctionalInterface
public interface VKLogErrorListener {

    /**
     * 在 ERROR 级别日志写入完成后被调用。
     *
     * @param loggerName Logger 名称
     * @param message    日志消息
     * @param error      关联的异常，可为 {@code null}
     * @param timestamp  事件时间戳（epoch millis）
     */
    void onError(String loggerName, String message, Throwable error, long timestamp);
}
