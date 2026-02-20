package yueyang.vostok.http;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class VKHttpConfig {
    private long connectTimeoutMs = 3000;
    private long requestTimeoutMs = 10_000;
    private int maxRetries = 1;
    private long retryBackoffBaseMs = 100;
    private long retryBackoffMaxMs = 1000;
    private boolean retryJitterEnabled = true;
    private boolean retryOnNetworkError = true;
    private Set<Integer> retryOnStatuses = new LinkedHashSet<>(Set.of(429, 502, 503, 504));
    private Set<String> retryMethods = new LinkedHashSet<>(Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE"));
    private boolean failOnNon2xx = true;
    private boolean followRedirects = true;
    private boolean logEnabled = true;
    private boolean metricsEnabled = true;
    private long maxResponseBodyBytes = 8L * 1024 * 1024;
    private String userAgent = "VostokHttp/1.0";
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();

    public VKHttpConfig copy() {
        VKHttpConfig c = new VKHttpConfig();
        c.connectTimeoutMs = this.connectTimeoutMs;
        c.requestTimeoutMs = this.requestTimeoutMs;
        c.maxRetries = this.maxRetries;
        c.retryBackoffBaseMs = this.retryBackoffBaseMs;
        c.retryBackoffMaxMs = this.retryBackoffMaxMs;
        c.retryJitterEnabled = this.retryJitterEnabled;
        c.retryOnNetworkError = this.retryOnNetworkError;
        c.retryOnStatuses = new LinkedHashSet<>(this.retryOnStatuses);
        c.retryMethods = new LinkedHashSet<>(this.retryMethods);
        c.failOnNon2xx = this.failOnNon2xx;
        c.followRedirects = this.followRedirects;
        c.logEnabled = this.logEnabled;
        c.metricsEnabled = this.metricsEnabled;
        c.maxResponseBodyBytes = this.maxResponseBodyBytes;
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

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public VKHttpConfig requestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = Math.max(1, requestTimeoutMs);
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
