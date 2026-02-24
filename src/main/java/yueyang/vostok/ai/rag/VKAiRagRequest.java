package yueyang.vostok.ai.rag;

public class VKAiRagRequest {
    private String clientName;
    private String model;
    private String query;
    private int topK = 4;
    private int vectorTopK = 8;
    private int keywordTopK = 8;
    private double vectorWeight = 0.65;
    private double keywordWeight = 0.35;
    private boolean rerankEnabled = true;
    private String systemPrompt;

    public String getClientName() {
        return clientName;
    }

    public VKAiRagRequest client(String clientName) {
        this.clientName = clientName;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiRagRequest model(String model) {
        this.model = model;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public VKAiRagRequest query(String query) {
        this.query = query;
        return this;
    }

    public int getTopK() {
        return topK;
    }

    public VKAiRagRequest topK(int topK) {
        this.topK = Math.max(1, topK);
        return this;
    }

    public int getVectorTopK() {
        return vectorTopK;
    }

    public VKAiRagRequest vectorTopK(int vectorTopK) {
        this.vectorTopK = Math.max(1, vectorTopK);
        return this;
    }

    public int getKeywordTopK() {
        return keywordTopK;
    }

    public VKAiRagRequest keywordTopK(int keywordTopK) {
        this.keywordTopK = Math.max(1, keywordTopK);
        return this;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public VKAiRagRequest vectorWeight(double vectorWeight) {
        this.vectorWeight = Math.max(0.0, vectorWeight);
        return this;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public VKAiRagRequest keywordWeight(double keywordWeight) {
        this.keywordWeight = Math.max(0.0, keywordWeight);
        return this;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public VKAiRagRequest rerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
        return this;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public VKAiRagRequest systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }
}
