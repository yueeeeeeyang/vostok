package yueyang.vostok.http.core;

import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.Set;

record ResolvedRequest(
        String clientName,
        String method,
        String url,
        Map<String, String> headers,
        ClientRuntimeEntry runtime,
        ResilienceRuntime resilience,
        HttpRequest httpRequest,
        ResolvedPolicy policy
) {
}

record ResolvedPolicy(
        int maxRetries,
        Set<Integer> retryOnStatuses,
        Set<String> retryMethods,
        boolean retryOnNetworkError,
        boolean retryOnTimeout,
        boolean respectRetryAfter,
        long retryBackoffBaseMs,
        long retryBackoffMaxMs,
        boolean retryJitterEnabled,
        long maxRetryDelayMs,
        boolean failOnNon2xx,
        long maxResponseBodyBytes,
        long readTimeoutMs,
        boolean requireIdempotencyKeyForUnsafeRetry,
        String idempotencyKeyHeader,
        boolean rateLimitEnabled,
        int rateLimitQps,
        int rateLimitBurst,
        boolean circuitEnabled,
        int circuitWindowSize,
        int circuitMinCalls,
        int circuitFailureRateThreshold,
        long circuitOpenWaitMs,
        int circuitHalfOpenMaxCalls,
        Set<Integer> circuitRecordStatuses,
        boolean bulkheadEnabled,
        int bulkheadMaxConcurrent,
        int bulkheadQueueSize,
        long bulkheadAcquireTimeoutMs,
        boolean streamEnabled,
        long streamIdleTimeoutMs,
        long streamTotalTimeoutMs,
        int sseMaxEventBytes,
        boolean sseEmitDoneEvent,
        int streamQueueCapacity
) {
    String resilienceKey() {
        return rateLimitEnabled + ":" + rateLimitQps + ":" + rateLimitBurst + "|"
                + circuitEnabled + ":" + circuitWindowSize + ":" + circuitMinCalls + ":"
                + circuitFailureRateThreshold + ":" + circuitOpenWaitMs + ":" + circuitHalfOpenMaxCalls + ":"
                + circuitRecordStatuses.hashCode() + "|"
                + bulkheadEnabled + ":" + bulkheadMaxConcurrent + ":" + bulkheadQueueSize + ":" + bulkheadAcquireTimeoutMs;
    }
}

final class ClientRuntimeEntry {
    final HttpClient client;
    /** 扩展2：Cookie 管理器（启用 cookiePolicy 时注入，否则为 null）。 */
    final CookieManager cookieManager;
    volatile long lastUsedAt;

    ClientRuntimeEntry(HttpClient client, CookieManager cookieManager, long lastUsedAt) {
        this.client = client;
        this.cookieManager = cookieManager;
        this.lastUsedAt = lastUsedAt;
    }
}
