package yueyang.vostok.web.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * Web 服务器运行时指标收集类。
 *
 * 由 VKWebServer 持有单例，VKReactor 在每次请求完成后更新计数。
 * 提供两个内置端点的 JSON 序列化方法：
 * - toHealthJson: /actuator/health 响应
 * - toMetricsJson: /actuator/metrics 响应
 *
 * 所有计数器均为原子操作，线程安全。
 */
public final class VKMetrics {
    /** 历史总请求数（包括错误请求）。 */
    final AtomicLong totalRequests = new AtomicLong();
    /** 历史总错误请求数（handler 抛出异常或 5xx 响应）。 */
    final AtomicLong totalErrors = new AtomicLong();
    /** 历史总响应时间（纳秒），用于计算平均响应时间。 */
    final AtomicLong totalResponseNs = new AtomicLong();

    /**
     * 当前活跃连接数的动态回调，由 VKWebServer 注入。
     * 使用 IntSupplier 而非快照，确保每次查询都是实时值。
     */
    private volatile IntSupplier activeConnectionsSupplier;

    /**
     * 注入活跃连接数获取器，由 VKWebServer 在初始化时调用。
     */
    void setActiveConnectionsSupplier(IntSupplier supplier) {
        this.activeConnectionsSupplier = supplier;
    }

    /** 获取历史总请求数。 */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /** 获取历史总错误数。 */
    public long getTotalErrors() {
        return totalErrors.get();
    }

    /** 获取历史总响应时间（纳秒）。 */
    public long getTotalResponseNs() {
        return totalResponseNs.get();
    }

    /** 获取当前活跃连接数。 */
    public int getActiveConnections() {
        IntSupplier s = activeConnectionsSupplier;
        return s == null ? 0 : s.getAsInt();
    }

    /**
     * 计算平均响应时间（毫秒）。
     * totalRequests 为 0 时返回 0.0，避免除零。
     */
    public double getAvgResponseMs() {
        long reqs = totalRequests.get();
        if (reqs == 0) {
            return 0.0;
        }
        return (totalResponseNs.get() / 1_000_000.0) / reqs;
    }

    /**
     * 序列化为 /actuator/health 端点的 JSON 响应。
     * 格式：{"status":"UP","connections":42}
     */
    public String toHealthJson() {
        return "{\"status\":\"UP\",\"connections\":" + getActiveConnections() + "}";
    }

    /**
     * 序列化为 /actuator/metrics 端点的 JSON 响应。
     * 格式：{"requests":1000,"errors":2,"activeConnections":42,"avgResponseMs":5.2}
     */
    public String toMetricsJson() {
        return "{\"requests\":" + totalRequests.get()
                + ",\"errors\":" + totalErrors.get()
                + ",\"activeConnections\":" + getActiveConnections()
                + ",\"avgResponseMs\":" + String.format("%.1f", getAvgResponseMs())
                + "}";
    }
}
