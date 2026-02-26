package yueyang.vostok.ai.provider;

public enum VKAiProvider {
    OPENAI_COMPATIBLE("openai-compatible", "/v1/chat/completions", "/v1/embeddings", "/v1/rerank");

    private final String code;
    private final String chatPath;
    private final String embeddingPath;
    private final String rerankPath;

    VKAiProvider(String code, String chatPath, String embeddingPath, String rerankPath) {
        this.code = code;
        this.chatPath = chatPath;
        this.embeddingPath = embeddingPath;
        this.rerankPath = rerankPath;
    }

    public String code() {
        return code;
    }

    public String defaultPath(VKAiModelType type) {
        if (type == null) {
            return chatPath;
        }
        return switch (type) {
            case CHAT -> chatPath;
            case EMBEDDING -> embeddingPath;
            case RERANK -> rerankPath;
        };
    }
}
