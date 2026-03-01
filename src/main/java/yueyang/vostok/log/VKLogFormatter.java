package yueyang.vostok.log;

import java.util.Map;

/**
 * 可插拔日志格式化器。
 * <p>
 * 实现此接口可完全自定义日志输出格式（如 JSON、plain-text 变体等）。
 * 默认格式：{@code yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [loggerName] {mdc} message}。
 * <p>
 * 示例（JSON 格式）：
 * <pre>{@code
 * VKLogConfig.defaults().formatter((level, loggerName, msg, t, ts, mdc) -> {
 *     StringBuilder sb = new StringBuilder();
 *     sb.append("{\"ts\":").append(ts)
 *       .append(",\"level\":\"").append(level).append("\"")
 *       .append(",\"logger\":\"").append(loggerName).append("\"")
 *       .append(",\"msg\":\"").append(msg).append("\"}\n");
 *     return sb.toString();
 * });
 * }</pre>
 *
 * @see VKLogConfig#formatter(VKLogFormatter)
 * @see VKLogSinkConfig#formatter(VKLogFormatter)
 */
@FunctionalInterface
public interface VKLogFormatter {

    /**
     * 将一条日志事件格式化为字符串。
     *
     * @param level      日志级别
     * @param loggerName Logger 名称（来自命名 Logger 或调用方类名）
     * @param msg        日志消息（已完成 {@code {}} 模板替换）
     * @param t          关联的异常，可为 {@code null}
     * @param ts         事件时间戳（epoch millis）
     * @param mdc        调用线程的 MDC 上下文快照（不可修改），无上下文时为空 {@link Map}
     * @return 要写入文件/控制台的完整字符串（含末尾换行符）
     */
    String format(VKLogLevel level, String loggerName, String msg, Throwable t, long ts, Map<String, String> mdc);
}
