package yueyang.vostok.log;

public interface VKLogger {
    String name();

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
