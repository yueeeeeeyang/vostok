package yueyang.vostok.ai.provider;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiModelConfig {
    private VKAiModelType type;
    private VKAiProvider provider = VKAiProvider.OPENAI_COMPATIBLE;
    private String baseUrl;
    private String path;
    private String apiKey;
    private long connectTimeoutMs = -1;
    private long readTimeoutMs = -1;
    private int maxRetries = -1;
    private Boolean failOnNon2xx;
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
    private String model;

    public VKAiModelConfig copy() {
        VKAiModelConfig c = new VKAiModelConfig();
        c.type = this.type;
        c.provider = this.provider;
        c.baseUrl = this.baseUrl;
        c.path = this.path;
        c.apiKey = this.apiKey;
        c.connectTimeoutMs = this.connectTimeoutMs;
        c.readTimeoutMs = this.readTimeoutMs;
        c.maxRetries = this.maxRetries;
        c.failOnNon2xx = this.failOnNon2xx;
        c.defaultHeaders = new LinkedHashMap<>(this.defaultHeaders);
        c.model = this.model;
        return c;
    }

    public VKAiModelType getType() {
        return type;
    }

    public VKAiModelConfig type(VKAiModelType type) {
        this.type = type;
        return this;
    }

    public VKAiProvider getProvider() {
        return provider;
    }

    public VKAiModelConfig provider(VKAiProvider provider) {
        this.provider = provider == null ? VKAiProvider.OPENAI_COMPATIBLE : provider;
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public VKAiModelConfig baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public String getPath() {
        return path;
    }

    public VKAiModelConfig path(String path) {
        this.path = path;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public VKAiModelConfig apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKAiModelConfig connectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs <= 0 ? -1 : connectTimeoutMs;
        return this;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKAiModelConfig readTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs <= 0 ? -1 : readTimeoutMs;
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKAiModelConfig maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public Boolean getFailOnNon2xx() {
        return failOnNon2xx;
    }

    public VKAiModelConfig failOnNon2xx(Boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public Map<String, String> getDefaultHeaders() {
        return Map.copyOf(defaultHeaders);
    }

    public VKAiModelConfig defaultHeaders(Map<String, String> defaultHeaders) {
        this.defaultHeaders = defaultHeaders == null ? new LinkedHashMap<>() : new LinkedHashMap<>(defaultHeaders);
        return this;
    }

    public VKAiModelConfig putHeader(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            defaultHeaders.put(name.trim(), value);
        }
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiModelConfig model(String model) {
        this.model = model;
        return this;
    }
}
