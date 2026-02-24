package yueyang.vostok.ai;

import java.util.Map;

public record VKAiMetrics(
        long totalCalls,
        long successCalls,
        long failedCalls,
        long retriedCalls,
        long timeoutCalls,
        long networkErrorCalls,
        long totalCostMs,
        long totalPromptTokens,
        long totalCompletionTokens,
        long totalTokens,
        Map<Integer, Long> statusCounts
) {
}
