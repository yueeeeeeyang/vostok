package yueyang.vostok.log;

import java.util.Map;

/**
 * 日志输出 Backend SPI。
 * <p>
 * 实现此接口可将 Vostok 的日志输出委托给任意外部框架（SLF4J、Log4j2、Logback 等）。
 * 配置到 {@link VKLogConfig#backend(VKLogBackend)} 后，{@link AsyncEngine} 在写入时
 * 调用 {@link #emit}，内置文件写入与控制台输出被跳过；{@link VKLogErrorListener} 仍然正常触发。
 * <p>
 * <b>约束</b>：{@link #emit} 在 AsyncEngine worker 线程调用，<b>必须非阻塞、无 IO</b>，
 * 可调用外部框架的异步 API 或投递到独立队列。
 * <p>
 * 如需同时保留内置文件写入，可自行实现组合 Backend，在 {@link #emit} 中依次分发。
 *
 * @see VKLogConfig#backend(VKLogBackend)
 * @see VKLogSinkConfig#backend(VKLogBackend)
 */
public interface VKLogBackend {

    /**
     * 输出一条日志事件。在 AsyncEngine worker 线程调用，必须非阻塞。
     *
     * @param level        日志级别
     * @param loggerName   Logger 名称（命名 Logger 名 或 调用方类名）
     * @param formattedMsg 已完成 {@code {}} 替换的消息（格式由 {@link VKLogFormatter} 控制，
     *                     是否含异常栈取决于所配置的 formatter）
     * @param t            关联异常，可为 {@code null}
     * @param tsMillis     事件时间戳（epoch millis）
     * @param mdc          调用线程的 MDC 快照（不可修改），无上下文时为空 {@link Map}
     */
    void emit(VKLogLevel level, String loggerName,
              String formattedMsg, Throwable t, long tsMillis,
              Map<String, String> mdc);

    /**
     * 引擎启动时调用（在 worker 线程启动前）。可在此建立连接、分配资源。默认空实现。
     */
    default void start() {}

    /**
     * 引擎关闭时调用（在 worker 线程退出后）。可在此释放资源。默认空实现。
     */
    default void stop() {}
}
