package yueyang.vostok.ai.rag;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiRagRequest {
    private String chatModel;
    private String embeddingModel;
    private String rerankModel;
    private String query;
    private int topK = 4;
    private int vectorTopK = 8;
    private int keywordTopK = 8;
    private double vectorWeight = 0.65;
    private double keywordWeight = 0.35;
    private boolean rerankEnabled = true;
    private boolean queryRewriteEnabled = true;
    private boolean dynamicTopKEnabled = true;
    private boolean mergeSimilarChunksEnabled = true;
    private boolean contextCompressionEnabled = true;
    private int contextMaxCharsPerChunk = 280;
    private int contextMaxChars = 1800;
    private String systemPrompt;

    /**
     * Ext 5：元数据过滤条件（AND 语义）。
     * 向量检索和关键词检索均只返回 metadata 匹配所有条目的文档。
     * 为 null 或空时不过滤。
     */
    private Map<String, String> metadataFilter;

    public String getChatModel() {
        return chatModel;
    }

    public VKAiRagRequest chatModel(String chatModel) {
        this.chatModel = chatModel;
        return this;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public VKAiRagRequest embeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public VKAiRagRequest rerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
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

    public boolean isQueryRewriteEnabled() {
        return queryRewriteEnabled;
    }

    public VKAiRagRequest queryRewriteEnabled(boolean queryRewriteEnabled) {
        this.queryRewriteEnabled = queryRewriteEnabled;
        return this;
    }

    public boolean isDynamicTopKEnabled() {
        return dynamicTopKEnabled;
    }

    public VKAiRagRequest dynamicTopKEnabled(boolean dynamicTopKEnabled) {
        this.dynamicTopKEnabled = dynamicTopKEnabled;
        return this;
    }

    public boolean isMergeSimilarChunksEnabled() {
        return mergeSimilarChunksEnabled;
    }

    public VKAiRagRequest mergeSimilarChunksEnabled(boolean mergeSimilarChunksEnabled) {
        this.mergeSimilarChunksEnabled = mergeSimilarChunksEnabled;
        return this;
    }

    public boolean isContextCompressionEnabled() {
        return contextCompressionEnabled;
    }

    public VKAiRagRequest contextCompressionEnabled(boolean contextCompressionEnabled) {
        this.contextCompressionEnabled = contextCompressionEnabled;
        return this;
    }

    public int getContextMaxCharsPerChunk() {
        return contextMaxCharsPerChunk;
    }

    public VKAiRagRequest contextMaxCharsPerChunk(int contextMaxCharsPerChunk) {
        this.contextMaxCharsPerChunk = Math.max(32, contextMaxCharsPerChunk);
        return this;
    }

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public VKAiRagRequest contextMaxChars(int contextMaxChars) {
        this.contextMaxChars = Math.max(128, contextMaxChars);
        return this;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public VKAiRagRequest systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    // Ext 5：元数据过滤

    public Map<String, String> getMetadataFilter() {
        return metadataFilter == null ? null : Map.copyOf(metadataFilter);
    }

    public VKAiRagRequest metadataFilter(Map<String, String> filter) {
        this.metadataFilter = filter == null ? null : new LinkedHashMap<>(filter);
        return this;
    }

    public VKAiRagRequest metadataFilter(String key, String value) {
        if (this.metadataFilter == null) {
            this.metadataFilter = new LinkedHashMap<>();
        }
        if (key != null && value != null) {
            this.metadataFilter.put(key, value);
        }
        return this;
    }
}
