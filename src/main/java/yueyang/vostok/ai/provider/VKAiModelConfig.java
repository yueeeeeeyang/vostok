package yueyang.vostok.ai.provider;

public class VKAiModelConfig {
    private VKAiModelType type;
    private String providerName;
    private String model;

    public VKAiModelConfig copy() {
        return new VKAiModelConfig()
                .type(type)
                .provider(providerName)
                .model(model);
    }

    public VKAiModelType getType() {
        return type;
    }

    public VKAiModelConfig type(VKAiModelType type) {
        this.type = type;
        return this;
    }

    public String getProviderName() {
        return providerName;
    }

    public VKAiModelConfig provider(String providerName) {
        this.providerName = providerName;
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

