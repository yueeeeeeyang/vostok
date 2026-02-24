package yueyang.vostok.ai.provider;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiClientConfig {
    private String provider = "openai-compatible";
    private String baseUrl;
    private String apiKey;
    private String model;
    private String chatPath = "/v1/chat/completions";
    private String embeddingPath = "/v1/embeddings";
    private String rerankPath = "/v1/rerank";
    private long connectTimeoutMs = -1;
    private long readTimeoutMs = -1;
    private int maxRetries = -1;
    private Boolean failOnNon2xx;
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();

    public VKAiClientConfig copy() {
        VKAiClientConfig c = new VKAiClientConfig();
        c.provider = this.provider;
        c.baseUrl = this.baseUrl;
        c.apiKey = this.apiKey;
        c.model = this.model;
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

    public VKAiClientConfig provider(String provider) {
        if (provider != null && !provider.isBlank()) {
            this.provider = provider.trim();
        }
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public VKAiClientConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public VKAiClientConfig apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiClientConfig model(String model) {
        this.model = model;
        return this;
    }

    public String getChatPath() {
        return chatPath;
    }

    public VKAiClientConfig chatPath(String chatPath) {
        if (chatPath != null && !chatPath.isBlank()) {
            this.chatPath = chatPath;
        }
        return this;
    }

    public String getEmbeddingPath() {
        return embeddingPath;
    }

    public VKAiClientConfig embeddingPath(String embeddingPath) {
        if (embeddingPath != null && !embeddingPath.isBlank()) {
            this.embeddingPath = embeddingPath;
        }
        return this;
    }

    public String getRerankPath() {
        return rerankPath;
    }

    public VKAiClientConfig rerankPath(String rerankPath) {
        if (rerankPath != null && !rerankPath.isBlank()) {
            this.rerankPath = rerankPath;
        }
        return this;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKAiClientConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs <= 0 ? -1 : connectTimeoutMs;
        return this;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKAiClientConfig readTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs <= 0 ? -1 : readTimeoutMs;
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKAiClientConfig maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public Boolean getFailOnNon2xx() {
        return failOnNon2xx;
    }

    public VKAiClientConfig failOnNon2xx(Boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public Map<String, String> getDefaultHeaders() {
        return Map.copyOf(defaultHeaders);
    }

    public VKAiClientConfig defaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultHeaders);
        return this;
    }

    public VKAiClientConfig putHeader(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            this.defaultHeaders.put(name.trim(), value);
        }
        return this;
    }
}
