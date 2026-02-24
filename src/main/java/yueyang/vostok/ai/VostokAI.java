package yueyang.vostok.ai;

import yueyang.vostok.ai.core.VKAiRuntime;
import yueyang.vostok.ai.provider.VKAiClientConfig;
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

    public static void registerClient(String name, VKAiClientConfig config) {
        RUNTIME.registerClient(name, config);
    }

    public static void withClient(String name, Runnable action) {
        RUNTIME.withClient(name, action);
    }

    public static <T> T withClient(String name, Supplier<T> supplier) {
        return RUNTIME.withClient(name, supplier);
    }

    public static Set<String> clientNames() {
        return RUNTIME.clientNames();
    }

    public static String currentClientName() {
        return RUNTIME.currentClientName();
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

    public static void healthCheckRag(String clientName) {
        RUNTIME.healthCheckRag(clientName, true);
    }

    public static void healthCheckRag(String clientName, boolean includeRerank) {
        RUNTIME.healthCheckRag(clientName, includeRerank);
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
