package yueyang.vostok.http;

import yueyang.vostok.http.auth.VKHttpAuth;

import javax.net.ssl.SSLContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VKHttpClientConfig {
    private String baseUrl;
    private long connectTimeoutMs = -1;
    private long totalTimeoutMs = -1;
    private long readTimeoutMs = -1;
    private int maxRetries = -1;
    private long maxResponseBodyBytes = -1;
    private long maxRetryDelayMs = -1;
    private Boolean retryOnNetworkError;
    private Boolean retryOnTimeout;
    private Boolean respectRetryAfter;
    private Boolean requireIdempotencyKeyForUnsafeRetry;
    private String idempotencyKeyHeader;
    private Boolean failOnNon2xx;
    private Boolean followRedirects;
    private Integer rateLimitQps;
    private Integer rateLimitBurst;
    private Boolean circuitEnabled;
    private Integer circuitWindowSize;
    private Integer circuitMinCalls;
    private Integer circuitFailureRateThreshold;
    private Long circuitOpenWaitMs;
    private Integer circuitHalfOpenMaxCalls;
    private Set<Integer> circuitRecordStatuses = new LinkedHashSet<>();
    private Boolean bulkheadEnabled;
    private Integer bulkheadMaxConcurrent;
    private Integer bulkheadQueueSize;
    private Long bulkheadAcquireTimeoutMs;
    private Boolean streamEnabled;
    private Long streamIdleTimeoutMs;
    private Long streamTotalTimeoutMs;
    private Integer sseMaxEventBytes;
    private Boolean sseEmitDoneEvent;
    private Integer streamQueueCapacity;
    private String userAgent;
    private Set<Integer> retryOnStatuses = new LinkedHashSet<>();
    private Set<String> retryMethods = new LinkedHashSet<>();
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private VKHttpAuth auth;
    private SSLContext sslContext;
    private String trustStorePath;
    private String trustStorePassword;
    private String trustStoreType = "PKCS12";
    private String keyStorePath;
    private String keyStorePassword;
    private String keyStoreKeyPassword;
    private String keyStoreType = "PKCS12";
    // 扩展1：客户端级拦截器（在全局拦截器之后执行）
    private List<VKHttpInterceptor> interceptors = new ArrayList<>();
    // 扩展2：Cookie 策略，null 表示继承全局配置或不启用
    private String cookiePolicy;

    public VKHttpClientConfig copy() {
        VKHttpClientConfig c = new VKHttpClientConfig();
        c.baseUrl = this.baseUrl;
        c.connectTimeoutMs = this.connectTimeoutMs;
        c.totalTimeoutMs = this.totalTimeoutMs;
        c.readTimeoutMs = this.readTimeoutMs;
        c.maxRetries = this.maxRetries;
        c.maxResponseBodyBytes = this.maxResponseBodyBytes;
        c.maxRetryDelayMs = this.maxRetryDelayMs;
        c.retryOnNetworkError = this.retryOnNetworkError;
        c.retryOnTimeout = this.retryOnTimeout;
        c.respectRetryAfter = this.respectRetryAfter;
        c.requireIdempotencyKeyForUnsafeRetry = this.requireIdempotencyKeyForUnsafeRetry;
        c.idempotencyKeyHeader = this.idempotencyKeyHeader;
        c.failOnNon2xx = this.failOnNon2xx;
        c.followRedirects = this.followRedirects;
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
        c.streamEnabled = this.streamEnabled;
        c.streamIdleTimeoutMs = this.streamIdleTimeoutMs;
        c.streamTotalTimeoutMs = this.streamTotalTimeoutMs;
        c.sseMaxEventBytes = this.sseMaxEventBytes;
        c.sseEmitDoneEvent = this.sseEmitDoneEvent;
        c.streamQueueCapacity = this.streamQueueCapacity;
        c.userAgent = this.userAgent;
        c.retryOnStatuses = new LinkedHashSet<>(this.retryOnStatuses);
        c.retryMethods = new LinkedHashSet<>(this.retryMethods);
        c.defaultHeaders = new LinkedHashMap<>(this.defaultHeaders);
        c.auth = this.auth;
        c.sslContext = this.sslContext;
        c.trustStorePath = this.trustStorePath;
        c.trustStorePassword = this.trustStorePassword;
        c.trustStoreType = this.trustStoreType;
        c.keyStorePath = this.keyStorePath;
        c.keyStorePassword = this.keyStorePassword;
        c.keyStoreKeyPassword = this.keyStoreKeyPassword;
        c.keyStoreType = this.keyStoreType;
        c.interceptors = new ArrayList<>(this.interceptors);
        c.cookiePolicy = this.cookiePolicy;
        return c;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public VKHttpClientConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKHttpClientConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs <= 0 ? -1 : connectTimeoutMs;
        return this;
    }

    public long getTotalTimeoutMs() {
        return totalTimeoutMs;
    }

    public VKHttpClientConfig totalTimeoutMs(long totalTimeoutMs) {
        this.totalTimeoutMs = totalTimeoutMs <= 0 ? -1 : totalTimeoutMs;
        return this;
    }

    public long getRequestTimeoutMs() {
        return totalTimeoutMs;
    }

    public VKHttpClientConfig requestTimeoutMs(long requestTimeoutMs) {
        return totalTimeoutMs(requestTimeoutMs);
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKHttpClientConfig readTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs <= 0 ? -1 : readTimeoutMs;
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKHttpClientConfig maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public long getMaxResponseBodyBytes() {
        return maxResponseBodyBytes;
    }

    public VKHttpClientConfig maxResponseBodyBytes(long maxResponseBodyBytes) {
        this.maxResponseBodyBytes = maxResponseBodyBytes <= 0 ? -1 : maxResponseBodyBytes;
        return this;
    }

    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public VKHttpClientConfig maxRetryDelayMs(long maxRetryDelayMs) {
        this.maxRetryDelayMs = maxRetryDelayMs <= 0 ? -1 : maxRetryDelayMs;
        return this;
    }

    public Boolean getRetryOnNetworkError() {
        return retryOnNetworkError;
    }

    public VKHttpClientConfig retryOnNetworkError(Boolean retryOnNetworkError) {
        this.retryOnNetworkError = retryOnNetworkError;
        return this;
    }

    public Boolean getRetryOnTimeout() {
        return retryOnTimeout;
    }

    public VKHttpClientConfig retryOnTimeout(Boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
        return this;
    }

    public Boolean getRespectRetryAfter() {
        return respectRetryAfter;
    }

    public VKHttpClientConfig respectRetryAfter(Boolean respectRetryAfter) {
        this.respectRetryAfter = respectRetryAfter;
        return this;
    }

    public Boolean getRequireIdempotencyKeyForUnsafeRetry() {
        return requireIdempotencyKeyForUnsafeRetry;
    }

    public VKHttpClientConfig requireIdempotencyKeyForUnsafeRetry(Boolean requireIdempotencyKeyForUnsafeRetry) {
        this.requireIdempotencyKeyForUnsafeRetry = requireIdempotencyKeyForUnsafeRetry;
        return this;
    }

    public String getIdempotencyKeyHeader() {
        return idempotencyKeyHeader;
    }

    public VKHttpClientConfig idempotencyKeyHeader(String idempotencyKeyHeader) {
        this.idempotencyKeyHeader = idempotencyKeyHeader;
        return this;
    }

    public Boolean getFailOnNon2xx() {
        return failOnNon2xx;
    }

    public VKHttpClientConfig failOnNon2xx(Boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public Boolean getFollowRedirects() {
        return followRedirects;
    }

    public VKHttpClientConfig followRedirects(Boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public Integer getRateLimitQps() {
        return rateLimitQps;
    }

    public VKHttpClientConfig rateLimitQps(Integer rateLimitQps) {
        this.rateLimitQps = rateLimitQps;
        return this;
    }

    public Integer getRateLimitBurst() {
        return rateLimitBurst;
    }

    public VKHttpClientConfig rateLimitBurst(Integer rateLimitBurst) {
        this.rateLimitBurst = rateLimitBurst;
        return this;
    }

    public Boolean getCircuitEnabled() {
        return circuitEnabled;
    }

    public VKHttpClientConfig circuitEnabled(Boolean circuitEnabled) {
        this.circuitEnabled = circuitEnabled;
        return this;
    }

    public Integer getCircuitWindowSize() {
        return circuitWindowSize;
    }

    public VKHttpClientConfig circuitWindowSize(Integer circuitWindowSize) {
        this.circuitWindowSize = circuitWindowSize;
        return this;
    }

    public Integer getCircuitMinCalls() {
        return circuitMinCalls;
    }

    public VKHttpClientConfig circuitMinCalls(Integer circuitMinCalls) {
        this.circuitMinCalls = circuitMinCalls;
        return this;
    }

    public Integer getCircuitFailureRateThreshold() {
        return circuitFailureRateThreshold;
    }

    public VKHttpClientConfig circuitFailureRateThreshold(Integer circuitFailureRateThreshold) {
        this.circuitFailureRateThreshold = circuitFailureRateThreshold;
        return this;
    }

    public Long getCircuitOpenWaitMs() {
        return circuitOpenWaitMs;
    }

    public VKHttpClientConfig circuitOpenWaitMs(Long circuitOpenWaitMs) {
        this.circuitOpenWaitMs = circuitOpenWaitMs;
        return this;
    }

    public Integer getCircuitHalfOpenMaxCalls() {
        return circuitHalfOpenMaxCalls;
    }

    public VKHttpClientConfig circuitHalfOpenMaxCalls(Integer circuitHalfOpenMaxCalls) {
        this.circuitHalfOpenMaxCalls = circuitHalfOpenMaxCalls;
        return this;
    }

    public Set<Integer> getCircuitRecordStatuses() {
        return Set.copyOf(circuitRecordStatuses);
    }

    public VKHttpClientConfig circuitRecordStatuses(Set<Integer> circuitRecordStatuses) {
        this.circuitRecordStatuses = circuitRecordStatuses == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(circuitRecordStatuses);
        return this;
    }

    public VKHttpClientConfig circuitRecordStatuses(Integer... circuitRecordStatuses) {
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

    public Boolean getBulkheadEnabled() {
        return bulkheadEnabled;
    }

    public VKHttpClientConfig bulkheadEnabled(Boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
        return this;
    }

    public Integer getBulkheadMaxConcurrent() {
        return bulkheadMaxConcurrent;
    }

    public VKHttpClientConfig bulkheadMaxConcurrent(Integer bulkheadMaxConcurrent) {
        this.bulkheadMaxConcurrent = bulkheadMaxConcurrent;
        return this;
    }

    public Integer getBulkheadQueueSize() {
        return bulkheadQueueSize;
    }

    public VKHttpClientConfig bulkheadQueueSize(Integer bulkheadQueueSize) {
        this.bulkheadQueueSize = bulkheadQueueSize;
        return this;
    }

    public Long getBulkheadAcquireTimeoutMs() {
        return bulkheadAcquireTimeoutMs;
    }

    public VKHttpClientConfig bulkheadAcquireTimeoutMs(Long bulkheadAcquireTimeoutMs) {
        this.bulkheadAcquireTimeoutMs = bulkheadAcquireTimeoutMs;
        return this;
    }

    public Boolean getStreamEnabled() {
        return streamEnabled;
    }

    public VKHttpClientConfig streamEnabled(Boolean streamEnabled) {
        this.streamEnabled = streamEnabled;
        return this;
    }

    public Long getStreamIdleTimeoutMs() {
        return streamIdleTimeoutMs;
    }

    public VKHttpClientConfig streamIdleTimeoutMs(Long streamIdleTimeoutMs) {
        this.streamIdleTimeoutMs = streamIdleTimeoutMs;
        return this;
    }

    public Long getStreamTotalTimeoutMs() {
        return streamTotalTimeoutMs;
    }

    public VKHttpClientConfig streamTotalTimeoutMs(Long streamTotalTimeoutMs) {
        this.streamTotalTimeoutMs = streamTotalTimeoutMs;
        return this;
    }

    public Integer getSseMaxEventBytes() {
        return sseMaxEventBytes;
    }

    public VKHttpClientConfig sseMaxEventBytes(Integer sseMaxEventBytes) {
        this.sseMaxEventBytes = sseMaxEventBytes;
        return this;
    }

    public Boolean getSseEmitDoneEvent() {
        return sseEmitDoneEvent;
    }

    public VKHttpClientConfig sseEmitDoneEvent(Boolean sseEmitDoneEvent) {
        this.sseEmitDoneEvent = sseEmitDoneEvent;
        return this;
    }

    public Integer getStreamQueueCapacity() {
        return streamQueueCapacity;
    }

    public VKHttpClientConfig streamQueueCapacity(Integer streamQueueCapacity) {
        this.streamQueueCapacity = streamQueueCapacity;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public VKHttpClientConfig userAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public Set<Integer> getRetryOnStatuses() {
        return Set.copyOf(retryOnStatuses);
    }

    public VKHttpClientConfig retryOnStatuses(Set<Integer> retryOnStatuses) {
        this.retryOnStatuses = retryOnStatuses == null ? new LinkedHashSet<>() : new LinkedHashSet<>(retryOnStatuses);
        return this;
    }

    public VKHttpClientConfig retryOnStatuses(Integer... retryOnStatuses) {
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

    public VKHttpClientConfig retryMethods(Set<String> retryMethods) {
        this.retryMethods = normalizeMethods(retryMethods);
        return this;
    }

    public VKHttpClientConfig retryMethods(String... retryMethods) {
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

    public Map<String, String> getDefaultHeaders() {
        return Map.copyOf(defaultHeaders);
    }

    public VKHttpClientConfig defaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultHeaders);
        return this;
    }

    public VKHttpClientConfig putHeader(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            this.defaultHeaders.put(name.trim(), value);
        }
        return this;
    }

    public VKHttpAuth getAuth() {
        return auth;
    }

    public VKHttpClientConfig auth(VKHttpAuth auth) {
        this.auth = auth;
        return this;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public VKHttpClientConfig sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public VKHttpClientConfig trustStore(String path, String password) {
        return trustStore(path, password, "PKCS12");
    }

    public VKHttpClientConfig trustStore(String path, String password, String type) {
        this.trustStorePath = path;
        this.trustStorePassword = password;
        this.trustStoreType = (type == null || type.isBlank()) ? "PKCS12" : type.trim();
        return this;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getKeyStoreKeyPassword() {
        return keyStoreKeyPassword;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public VKHttpClientConfig keyStore(String path, String storePassword) {
        return keyStore(path, storePassword, storePassword, "PKCS12");
    }

    public VKHttpClientConfig keyStore(String path, String storePassword, String keyPassword, String type) {
        this.keyStorePath = path;
        this.keyStorePassword = storePassword;
        this.keyStoreKeyPassword = keyPassword;
        this.keyStoreType = (type == null || type.isBlank()) ? "PKCS12" : type.trim();
        return this;
    }

    /** 添加客户端级拦截器（在全局拦截器之后执行）。 */
    public VKHttpClientConfig addInterceptor(VKHttpInterceptor interceptor) {
        if (interceptor != null) {
            this.interceptors.add(interceptor);
        }
        return this;
    }

    /** 返回客户端级拦截器列表（不可修改快照）。 */
    public List<VKHttpInterceptor> getInterceptors() {
        return List.copyOf(interceptors);
    }

    /** Cookie 策略，null 表示不启用（ACCEPT_ALL / ACCEPT_NONE / ACCEPT_ORIGINAL_SERVER）。 */
    public String getCookiePolicy() {
        return cookiePolicy;
    }

    /** 设置此客户端的 Cookie 策略。 */
    public VKHttpClientConfig cookiePolicy(String cookiePolicy) {
        this.cookiePolicy = cookiePolicy;
        return this;
    }

    /** 便捷方法：设置 Basic Auth。 */
    public VKHttpClientConfig basic(String username, String password) {
        this.auth = new yueyang.vostok.http.auth.VKBasicAuth(username, password);
        return this;
    }

    /** 便捷方法：设置 Bearer Token Auth。 */
    public VKHttpClientConfig bearer(String token) {
        this.auth = new yueyang.vostok.http.auth.VKBearerAuth(token);
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
