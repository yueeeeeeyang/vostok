package yueyang.vostok.log;

/**
 * 命名 Logger 接口，支持级别查询与模板格式化日志。
 * <p>
 * 通过 {@code VostokLog.logger("name")} 或 {@code VostokLog.getLogger("name")} 获取实例。
 * 同名 logger 返回相同实例（缓存复用）。
 * <p>
 * 建议在高频日志路径中先调用 {@code isXxxEnabled()} 进行防护，
 * 避免在级别禁用时执行昂贵的参数计算：
 * <pre>{@code
 * if (logger.isDebugEnabled()) {
 *     logger.debug("state: {}", expensiveState());
 * }
 * }</pre>
 */
public interface VKLogger {

    /** 返回此 Logger 的名称。 */
    String name();

    /** 当前配置是否允许输出 TRACE 级别日志。 */
    boolean isTraceEnabled();

    /** 当前配置是否允许输出 DEBUG 级别日志。 */
    boolean isDebugEnabled();

    /** 当前配置是否允许输出 INFO 级别日志。 */
    boolean isInfoEnabled();

    /** 当前配置是否允许输出 WARN 级别日志。 */
    boolean isWarnEnabled();

    /** 当前配置是否允许输出 ERROR 级别日志。 */
    boolean isErrorEnabled();

    void trace(String msg);

    void debug(String msg);

    void info(String msg);

    void warn(String msg);

    void error(String msg);

    void error(String msg, Throwable t);

    void trace(String template, Object... args);

    void debug(String template, Object... args);

    void info(String template, Object... args);

    void warn(String template, Object... args);

    void error(String template, Object... args);
}
