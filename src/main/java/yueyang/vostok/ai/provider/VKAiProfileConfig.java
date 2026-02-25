package yueyang.vostok.ai.provider;

public class VKAiProfileConfig {
    private String chatModel;
    private String embeddingModel;
    private String rerankModel;

    public VKAiProfileConfig copy() {
        return new VKAiProfileConfig()
                .chatModel(chatModel)
                .embeddingModel(embeddingModel)
                .rerankModel(rerankModel);
    }

    public String getChatModel() {
        return chatModel;
    }

    public VKAiProfileConfig chatModel(String chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public VKAiProfileConfig embeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public VKAiProfileConfig rerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
        return this;
    }
}

