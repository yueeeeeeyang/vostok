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
}
