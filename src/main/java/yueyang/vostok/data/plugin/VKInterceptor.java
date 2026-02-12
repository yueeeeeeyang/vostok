package yueyang.vostok.data.plugin;

/**
 * SQL 拦截器。
 */
public interface VKInterceptor {
    default void beforeExecute(String sql, Object[] params) {
    }

    default void afterExecute(String sql, Object[] params, long costMs, boolean success, Throwable error) {
    }
}
