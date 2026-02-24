package yueyang.vostok.ai;

import java.util.LinkedHashSet;
import java.util.Set;

public class VKAiConfig {
    private long connectTimeoutMs = 3000;
    private long readTimeoutMs = 30_000;
    private int maxRetries = 1;
    private long retryBackoffMs = 150;
    private long maxRetryBackoffMs = 1500;
    private Set<Integer> retryOnStatuses = new LinkedHashSet<>(Set.of(429, 500, 502, 503, 504));
    private boolean retryOnNetworkError = true;
    private boolean retryOnTimeout = true;
    private boolean failOnNon2xx = true;
    private boolean metricsEnabled = true;
    private boolean logEnabled = false;
    private String defaultModel = "gpt-4o-mini";
    private boolean toolCallingEnabled = true;
    private boolean securityCheckEnabled = true;
    private boolean blockOnSecurityRisk = true;
    private boolean auditEnabled = true;
    private int maxToolCallsPerRequest = 8;
    private int maxAuditRecords = 1000;
    private boolean ragCacheEnabled = true;
    private long embeddingCacheTtlMs = 6 * 60 * 60 * 1000L;
    private long rerankCacheTtlMs = 30 * 60 * 1000L;
    private long ragAnswerCacheTtlMs = 10 * 60 * 1000L;
    private long ragRerankTimeoutMs = 1500;

    public VKAiConfig copy() {
        VKAiConfig c = new VKAiConfig();
        c.connectTimeoutMs = this.connectTimeoutMs;
        c.readTimeoutMs = this.readTimeoutMs;
        c.maxRetries = this.maxRetries;
        c.retryBackoffMs = this.retryBackoffMs;
        c.maxRetryBackoffMs = this.maxRetryBackoffMs;
        c.retryOnStatuses = new LinkedHashSet<>(this.retryOnStatuses);
        c.retryOnNetworkError = this.retryOnNetworkError;
        c.retryOnTimeout = this.retryOnTimeout;
        c.failOnNon2xx = this.failOnNon2xx;
        c.metricsEnabled = this.metricsEnabled;
        c.logEnabled = this.logEnabled;
        c.defaultModel = this.defaultModel;
        c.toolCallingEnabled = this.toolCallingEnabled;
        c.securityCheckEnabled = this.securityCheckEnabled;
        c.blockOnSecurityRisk = this.blockOnSecurityRisk;
        c.auditEnabled = this.auditEnabled;
        c.maxToolCallsPerRequest = this.maxToolCallsPerRequest;
        c.maxAuditRecords = this.maxAuditRecords;
        c.ragCacheEnabled = this.ragCacheEnabled;
        c.embeddingCacheTtlMs = this.embeddingCacheTtlMs;
        c.rerankCacheTtlMs = this.rerankCacheTtlMs;
        c.ragAnswerCacheTtlMs = this.ragAnswerCacheTtlMs;
        c.ragRerankTimeoutMs = this.ragRerankTimeoutMs;
        return c;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKAiConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
        return this;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKAiConfig readTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = Math.max(1, readTimeoutMs);
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKAiConfig maxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
        return this;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public VKAiConfig retryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = Math.max(1, retryBackoffMs);
        return this;
    }

    public long getMaxRetryBackoffMs() {
        return maxRetryBackoffMs;
    }

    public VKAiConfig maxRetryBackoffMs(long maxRetryBackoffMs) {
        this.maxRetryBackoffMs = Math.max(1, maxRetryBackoffMs);
        return this;
    }

    public Set<Integer> getRetryOnStatuses() {
        return Set.copyOf(retryOnStatuses);
    }

    public VKAiConfig retryOnStatuses(Set<Integer> retryOnStatuses) {
        this.retryOnStatuses = retryOnStatuses == null ? new LinkedHashSet<>() : new LinkedHashSet<>(retryOnStatuses);
        return this;
    }

    public VKAiConfig retryOnStatuses(Integer... statuses) {
        this.retryOnStatuses = new LinkedHashSet<>();
        if (statuses != null) {
            for (Integer status : statuses) {
                if (status != null) {
                    this.retryOnStatuses.add(status);
                }
            }
        }
        return this;
    }

    public boolean isRetryOnNetworkError() {
        return retryOnNetworkError;
    }

    public VKAiConfig retryOnNetworkError(boolean retryOnNetworkError) {
        this.retryOnNetworkError = retryOnNetworkError;
        return this;
    }

    public boolean isRetryOnTimeout() {
        return retryOnTimeout;
    }

    public VKAiConfig retryOnTimeout(boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
        return this;
    }

    public boolean isFailOnNon2xx() {
        return failOnNon2xx;
    }

    public VKAiConfig failOnNon2xx(boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public VKAiConfig metricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public VKAiConfig logEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
        return this;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public VKAiConfig defaultModel(String defaultModel) {
        if (defaultModel != null && !defaultModel.isBlank()) {
            this.defaultModel = defaultModel.trim();
        }
        return this;
    }

    public boolean isToolCallingEnabled() {
        return toolCallingEnabled;
    }

    public VKAiConfig toolCallingEnabled(boolean toolCallingEnabled) {
        this.toolCallingEnabled = toolCallingEnabled;
        return this;
    }

    public boolean isSecurityCheckEnabled() {
        return securityCheckEnabled;
    }

    public VKAiConfig securityCheckEnabled(boolean securityCheckEnabled) {
        this.securityCheckEnabled = securityCheckEnabled;
        return this;
    }

    public boolean isBlockOnSecurityRisk() {
        return blockOnSecurityRisk;
    }

    public VKAiConfig blockOnSecurityRisk(boolean blockOnSecurityRisk) {
        this.blockOnSecurityRisk = blockOnSecurityRisk;
        return this;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public VKAiConfig auditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
        return this;
    }

    public int getMaxToolCallsPerRequest() {
        return maxToolCallsPerRequest;
    }

    public VKAiConfig maxToolCallsPerRequest(int maxToolCallsPerRequest) {
        this.maxToolCallsPerRequest = Math.max(0, maxToolCallsPerRequest);
        return this;
    }

    public int getMaxAuditRecords() {
        return maxAuditRecords;
    }

    public VKAiConfig maxAuditRecords(int maxAuditRecords) {
        this.maxAuditRecords = Math.max(1, maxAuditRecords);
        return this;
    }

    public boolean isRagCacheEnabled() {
        return ragCacheEnabled;
    }

    public VKAiConfig ragCacheEnabled(boolean ragCacheEnabled) {
        this.ragCacheEnabled = ragCacheEnabled;
        return this;
    }

    public long getEmbeddingCacheTtlMs() {
        return embeddingCacheTtlMs;
    }

    public VKAiConfig embeddingCacheTtlMs(long embeddingCacheTtlMs) {
        this.embeddingCacheTtlMs = Math.max(0, embeddingCacheTtlMs);
        return this;
    }

    public long getRerankCacheTtlMs() {
        return rerankCacheTtlMs;
    }

    public VKAiConfig rerankCacheTtlMs(long rerankCacheTtlMs) {
        this.rerankCacheTtlMs = Math.max(0, rerankCacheTtlMs);
        return this;
    }

    public long getRagAnswerCacheTtlMs() {
        return ragAnswerCacheTtlMs;
    }

    public VKAiConfig ragAnswerCacheTtlMs(long ragAnswerCacheTtlMs) {
        this.ragAnswerCacheTtlMs = Math.max(0, ragAnswerCacheTtlMs);
        return this;
    }

    public long getRagRerankTimeoutMs() {
        return ragRerankTimeoutMs;
    }

    public VKAiConfig ragRerankTimeoutMs(long ragRerankTimeoutMs) {
        this.ragRerankTimeoutMs = Math.max(0, ragRerankTimeoutMs);
        return this;
    }
}
