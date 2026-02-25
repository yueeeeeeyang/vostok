package yueyang.vostok.ai;

import yueyang.vostok.ai.core.VKAiRuntime;
import yueyang.vostok.ai.provider.VKAiModelConfig;
import yueyang.vostok.ai.provider.VKAiProfileConfig;
import yueyang.vostok.ai.provider.VKAiProviderConfig;
import yueyang.vostok.ai.rag.VKAiEmbedding;
import yueyang.vostok.ai.rag.VKAiEmbeddingRequest;
import yueyang.vostok.ai.rag.VKAiRagRequest;
import yueyang.vostok.ai.rag.VKAiRagResponse;
import yueyang.vostok.ai.rag.VKAiRagIngestRequest;
import yueyang.vostok.ai.rag.VKAiRagIngestResult;
import yueyang.vostok.ai.rag.VKAiRerankRequest;
import yueyang.vostok.ai.rag.VKAiRerankResponse;
import yueyang.vostok.ai.rag.VKAiVectorDoc;
import yueyang.vostok.ai.rag.VKAiVectorHit;
import yueyang.vostok.ai.rag.VKAiVectorStore;
import yueyang.vostok.ai.tool.VKAiTool;
import yueyang.vostok.ai.tool.VKAiToolCall;
import yueyang.vostok.ai.tool.VKAiToolCallResult;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class VostokAI {
    private static final VKAiRuntime RUNTIME = VKAiRuntime.getInstance();

    protected VostokAI() {
    }

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

    public static void registerProvider(String name, VKAiProviderConfig config) {
        RUNTIME.registerProvider(name, config);
    }

    public static void registerModel(String name, VKAiModelConfig config) {
        RUNTIME.registerModel(name, config);
    }

    public static void registerProfile(String name, VKAiProfileConfig config) {
        RUNTIME.registerProfile(name, config);
    }

    public static void withProfile(String name, Runnable action) {
        RUNTIME.withProfile(name, action);
    }

    public static <T> T withProfile(String name, Supplier<T> supplier) {
        return RUNTIME.withProfile(name, supplier);
    }

    public static Set<String> profileNames() {
        return RUNTIME.profileNames();
    }

    public static String currentProfileName() {
        return RUNTIME.currentProfileName();
    }

    public static void setMemoryStore(VKAiMemoryStore store) {
        RUNTIME.setMemoryStore(store);
    }

    public static VKAiSession createSession(String profileName) {
        return RUNTIME.createSession(profileName, null);
    }

    public static VKAiSession createSession(String profileName, String model) {
        return RUNTIME.createSession(profileName, model);
    }

    public static VKAiSession session(String sessionId) {
        return RUNTIME.session(sessionId);
    }

    public static VKAiSession switchSessionModel(String sessionId, String model) {
        return RUNTIME.switchSessionModel(sessionId, model);
    }

    public static java.util.List<VKAiSessionMessage> sessionMessages(String sessionId) {
        return RUNTIME.sessionMessages(sessionId);
    }

    public static VKAiChatResponse chatSession(String sessionId, String userText) {
        return RUNTIME.chatSession(sessionId, userText);
    }

    public static void deleteSession(String sessionId) {
        RUNTIME.deleteSession(sessionId);
    }

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

    public static java.util.List<VKAiEmbedding> embed(VKAiEmbeddingRequest request) {
        return RUNTIME.embed(request);
    }

    public static VKAiRerankResponse rerank(VKAiRerankRequest request) {
        return RUNTIME.rerank(request);
    }

    public static void setVectorStore(VKAiVectorStore store) {
        RUNTIME.setVectorStore(store);
    }

    public static void upsertVectorDocs(java.util.List<VKAiVectorDoc> docs) {
        RUNTIME.upsertVectorDocs(docs);
    }

    public static java.util.List<VKAiVectorHit> searchVector(java.util.List<Double> queryVector, int topK) {
        return RUNTIME.searchVector(queryVector, topK);
    }

    public static java.util.List<VKAiVectorHit> searchKeywords(String query, int topK) {
        return RUNTIME.searchKeywords(query, topK);
    }

    public static void clearVectorStore() {
        RUNTIME.clearVectorStore();
    }

    public static VKAiRagIngestResult ingestRagDocument(VKAiRagIngestRequest request) {
        return RUNTIME.ingestRagDocument(request);
    }

    public static VKAiRagResponse rag(VKAiRagRequest request) {
        return RUNTIME.rag(request);
    }

    public static void healthCheckRag(String profileName) {
        RUNTIME.healthCheckRag(profileName, true);
    }

    public static void healthCheckRag(String profileName, boolean includeRerank) {
        RUNTIME.healthCheckRag(profileName, includeRerank);
    }

    public static void registerTool(VKAiTool tool) {
        RUNTIME.registerTool(tool);
    }

    public static void clearTools() {
        RUNTIME.clearTools();
    }

    public static java.util.Set<String> toolNames() {
        return RUNTIME.toolNames();
    }

    public static VKAiToolCallResult callTool(VKAiToolCall call) {
        return RUNTIME.callTool(call, null, null);
    }

    public static java.util.List<VKAiAuditRecord> audits(int limit) {
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
