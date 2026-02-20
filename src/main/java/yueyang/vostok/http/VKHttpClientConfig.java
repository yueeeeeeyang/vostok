package yueyang.vostok.http;

import yueyang.vostok.http.auth.VKHttpAuth;

import javax.net.ssl.SSLContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
