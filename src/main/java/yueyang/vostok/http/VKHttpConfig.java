package yueyang.vostok.http;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class VKHttpConfig {
    private long connectTimeoutMs = 3000;
    private long totalTimeoutMs = 10_000;
    private long readTimeoutMs = 0;
    private int maxRetries = 1;
    private long retryBackoffBaseMs = 100;
    private long retryBackoffMaxMs = 1000;
    private long maxRetryDelayMs = 30_000;
    private boolean retryJitterEnabled = true;
    private boolean retryOnNetworkError = true;
    private boolean retryOnTimeout = true;
    private boolean respectRetryAfter = true;
    private boolean requireIdempotencyKeyForUnsafeRetry = true;
    private String idempotencyKeyHeader = "Idempotency-Key";
    private Set<Integer> retryOnStatuses = new LinkedHashSet<>(Set.of(429, 502, 503, 504));
    private Set<String> retryMethods = new LinkedHashSet<>(Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE"));
    private boolean failOnNon2xx = true;
    private boolean followRedirects = true;
    private boolean logEnabled = true;
    private boolean metricsEnabled = true;
    private long maxResponseBodyBytes = 8L * 1024 * 1024;
    private long clientReuseIdleEvictMs = 10 * 60 * 1000L;
    private int rateLimitQps = 0;
    private int rateLimitBurst = 0;
    private boolean circuitEnabled = false;
    private int circuitWindowSize = 20;
    private int circuitMinCalls = 10;
    private int circuitFailureRateThreshold = 50;
    private long circuitOpenWaitMs = 5000;
    private int circuitHalfOpenMaxCalls = 3;
    private Set<Integer> circuitRecordStatuses = new LinkedHashSet<>(Set.of(429, 500, 502, 503, 504));
    private boolean bulkheadEnabled = false;
    private int bulkheadMaxConcurrent = 100;
    private int bulkheadQueueSize = 0;
    private long bulkheadAcquireTimeoutMs = 0;
    private String userAgent = "VostokHttp/1.0";
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();

    public VKHttpConfig copy() {
        VKHttpConfig c = new VKHttpConfig();
        c.connectTimeoutMs = this.connectTimeoutMs;
        c.totalTimeoutMs = this.totalTimeoutMs;
        c.readTimeoutMs = this.readTimeoutMs;
        c.maxRetries = this.maxRetries;
        c.retryBackoffBaseMs = this.retryBackoffBaseMs;
        c.retryBackoffMaxMs = this.retryBackoffMaxMs;
        c.maxRetryDelayMs = this.maxRetryDelayMs;
        c.retryJitterEnabled = this.retryJitterEnabled;
        c.retryOnNetworkError = this.retryOnNetworkError;
        c.retryOnTimeout = this.retryOnTimeout;
        c.respectRetryAfter = this.respectRetryAfter;
        c.requireIdempotencyKeyForUnsafeRetry = this.requireIdempotencyKeyForUnsafeRetry;
        c.idempotencyKeyHeader = this.idempotencyKeyHeader;
        c.retryOnStatuses = new LinkedHashSet<>(this.retryOnStatuses);
        c.retryMethods = new LinkedHashSet<>(this.retryMethods);
        c.failOnNon2xx = this.failOnNon2xx;
        c.followRedirects = this.followRedirects;
        c.logEnabled = this.logEnabled;
        c.metricsEnabled = this.metricsEnabled;
        c.maxResponseBodyBytes = this.maxResponseBodyBytes;
        c.clientReuseIdleEvictMs = this.clientReuseIdleEvictMs;
        c.rateLimitQps = this.rateLimitQps;
        c.rateLimitBurst = this.rateLimitBurst;
        c.circuitEnabled = this.circuitEnabled;
        c.circuitWindowSize = this.circuitWindowSize;
        c.circuitMinCalls = this.circuitMinCalls;
        c.circuitFailureRateThreshold = this.circuitFailureRateThreshold;
        c.circuitOpenWaitMs = this.circuitOpenWaitMs;
        c.circuitHalfOpenMaxCalls = this.circuitHalfOpenMaxCalls;
        c.circuitRecordStatuses = new LinkedHashSet<>(this.circuitRecordStatuses);
        c.bulkheadEnabled = this.bulkheadEnabled;
        c.bulkheadMaxConcurrent = this.bulkheadMaxConcurrent;
        c.bulkheadQueueSize = this.bulkheadQueueSize;
        c.bulkheadAcquireTimeoutMs = this.bulkheadAcquireTimeoutMs;
        c.userAgent = this.userAgent;
        c.defaultHeaders = new LinkedHashMap<>(this.defaultHeaders);
        return c;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKHttpConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
        return this;
    }

    public long getTotalTimeoutMs() {
        return totalTimeoutMs;
    }

    public VKHttpConfig totalTimeoutMs(long totalTimeoutMs) {
        this.totalTimeoutMs = Math.max(1, totalTimeoutMs);
        return this;
    }

    public long getRequestTimeoutMs() {
        return totalTimeoutMs;
    }

    public VKHttpConfig requestTimeoutMs(long requestTimeoutMs) {
        return totalTimeoutMs(requestTimeoutMs);
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKHttpConfig readTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = Math.max(0, readTimeoutMs);
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKHttpConfig maxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
        return this;
    }

    public long getRetryBackoffBaseMs() {
        return retryBackoffBaseMs;
    }

    public VKHttpConfig retryBackoffBaseMs(long retryBackoffBaseMs) {
        this.retryBackoffBaseMs = Math.max(1, retryBackoffBaseMs);
        return this;
    }

    public long getRetryBackoffMaxMs() {
        return retryBackoffMaxMs;
    }

    public VKHttpConfig retryBackoffMaxMs(long retryBackoffMaxMs) {
        this.retryBackoffMaxMs = Math.max(1, retryBackoffMaxMs);
        return this;
    }

    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public VKHttpConfig maxRetryDelayMs(long maxRetryDelayMs) {
        this.maxRetryDelayMs = Math.max(1, maxRetryDelayMs);
        return this;
    }

    public boolean isRetryJitterEnabled() {
        return retryJitterEnabled;
    }

    public VKHttpConfig retryJitterEnabled(boolean retryJitterEnabled) {
        this.retryJitterEnabled = retryJitterEnabled;
        return this;
    }

    public boolean isRetryOnNetworkError() {
        return retryOnNetworkError;
    }

    public VKHttpConfig retryOnNetworkError(boolean retryOnNetworkError) {
        this.retryOnNetworkError = retryOnNetworkError;
        return this;
    }

    public boolean isRetryOnTimeout() {
        return retryOnTimeout;
    }

    public VKHttpConfig retryOnTimeout(boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
        return this;
    }

    public boolean isRespectRetryAfter() {
        return respectRetryAfter;
    }

    public VKHttpConfig respectRetryAfter(boolean respectRetryAfter) {
        this.respectRetryAfter = respectRetryAfter;
        return this;
    }

    public boolean isRequireIdempotencyKeyForUnsafeRetry() {
        return requireIdempotencyKeyForUnsafeRetry;
    }

    public VKHttpConfig requireIdempotencyKeyForUnsafeRetry(boolean requireIdempotencyKeyForUnsafeRetry) {
        this.requireIdempotencyKeyForUnsafeRetry = requireIdempotencyKeyForUnsafeRetry;
        return this;
    }

    public String getIdempotencyKeyHeader() {
        return idempotencyKeyHeader;
    }

    public VKHttpConfig idempotencyKeyHeader(String idempotencyKeyHeader) {
        if (idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()) {
            this.idempotencyKeyHeader = idempotencyKeyHeader.trim();
        }
        return this;
    }

    public Set<Integer> getRetryOnStatuses() {
        return Set.copyOf(retryOnStatuses);
    }

    public VKHttpConfig retryOnStatuses(Set<Integer> retryOnStatuses) {
        this.retryOnStatuses = retryOnStatuses == null ? new LinkedHashSet<>() : new LinkedHashSet<>(retryOnStatuses);
        return this;
    }

    public VKHttpConfig retryOnStatuses(Integer... retryOnStatuses) {
        this.retryOnStatuses = new LinkedHashSet<>();
        if (retryOnStatuses != null) {
            for (Integer s : retryOnStatuses) {
                if (s != null) {
                    this.retryOnStatuses.add(s);
                }
            }
        }
        return this;
    }

    public Set<String> getRetryMethods() {
        return Set.copyOf(retryMethods);
    }

    public VKHttpConfig retryMethods(Set<String> retryMethods) {
        this.retryMethods = normalizeMethods(retryMethods);
        return this;
    }

    public VKHttpConfig retryMethods(String... retryMethods) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (retryMethods != null) {
            for (String m : retryMethods) {
                if (m != null && !m.isBlank()) {
                    set.add(m.trim().toUpperCase());
                }
            }
        }
        this.retryMethods = set;
        return this;
    }

    public boolean isFailOnNon2xx() {
        return failOnNon2xx;
    }

    public VKHttpConfig failOnNon2xx(boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public VKHttpConfig followRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public VKHttpConfig logEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
        return this;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public VKHttpConfig metricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public long getMaxResponseBodyBytes() {
        return maxResponseBodyBytes;
    }

    public VKHttpConfig maxResponseBodyBytes(long maxResponseBodyBytes) {
        this.maxResponseBodyBytes = Math.max(1024, maxResponseBodyBytes);
        return this;
    }

    public long getClientReuseIdleEvictMs() {
        return clientReuseIdleEvictMs;
    }

    public VKHttpConfig clientReuseIdleEvictMs(long clientReuseIdleEvictMs) {
        this.clientReuseIdleEvictMs = Math.max(1000, clientReuseIdleEvictMs);
        return this;
    }

    public int getRateLimitQps() {
        return rateLimitQps;
    }

    public VKHttpConfig rateLimitQps(int rateLimitQps) {
        this.rateLimitQps = Math.max(0, rateLimitQps);
        return this;
    }

    public int getRateLimitBurst() {
        return rateLimitBurst;
    }

    public VKHttpConfig rateLimitBurst(int rateLimitBurst) {
        this.rateLimitBurst = Math.max(0, rateLimitBurst);
        return this;
    }

    public boolean isCircuitEnabled() {
        return circuitEnabled;
    }

    public VKHttpConfig circuitEnabled(boolean circuitEnabled) {
        this.circuitEnabled = circuitEnabled;
        return this;
    }

    public int getCircuitWindowSize() {
        return circuitWindowSize;
    }

    public VKHttpConfig circuitWindowSize(int circuitWindowSize) {
        this.circuitWindowSize = Math.max(1, circuitWindowSize);
        return this;
    }

    public int getCircuitMinCalls() {
        return circuitMinCalls;
    }

    public VKHttpConfig circuitMinCalls(int circuitMinCalls) {
        this.circuitMinCalls = Math.max(1, circuitMinCalls);
        return this;
    }

    public int getCircuitFailureRateThreshold() {
        return circuitFailureRateThreshold;
    }

    public VKHttpConfig circuitFailureRateThreshold(int circuitFailureRateThreshold) {
        this.circuitFailureRateThreshold = Math.min(100, Math.max(1, circuitFailureRateThreshold));
        return this;
    }

    public long getCircuitOpenWaitMs() {
        return circuitOpenWaitMs;
    }

    public VKHttpConfig circuitOpenWaitMs(long circuitOpenWaitMs) {
        this.circuitOpenWaitMs = Math.max(100, circuitOpenWaitMs);
        return this;
    }

    public int getCircuitHalfOpenMaxCalls() {
        return circuitHalfOpenMaxCalls;
    }

    public VKHttpConfig circuitHalfOpenMaxCalls(int circuitHalfOpenMaxCalls) {
        this.circuitHalfOpenMaxCalls = Math.max(1, circuitHalfOpenMaxCalls);
        return this;
    }

    public Set<Integer> getCircuitRecordStatuses() {
        return Set.copyOf(circuitRecordStatuses);
    }

    public VKHttpConfig circuitRecordStatuses(Set<Integer> circuitRecordStatuses) {
        this.circuitRecordStatuses = circuitRecordStatuses == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(circuitRecordStatuses);
        return this;
    }

    public VKHttpConfig circuitRecordStatuses(Integer... circuitRecordStatuses) {
        this.circuitRecordStatuses = new LinkedHashSet<>();
        if (circuitRecordStatuses != null) {
            for (Integer s : circuitRecordStatuses) {
                if (s != null) {
                    this.circuitRecordStatuses.add(s);
                }
            }
        }
        return this;
    }

    public boolean isBulkheadEnabled() {
        return bulkheadEnabled;
    }

    public VKHttpConfig bulkheadEnabled(boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
        return this;
    }

    public int getBulkheadMaxConcurrent() {
        return bulkheadMaxConcurrent;
    }

    public VKHttpConfig bulkheadMaxConcurrent(int bulkheadMaxConcurrent) {
        this.bulkheadMaxConcurrent = Math.max(1, bulkheadMaxConcurrent);
        return this;
    }

    public int getBulkheadQueueSize() {
        return bulkheadQueueSize;
    }

    public VKHttpConfig bulkheadQueueSize(int bulkheadQueueSize) {
        this.bulkheadQueueSize = Math.max(0, bulkheadQueueSize);
        return this;
    }

    public long getBulkheadAcquireTimeoutMs() {
        return bulkheadAcquireTimeoutMs;
    }

    public VKHttpConfig bulkheadAcquireTimeoutMs(long bulkheadAcquireTimeoutMs) {
        this.bulkheadAcquireTimeoutMs = Math.max(0, bulkheadAcquireTimeoutMs);
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public VKHttpConfig userAgent(String userAgent) {
        if (userAgent != null && !userAgent.isBlank()) {
            this.userAgent = userAgent.trim();
        }
        return this;
    }

    public Map<String, String> getDefaultHeaders() {
        return Map.copyOf(defaultHeaders);
    }

    public VKHttpConfig defaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultHeaders);
        return this;
    }

    public VKHttpConfig putHeader(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            this.defaultHeaders.put(name.trim(), value);
        }
        return this;
    }

    private static LinkedHashSet<String> normalizeMethods(Set<String> methods) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (methods == null) {
            return out;
        }
        for (String m : methods) {
            if (m != null && !m.isBlank()) {
                out.add(m.trim().toUpperCase());
            }
        }
        return out;
    }
}
