package yueyang.vostok.web.rate;

import yueyang.vostok.web.http.VKRequest;

import java.util.function.Function;

public final class VKRateLimitConfig {
    private int capacity = 100;
    private int refillTokens = 100;
    private long refillPeriodMs = 1000;
    private VKRateLimitKeyStrategy keyStrategy = VKRateLimitKeyStrategy.IP;
    private String headerName = "X-Rate-Limit-Key";
    private Function<VKRequest, String> customKeyResolver;
    private int rejectStatus = 429;
    private String rejectBody = "Too Many Requests";

    public int getCapacity() {
        return capacity;
    }

    public VKRateLimitConfig capacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        return this;
    }

    public int getRefillTokens() {
        return refillTokens;
    }

    public VKRateLimitConfig refillTokens(int refillTokens) {
        this.refillTokens = Math.max(1, refillTokens);
        return this;
    }

    public long getRefillPeriodMs() {
        return refillPeriodMs;
    }

    public VKRateLimitConfig refillPeriodMs(long refillPeriodMs) {
        this.refillPeriodMs = Math.max(1, refillPeriodMs);
        return this;
    }

    public VKRateLimitKeyStrategy getKeyStrategy() {
        return keyStrategy;
    }

    public VKRateLimitConfig keyStrategy(VKRateLimitKeyStrategy keyStrategy) {
        this.keyStrategy = keyStrategy == null ? VKRateLimitKeyStrategy.IP : keyStrategy;
        return this;
    }

    public String getHeaderName() {
        return headerName;
    }

    public VKRateLimitConfig headerName(String headerName) {
        if (headerName != null && !headerName.isEmpty()) {
            this.headerName = headerName;
        }
        return this;
    }

    public Function<VKRequest, String> getCustomKeyResolver() {
        return customKeyResolver;
    }

    public VKRateLimitConfig customKeyResolver(Function<VKRequest, String> customKeyResolver) {
        this.customKeyResolver = customKeyResolver;
        return this;
    }

    public int getRejectStatus() {
        return rejectStatus;
    }

    public VKRateLimitConfig rejectStatus(int rejectStatus) {
        this.rejectStatus = rejectStatus <= 0 ? 429 : rejectStatus;
        return this;
    }

    public String getRejectBody() {
        return rejectBody;
    }

    public VKRateLimitConfig rejectBody(String rejectBody) {
        this.rejectBody = rejectBody == null ? "" : rejectBody;
        return this;
    }
}
