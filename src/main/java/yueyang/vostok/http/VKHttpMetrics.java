package yueyang.vostok.http;

import java.util.Map;

public record VKHttpMetrics(
        long totalCalls,
        long successCalls,
        long failedCalls,
        long retriedCalls,
        long timeoutCalls,
        long networkErrorCalls,
        long streamOpens,
        long streamCloses,
        long streamErrors,
        long sseEvents,
        long totalCostMs,
        Map<Integer, Long> statusCounts
) {
    /** 返回所有计数均为零的空 Metrics 实例（用于未记录过数据的客户端查询）。 */
    public static VKHttpMetrics empty() {
        return new VKHttpMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
    }
}
