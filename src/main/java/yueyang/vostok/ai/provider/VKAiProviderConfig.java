package yueyang.vostok.ai.provider;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiProviderConfig {
    private String provider = "openai-compatible";
    private String baseUrl;
    private String apiKey;
    private String chatPath = "/v1/chat/completions";
    private String embeddingPath = "/v1/embeddings";
    private String rerankPath = "/v1/rerank";
    private long connectTimeoutMs = -1;
    private long readTimeoutMs = -1;
    private int maxRetries = -1;
    private Boolean failOnNon2xx;
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();

    public VKAiProviderConfig copy() {
        VKAiProviderConfig c = new VKAiProviderConfig();
        c.provider = this.provider;
        c.baseUrl = this.baseUrl;
        c.apiKey = this.apiKey;
        c.chatPath = this.chatPath;
        c.embeddingPath = this.embeddingPath;
        c.rerankPath = this.rerankPath;
        c.connectTimeoutMs = this.connectTimeoutMs;
        c.readTimeoutMs = this.readTimeoutMs;
        c.maxRetries = this.maxRetries;
        c.failOnNon2xx = this.failOnNon2xx;
        c.defaultHeaders = new LinkedHashMap<>(this.defaultHeaders);
        return c;
    }

    public String getProvider() {
        return provider;
    }

    public VKAiProviderConfig provider(String provider) {
        if (provider != null && !provider.isBlank()) {
            this.provider = provider.trim();
        }
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public VKAiProviderConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public VKAiProviderConfig apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getChatPath() {
        return chatPath;
    }

    public VKAiProviderConfig chatPath(String chatPath) {
        if (chatPath != null && !chatPath.isBlank()) {
            this.chatPath = chatPath;
        }
        return this;
    }

    public String getEmbeddingPath() {
        return embeddingPath;
    }

    public VKAiProviderConfig embeddingPath(String embeddingPath) {
        if (embeddingPath != null && !embeddingPath.isBlank()) {
            this.embeddingPath = embeddingPath;
        }
        return this;
    }

    public String getRerankPath() {
        return rerankPath;
    }

    public VKAiProviderConfig rerankPath(String rerankPath) {
        if (rerankPath != null && !rerankPath.isBlank()) {
            this.rerankPath = rerankPath;
        }
        return this;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKAiProviderConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs <= 0 ? -1 : connectTimeoutMs;
        return this;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKAiProviderConfig readTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs <= 0 ? -1 : readTimeoutMs;
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKAiProviderConfig maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public Boolean getFailOnNon2xx() {
        return failOnNon2xx;
    }

    public VKAiProviderConfig failOnNon2xx(Boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public Map<String, String> getDefaultHeaders() {
        return Map.copyOf(defaultHeaders);
    }

    public VKAiProviderConfig defaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultHeaders);
        return this;
    }

    public VKAiProviderConfig putHeader(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            this.defaultHeaders.put(name.trim(), value);
        }
        return this;
    }
}

