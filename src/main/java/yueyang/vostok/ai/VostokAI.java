package yueyang.vostok.ai;

import yueyang.vostok.ai.core.VKAiRuntime;
import yueyang.vostok.ai.prompt.VKAiPromptTemplate;
import yueyang.vostok.ai.provider.VKAiModelConfig;
import yueyang.vostok.ai.rag.VKAiEmbedding;
import yueyang.vostok.ai.rag.VKAiEmbeddingRequest;
import yueyang.vostok.ai.rag.VKAiRagIngestRequest;
import yueyang.vostok.ai.rag.VKAiRagIngestResult;
import yueyang.vostok.ai.rag.VKAiRagRequest;
import yueyang.vostok.ai.rag.VKAiRagResponse;
import yueyang.vostok.ai.rag.VKAiRerankRequest;
import yueyang.vostok.ai.rag.VKAiRerankResponse;
import yueyang.vostok.ai.rag.VKAiVectorDoc;
import yueyang.vostok.ai.rag.VKAiVectorHit;
import yueyang.vostok.ai.rag.VKAiVectorStore;
import yueyang.vostok.ai.tool.VKAiTool;
import yueyang.vostok.ai.tool.VKAiToolCall;
import yueyang.vostok.ai.tool.VKAiToolCallResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class VostokAI {
    private static final VKAiRuntime RUNTIME = VKAiRuntime.getInstance();

    protected VostokAI() {
    }

    // -------------------------------------------------------------------------
    // 生命周期
    // -------------------------------------------------------------------------

    public static void init() {
        RUNTIME.init(new VKAiConfig());
    }

    public static void init(VKAiConfig config) {
        RUNTIME.init(config);
    }

    public static void reinit(VKAiConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKAiConfig config() {
        return RUNTIME.config();
    }

    public static void close() {
        RUNTIME.close();
    }

    // -------------------------------------------------------------------------
    // 模型注册
    // -------------------------------------------------------------------------

    public static void registerModel(String name, VKAiModelConfig config) {
        RUNTIME.registerModel(name, config);
    }

    // -------------------------------------------------------------------------
    // 会话管理
    // -------------------------------------------------------------------------

    public static void setMemoryStore(VKAiMemoryStore store) {
        RUNTIME.setMemoryStore(store);
    }

    public static VKAiSession createSession(String model) {
        return RUNTIME.createSession(model);
    }

    public static VKAiSession session(String sessionId) {
        return RUNTIME.session(sessionId);
    }

    public static VKAiSession switchSessionModel(String sessionId, String model) {
        return RUNTIME.switchSessionModel(sessionId, model);
    }

    public static List<VKAiSessionMessage> sessionMessages(String sessionId) {
        return RUNTIME.sessionMessages(sessionId);
    }

    public static VKAiChatResponse chatSession(String sessionId, String userText) {
        return RUNTIME.chatSession(sessionId, userText);
    }

    public static void deleteSession(String sessionId) {
        RUNTIME.deleteSession(sessionId);
    }

    /**
     * 更新会话 metadata（Ext 6）。
     * 返回更新后的 VKAiSession 对象。
     */
    public static VKAiSession updateSessionMetadata(String sessionId, Map<String, String> metadata) {
        return RUNTIME.updateSessionMetadata(sessionId, metadata);
    }

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------

    public static VKAiChatResponse chat(VKAiChatRequest request) {
        return RUNTIME.chat(request);
    }

    public static CompletableFuture<VKAiChatResponse> chatAsync(VKAiChatRequest request) {
        return RUNTIME.chatAsync(request);
    }

    public static <T> T chatJson(VKAiChatRequest request, Class<T> type) {
        return RUNTIME.chatJson(request, type);
    }

    public static <T> CompletableFuture<T> chatJsonAsync(VKAiChatRequest request, Class<T> type) {
        return RUNTIME.chatJsonAsync(request, type);
    }

    // -------------------------------------------------------------------------
    // Embedding & Rerank
    // -------------------------------------------------------------------------

    public static List<VKAiEmbedding> embed(VKAiEmbeddingRequest request) {
        return RUNTIME.embed(request);
    }

    public static VKAiRerankResponse rerank(VKAiRerankRequest request) {
        return RUNTIME.rerank(request);
    }

    // -------------------------------------------------------------------------
    // Vector Store
    // -------------------------------------------------------------------------

    public static void setVectorStore(VKAiVectorStore store) {
        RUNTIME.setVectorStore(store);
    }

    public static void upsertVectorDocs(List<VKAiVectorDoc> docs) {
        RUNTIME.upsertVectorDocs(docs);
    }

    public static List<VKAiVectorHit> searchVector(List<Double> queryVector, int topK) {
        return RUNTIME.searchVector(queryVector, topK);
    }

    public static List<VKAiVectorHit> searchKeywords(String query, int topK) {
        return RUNTIME.searchKeywords(query, topK);
    }

    public static void clearVectorStore() {
        RUNTIME.clearVectorStore();
    }

    // -------------------------------------------------------------------------
    // RAG
    // -------------------------------------------------------------------------

    public static VKAiRagIngestResult ingestRagDocument(VKAiRagIngestRequest request) {
        return RUNTIME.ingestRagDocument(request);
    }

    public static VKAiRagResponse rag(VKAiRagRequest request) {
        return RUNTIME.rag(request);
    }

    public static void healthCheckRag(String embeddingModel, String rerankModel) {
        RUNTIME.healthCheckRag(embeddingModel, rerankModel, true);
    }

    public static void healthCheckRag(String embeddingModel, String rerankModel, boolean includeRerank) {
        RUNTIME.healthCheckRag(embeddingModel, rerankModel, includeRerank);
    }

    // -------------------------------------------------------------------------
    // Tool Calling
    // -------------------------------------------------------------------------

    public static void registerTool(VKAiTool tool) {
        RUNTIME.registerTool(tool);
    }

    public static void clearTools() {
        RUNTIME.clearTools();
    }

    public static Set<String> toolNames() {
        return RUNTIME.toolNames();
    }

    public static VKAiToolCallResult callTool(VKAiToolCall call) {
        return RUNTIME.callTool(call, null, null);
    }

    // -------------------------------------------------------------------------
    // Ext 3：Prompt 模板注册中心
    // -------------------------------------------------------------------------

    /**
     * 注册 Prompt 模板（Ext 3）。
     */
    public static void registerPrompt(VKAiPromptTemplate template) {
        RUNTIME.registerPrompt(template);
    }

    /**
     * 渲染已注册的 Prompt 模板（Ext 3）。
     * 返回 {"system": "...", "user": "..."} 映射，可直接用于构建 VKAiChatRequest。
     */
    public static Map<String, String> renderPrompt(String name, Map<String, ?> vars) {
        return RUNTIME.renderPrompt(name, vars);
    }

    /**
     * 返回所有已注册的 Prompt 模板名称（Ext 3）。
     */
    public static Set<String> promptNames() {
        return RUNTIME.promptNames();
    }

    // -------------------------------------------------------------------------
    // Audit & Metrics
    // -------------------------------------------------------------------------

    public static List<VKAiAuditRecord> audits(int limit) {
        return RUNTIME.audits(limit);
    }

    public static void clearAudits() {
        RUNTIME.clearAudits();
    }

    public static VKAiMetrics metrics() {
        return RUNTIME.metrics();
    }

    public static void resetMetrics() {
        RUNTIME.resetMetrics();
    }
}
