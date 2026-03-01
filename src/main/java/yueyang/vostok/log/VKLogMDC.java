package yueyang.vostok.log;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapped Diagnostic Context（MDC）—— 线程级诊断上下文。
 * <p>
 * 使用 {@link ThreadLocal} 维护当前线程的键值对上下文。在每条日志事件创建时，
 * MDC 快照被自动捕获并注入 {@link VKLogFormatter}，输出到日志行中（如 requestId、userId）。
 * <p>
 * 典型 Web 请求场景用法：
 * <pre>{@code
 * // 请求入口（拦截器/中间件）
 * VKLogMDC.put("requestId", ctx.requestId());
 * VKLogMDC.put("userId", String.valueOf(ctx.userId()));
 * try {
 *     handle(ctx);
 * } finally {
 *     VKLogMDC.clear();  // 线程归还连接池前务必清除，防止上下文泄漏
 * }
 * }</pre>
 * <p>
 * 跨线程传递 MDC（如提交到线程池）：
 * <pre>{@code
 * Map<String, String> snapshot = VKLogMDC.getAll();
 * executor.submit(() -> {
 *     VKLogMDC.putAll(snapshot);
 *     try { doWork(); } finally { VKLogMDC.clear(); }
 * });
 * }</pre>
 */
public final class VKLogMDC {

    /** 每线程独立的 MDC 上下文，使用 LinkedHashMap 保持插入顺序 */
    private static final ThreadLocal<LinkedHashMap<String, String>> CTX =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private VKLogMDC() {
    }

    /**
     * 设置当前线程 MDC 中的键值对。
     * key 为 {@code null} 时忽略（value 可以为 null）。
     */
    public static void put(String key, String value) {
        if (key == null) {
            return;
        }
        CTX.get().put(key, value);
    }

    /**
     * 批量设置当前线程 MDC（用于跨线程传播场景）。
     * map 为 {@code null} 时忽略。
     */
    public static void putAll(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        CTX.get().putAll(map);
    }

    /**
     * 移除当前线程 MDC 中的指定键。
     */
    public static void remove(String key) {
        CTX.get().remove(key);
    }

    /**
     * 获取当前线程 MDC 中指定键的值，不存在时返回 {@code null}。
     */
    public static String get(String key) {
        return CTX.get().get(key);
    }

    /**
     * 清除当前线程的全部 MDC 上下文。
     * 应在请求处理结束后调用，防止线程复用时上下文泄漏。
     * <p>
     * 使用 {@link ThreadLocal#remove()} 彻底移除 ThreadLocal 绑定，
     * 而非仅清空 Map 内容，允许空 Map 对象被 GC 回收。
     */
    public static void clear() {
        CTX.remove();
    }

    /**
     * 获取当前线程 MDC 的只读视图（有序）。
     * 返回的 Map 为当前快照，不反映后续的 MDC 变更。
     */
    public static Map<String, String> getAll() {
        Map<String, String> m = CTX.get();
        return m.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    /**
     * 在调用线程上对当前 MDC 做深度快照，供 LogEvent 在创建时捕获。
     * 内部方法，仅由日志模块调用。
     * 返回的 Map 是独立副本，与原上下文解耦。
     */
    static Map<String, String> snapshot() {
        Map<String, String> m = CTX.get();
        return m.isEmpty() ? Collections.emptyMap() : new LinkedHashMap<>(m);
    }
}
