package yueyang.vostok.ai.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.ai.VKAiAuditRecord;
import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.VKAiChatResponse;
import yueyang.vostok.ai.VKAiMemoryStore;
import yueyang.vostok.ai.VKAiConfig;
import yueyang.vostok.ai.provider.VKAiModelConfig;
import yueyang.vostok.ai.provider.VKAiModelType;
import yueyang.vostok.ai.rag.VKAiEmbedding;
import yueyang.vostok.ai.rag.VKAiEmbeddingRequest;
import yueyang.vostok.ai.rag.VKAiInMemoryVectorStore;
import yueyang.vostok.ai.VKAiMessage;
import yueyang.vostok.ai.VKAiMetrics;
import yueyang.vostok.ai.VKAiSession;
import yueyang.vostok.ai.VKAiSessionMessage;
import yueyang.vostok.ai.rag.VKAiRagRequest;
import yueyang.vostok.ai.rag.VKAiRagResponse;
import yueyang.vostok.ai.rag.VKAiRagIngestRequest;
import yueyang.vostok.ai.rag.VKAiRagIngestResult;
import yueyang.vostok.ai.rag.VKAiRerankRequest;
import yueyang.vostok.ai.rag.VKAiRerankResponse;
import yueyang.vostok.ai.rag.VKAiRerankResult;
import yueyang.vostok.ai.rag.VKAiTextChunker;
import yueyang.vostok.ai.tool.VKAiTool;
import yueyang.vostok.ai.tool.VKAiToolCall;
import yueyang.vostok.ai.tool.VKAiToolCallResult;
import yueyang.vostok.ai.tool.VKAiToolResult;
import yueyang.vostok.ai.VKAiUsage;
import yueyang.vostok.ai.rag.VKAiVectorDoc;
import yueyang.vostok.ai.rag.VKAiVectorHit;
import yueyang.vostok.ai.rag.VKAiVectorStore;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;
import yueyang.vostok.http.VKHttpRequest;
import yueyang.vostok.http.VKHttpResponseMeta;
import yueyang.vostok.http.VKHttpSseEvent;
import yueyang.vostok.http.VKHttpSseListener;
import yueyang.vostok.http.VKHttpStreamSession;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;
import yueyang.vostok.util.json.VKJson;

import yueyang.vostok.ai.prompt.VKAiPromptRegistry;
import yueyang.vostok.ai.prompt.VKAiPromptTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class VKAiRuntime {
    private static final Object LOCK = new Object();
    private static final VKAiRuntime INSTANCE = new VKAiRuntime();

    // Bug 8 修复：专用 IO 线程池，避免异步调用阻塞 ForkJoinPool.commonPool
    private static final ExecutorService AI_IO_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "vk-ai-io");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, VKAiModelConfig> models = new ConcurrentHashMap<>();
    // Perf 6：按 ModelType 维护反向索引，resolveModel 无需全量遍历
    private final ConcurrentHashMap<VKAiModelType, Set<String>> modelNamesByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> providerHttpClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKAiTool> tools = new ConcurrentHashMap<>();
    private final ArrayDeque<VKAiAuditRecord> auditRecords = new ArrayDeque<>();
    private volatile VKAiVectorStore vectorStore = new VKAiInMemoryVectorStore();
    private volatile VKAiMemoryStore memoryStore = new VKAiInMemoryMemoryStore();
    private final ConcurrentHashMap<String, Set<String>> docVersionChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> latestVersionByDoc = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> chunkFingerprintById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> fingerprintRefCounts = new ConcurrentHashMap<>();
    // Bug 3 修复：fingerprint → 归一化向量缓存，dedup 时复用已有 embedding 而非丢弃
    private final ConcurrentHashMap<String, List<Double>> fingerprintToVector = new ConcurrentHashMap<>();
    // Bug 4 修复：per-session 锁，防止并发 chatSession 产生相同 seq
    private final ConcurrentHashMap<String, Object> chatSessionLocks = new ConcurrentHashMap<>();
    // Ext 3：Prompt 模板注册中心
    private final VKAiPromptRegistry promptRegistry = new VKAiPromptRegistry();

    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong successCalls = new AtomicLong();
    private final AtomicLong failedCalls = new AtomicLong();
    private final AtomicLong retriedCalls = new AtomicLong();
    private final AtomicLong timeoutCalls = new AtomicLong();
    private final AtomicLong networkErrorCalls = new AtomicLong();
    private final AtomicLong totalCostMs = new AtomicLong();
    private final AtomicLong totalPromptTokens = new AtomicLong();
    private final AtomicLong totalCompletionTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final ConcurrentHashMap<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();

    private volatile VKAiConfig config = new VKAiConfig();
    private volatile boolean initialized;

    private VKAiRuntime() {
    }

    public static VKAiRuntime getInstance() {
        return INSTANCE;
    }

    public void init(VKAiConfig cfg) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = cfg == null ? new VKAiConfig() : cfg.copy();
            initialized = true;
            providerHttpClients.clear();
        }
    }

    public void reinit(VKAiConfig cfg) {
        synchronized (LOCK) {
            config = cfg == null ? new VKAiConfig() : cfg.copy();
            initialized = true;
            providerHttpClients.clear();
            models.clear();
            modelNamesByType.clear();
            // Bug 6 修复：reinit 重置向量库，与 RAG 索引元数据保持一致
            vectorStore = new VKAiInMemoryVectorStore();
            resetMetrics();
            clearAudits();
            clearRagIndexes();
        }
    }

    public boolean started() {
        return initialized;
    }

    public VKAiConfig config() {
        return config.copy();
    }

    public void close() {
        synchronized (LOCK) {
            models.clear();
            modelNamesByType.clear();
            providerHttpClients.clear();
            tools.clear();
            clearAudits();
            clearRagIndexes();
            // Bug 6 修复：close 同步重置向量库，防止旧数据泄漏到下次 init
            vectorStore = new VKAiInMemoryVectorStore();
            try {
                memoryStore.close();
            } catch (Throwable ignore) {
            }
            memoryStore = new VKAiInMemoryMemoryStore();
            promptRegistry.clear();
            chatSessionLocks.clear();
            resetMetrics();
            config = new VKAiConfig();
            initialized = false;
        }
    }

    public void registerModel(String name, VKAiModelConfig cfg) {
        ensureInit();
        if (name == null || name.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Model name is blank");
        }
        if (cfg == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiModelConfig is null");
        }
        if (cfg.getType() == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model type is blank");
        }
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model baseUrl is blank");
        }
        if (cfg.getProvider() == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model provider is blank");
        }
        if (cfg.getModel() == null || cfg.getModel().isBlank()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model value is blank");
        }
        if (cfg.getPath() == null || cfg.getPath().isBlank()) {
            String defaultPath = cfg.getProvider().defaultPath(cfg.getType());
            if (defaultPath == null || defaultPath.isBlank()) {
                throw new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                        "Model path is blank and provider has no default path: " + cfg.getProvider() + ", type=" + cfg.getType());
            }
            cfg = cfg.copy().path(defaultPath);
        }
        String trimmedName = name.trim();
        // 若覆盖旧 model，先从旧 type 的索引中移除
        VKAiModelConfig old = models.get(trimmedName);
        if (old != null && old.getType() != null) {
            Set<String> oldSet = modelNamesByType.get(old.getType());
            if (oldSet != null) {
                oldSet.remove(trimmedName);
            }
        }
        models.put(trimmedName, cfg.copy());
        // Perf 6：维护反向索引
        modelNamesByType.computeIfAbsent(cfg.getType(), k -> ConcurrentHashMap.newKeySet()).add(trimmedName);
        providerHttpClients.clear();
    }

    public void setMemoryStore(VKAiMemoryStore store) {
        ensureInit();
        if (store == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiMemoryStore is null");
        }
        this.memoryStore = store;
    }

    public VKAiSession createSession(String model) {
        ensureInit();
        ModelResolved chatModel = resolveModel(model, VKAiModelType.CHAT, "chat");
        return VKAiSessionOps.createSession(memoryStore, chatModel.modelName);
    }

    public VKAiSession session(String sessionId) {
        ensureInit();
        return VKAiSessionOps.requireSession(memoryStore, sessionId);
    }

    public VKAiSession switchSessionModel(String sessionId, String model) {
        ensureInit();
        return VKAiSessionOps.switchSessionModel(memoryStore, sessionId, model);
    }

    public List<VKAiSessionMessage> sessionMessages(String sessionId) {
        ensureInit();
        return VKAiSessionOps.sessionMessages(memoryStore, sessionId);
    }

    public VKAiChatResponse chatSession(String sessionId, String userText) {
        ensureInit();
        // Bug 4 修复：per-session 锁防止并发 chatSession 计算出相同 seq
        Object lock = chatSessionLocks.computeIfAbsent(
                sessionId != null ? sessionId : "", k -> new Object());
        synchronized (lock) {
            return VKAiSessionOps.chatSession(memoryStore, sessionId, userText, this::chat);
        }
    }

    public void deleteSession(String sessionId) {
        ensureInit();
        VKAiSessionOps.deleteSession(memoryStore, sessionId);
        // 删除会话后清理对应的 per-session 锁，避免 map 无限增长
        if (sessionId != null && !sessionId.isBlank()) {
            chatSessionLocks.remove(sessionId);
        }
    }

    // Ext 6：更新会话 metadata
    public VKAiSession updateSessionMetadata(String sessionId, java.util.Map<String, String> metadata) {
        ensureInit();
        return VKAiSessionOps.updateSessionMetadata(memoryStore, sessionId, metadata);
    }

    // Ext 3：Prompt 模板注册中心

    public void registerPrompt(VKAiPromptTemplate template) {
        ensureInit();
        if (template == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Prompt template is null");
        }
        promptRegistry.register(template);
    }

    public java.util.Map<String, String> renderPrompt(String name, java.util.Map<String, ?> vars) {
        ensureInit();
        return promptRegistry.render(name, vars);
    }

    public Set<String> promptNames() {
        return promptRegistry.names();
    }

    public void registerTool(VKAiTool tool) {
        ensureInit();
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Tool/name is blank");
        }
        tools.put(tool.name().trim(), tool);
    }

    public void clearTools() {
        tools.clear();
    }

    public Set<String> toolNames() {
        return Set.copyOf(tools.keySet());
    }

    public void setVectorStore(VKAiVectorStore store) {
        // Bug 7 修复：与 setMemoryStore 保持一致，必须先初始化才能设置向量库
        ensureInit();
        if (store == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiVectorStore is null");
        }
        this.vectorStore = store;
        clearRagIndexes();
    }

    public void upsertVectorDocs(List<VKAiVectorDoc> docs) {
        ensureInit();
        vectorStore.upsert(docs);
    }

    public List<VKAiVectorHit> searchVector(List<Double> queryVector, int topK) {
        ensureInit();
        return vectorStore.search(queryVector, topK);
    }

    public List<VKAiVectorHit> searchKeywords(String query, int topK) {
        ensureInit();
        return vectorStore.searchByKeywords(query, topK);
    }

    public VKAiRagIngestResult ingestRagDocument(VKAiRagIngestRequest request) {
        ensureInit();
        if (request == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG ingest request is null");
        }
        String docId = request.getDocumentId();
        if (docId == null || docId.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG ingest documentId is blank");
        }
        if (request.getText() == null || request.getText().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG ingest text is blank");
        }
        String version = request.getVersion() == null || request.getVersion().isBlank()
                ? "v1"
                : request.getVersion().trim();

        removeOldVersions(docId, version);

        List<String> chunks = VKAiTextChunker.chunk(request.getText(), request.getChunkSize(), request.getChunkOverlap());
        if (chunks.isEmpty()) {
            return new VKAiRagIngestResult(docId, version, 0, 0, 0, List.of());
        }

        // Bug 3 修复：分两步处理 dedup，先计算 fingerprint 并找出哪些块需要重新 embed
        // batchSeenForEmbed：记录本批次已安排 embed 的 fingerprint，防止批次内同指纹重复调用 API
        String[] fingerprints = new String[chunks.size()];
        List<Integer> toEmbedIndices = new ArrayList<>(chunks.size());
        List<String> toEmbedTexts = new ArrayList<>(chunks.size());
        Set<String> batchSeenForEmbed = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            String fp = VKAiRagOps.sha256Hex(VKAiRagOps.normalizeForDedup(chunks.get(i)));
            fingerprints[i] = fp;
            // 若 dedup 开启且已有跨调用缓存向量或批次内已安排 embed，则跳过 embed API 调用
            if (!request.isDeduplicate() || (!fingerprintToVector.containsKey(fp) && batchSeenForEmbed.add(fp))) {
                toEmbedIndices.add(i);
                toEmbedTexts.add(chunks.get(i));
                batchSeenForEmbed.add(fp);
            }
        }

        // 仅对无缓存的块调用 embed API
        List<VKAiEmbedding> embeddings = toEmbedTexts.isEmpty() ? List.of()
                : embed(new VKAiEmbeddingRequest().model(request.getModel()).inputs(toEmbedTexts));
        if (embeddings.size() != toEmbedTexts.size()) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Embedding result size mismatch for RAG ingest");
        }

        // 建立 chunkIndex → vector 的映射（仅新 embed 的块）
        Map<Integer, List<Double>> embeddingByChunkIdx = new HashMap<>(toEmbedIndices.size() * 2);
        for (int j = 0; j < toEmbedIndices.size(); j++) {
            embeddingByChunkIdx.put(toEmbedIndices.get(j), embeddings.get(j).getVector());
        }

        List<VKAiVectorDoc> docs = new ArrayList<>(chunks.size());
        List<String> insertedChunkIds = new ArrayList<>(chunks.size());
        int skipped = 0;
        // batchInsertedFingerprints：记录本批次已插入 vectorStore 的 fingerprint，dedup 时跳过重复插入
        Set<String> batchInsertedFingerprints = new HashSet<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String fingerprint = fingerprints[i];
            String chunkId = docId + ":" + version + ":" + i;

            // dedup 开启时，批次内重复 fingerprint 跳过插入（不同文档/版本的相同 fingerprint 不受此限制）
            if (request.isDeduplicate() && !batchInsertedFingerprints.add(fingerprint)) {
                skipped++;
                continue;
            }

            List<Double> vector;
            if (embeddingByChunkIdx.containsKey(i)) {
                // 新 embed：缓存到 fingerprintToVector 供后续跨调用 dedup 复用
                vector = embeddingByChunkIdx.get(i);
                fingerprintToVector.putIfAbsent(fingerprint, vector);
            } else {
                // 跨调用 dedup 命中：复用已有向量，避免重复 API 调用
                vector = fingerprintToVector.get(fingerprint);
                if (vector == null) {
                    // 极端情况：缓存被并发清除，降级为重新 embed
                    List<VKAiEmbedding> fallback = embed(new VKAiEmbeddingRequest()
                            .model(request.getModel()).input(chunk));
                    vector = fallback.isEmpty() ? List.of() : fallback.get(0).getVector();
                    fingerprintToVector.putIfAbsent(fingerprint, vector);
                }
            }

            Map<String, String> metadata = new LinkedHashMap<>(request.getMetadata());
            metadata.put("vk_doc_id", docId);
            metadata.put("vk_doc_version", version);
            metadata.put("vk_chunk_index", String.valueOf(i));
            metadata.put("vk_chunk_size", String.valueOf(chunk.length()));
            docs.add(new VKAiVectorDoc(chunkId, chunk, vector, metadata));
            insertedChunkIds.add(chunkId);

            chunkFingerprintById.put(chunkId, fingerprint);
            fingerprintRefCounts.computeIfAbsent(fingerprint, k -> new AtomicLong()).incrementAndGet();
        }

        if (!docs.isEmpty()) {
            vectorStore.upsert(docs);
        }
        docVersionChunks.put(VKAiRagOps.docKey(docId, version), new LinkedHashSet<>(insertedChunkIds));
        latestVersionByDoc.put(docId, version);
        return new VKAiRagIngestResult(docId, version, chunks.size(), insertedChunkIds.size(), skipped, insertedChunkIds);
    }

    public void clearVectorStore() {
        ensureInit();
        vectorStore.clear();
        clearRagIndexes();
    }

    public VKAiToolCallResult callTool(VKAiToolCall call, Set<String> requestAllowedTools, String resolvedClientName) {
        ensureInit();
        if (!config.isToolCallingEnabled()) {
            throw new VKAiException(VKAiErrorCode.TOOL_DENIED, "Tool calling is disabled");
        }
        if (call == null || call.getName() == null || call.getName().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Tool call/name is blank");
        }

        String toolName = call.getName().trim();
        if (requestAllowedTools != null && !requestAllowedTools.isEmpty() && !requestAllowedTools.contains(toolName)) {
            audit("TOOL_DENIED", resolvedClientName, "tool=" + toolName + ", reason=not_allowed");
            throw new VKAiException(VKAiErrorCode.TOOL_DENIED, "Tool is not allowed: " + toolName);
        }

        VKAiTool tool = tools.get(toolName);
        if (tool == null) {
            audit("TOOL_MISSING", resolvedClientName, "tool=" + toolName);
            throw new VKAiException(VKAiErrorCode.TOOL_NOT_FOUND, "Tool not found: " + toolName);
        }

        String input = call.getArgumentsJson() == null ? "{}" : call.getArgumentsJson();
        if (config.isSecurityCheckEnabled()) {
            String risk = VKAiSecurityOps.firstToolInputSecurityRisk(input);
            if (risk != null && config.isBlockOnSecurityRisk()) {
                audit("TOOL_SECURITY_BLOCK", resolvedClientName, "tool=" + toolName + ", risk=" + risk);
                throw new VKAiException(VKAiErrorCode.SECURITY_BLOCKED,
                        "Tool input blocked by security policy: " + risk);
            }
        }

        long start = System.currentTimeMillis();
        try {
            VKAiToolResult result = tool.invoke(input);
            String output = result == null ? "{}" : result.getOutputJson();
            long costMs = System.currentTimeMillis() - start;
            VKAiToolCallResult callResult = new VKAiToolCallResult(call.getId(), toolName, true, output, null, costMs);
            audit("TOOL_CALL", resolvedClientName,
                    "tool=" + toolName + ", success=true, costMs=" + costMs + ", output="
                            + VKAiRuntimeSupportOps.abbreviate(output, 180));
            return callResult;
        } catch (VKAiException e) {
            throw e;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            audit("TOOL_CALL", resolvedClientName,
                    "tool=" + toolName + ", success=false, costMs=" + costMs + ", err="
                            + VKAiRuntimeSupportOps.abbreviate(e.getMessage(), 160));
            throw new VKAiException(VKAiErrorCode.TOOL_EXECUTION_ERROR, "Tool execute failed: " + toolName, e);
        }
    }

    /**
     * 返回最近 limit 条 audit 记录。
     * Bug 10 修复：limit <= 0 表示返回全部记录（不再因 limit=0 而静默返回空列表）。
     */
    public List<VKAiAuditRecord> audits(int limit) {
        synchronized (auditRecords) {
            if (auditRecords.isEmpty()) {
                return List.of();
            }
            // limit <= 0 返回全部
            if (limit <= 0) {
                return List.copyOf(auditRecords);
            }
            List<VKAiAuditRecord> out = new ArrayList<>(Math.min(limit, auditRecords.size()));
            int skip = Math.max(0, auditRecords.size() - limit);
            int i = 0;
            for (VKAiAuditRecord it : auditRecords) {
                if (i++ < skip) {
                    continue;
                }
                out.add(it);
            }
            return out;
        }
    }

    public void clearAudits() {
        synchronized (auditRecords) {
            auditRecords.clear();
        }
    }

    public VKAiChatResponse chat(VKAiChatRequest request) {
        ensureInit();
        if (request == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiChatRequest is null");
        }

        long start = System.currentTimeMillis();
        totalCalls.incrementAndGet();
        Resolved resolved = resolve(request);

        if (config.isSecurityCheckEnabled()) {
            String risk = VKAiSecurityOps.firstSecurityRiskFromPrompt(resolved.systemPrompt, resolved.messages);
            if (risk != null && config.isBlockOnSecurityRisk()) {
                recordFail(start, false, false);
                audit("CHAT_SECURITY_BLOCK", resolved.clientName, "risk=" + risk);
                throw new VKAiException(VKAiErrorCode.SECURITY_BLOCKED, "Chat input blocked by security policy: " + risk);
            }
        }

        audit("CHAT_REQUEST", resolved.clientName,
                "model=" + resolved.model + ", msgCount=" + resolved.messages.size() + ", stream=" + request.isStream()
                        + ", allowedTools=" + resolved.allowedTools);

        if (request.isStream()) {
            return chatStream(resolved, start);
        }
        return chatNonStream(resolved, start);
    }

    public <T> T chatJson(VKAiChatRequest request, Class<T> type) {
        if (type == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Type is null");
        }
        if (request != null && request.isStream()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "chatJson does not support stream=true");
        }
        VKAiChatResponse response = chat(request);
        try {
            return VKJson.fromJson(response.getText(), type);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.JSON_PARSE_ERROR, "Failed to parse chat content as JSON", e);
        }
    }

    public CompletableFuture<VKAiChatResponse> chatAsync(VKAiChatRequest request) {
        // Bug 8 修复：使用专用 IO 线程池，避免阻塞 ForkJoinPool.commonPool
        return CompletableFuture.supplyAsync(() -> chat(request), AI_IO_EXECUTOR);
    }

    public <T> CompletableFuture<T> chatJsonAsync(VKAiChatRequest request, Class<T> type) {
        // Bug 8 修复：同上
        return CompletableFuture.supplyAsync(() -> chatJson(request, type), AI_IO_EXECUTOR);
    }

    private VKAiChatResponse chatNonStream(Resolved resolved, long start) {
        // Ext 2：Agentic 工具循环所需的可追加消息列表（从原始 requestBody 解析出的 messages）
        List<Map<String, Object>> agentMessages = null;
        VKAiChatResponse lastResponse = null;
        int agentLoops = 0;
        int maxAgentLoops = (config.isAgentLoopEnabled() && config.isToolCallingEnabled())
                ? config.getMaxAgentLoops() : 0;

        Resolved currentResolved = resolved;

        outer:
        while (true) {
            int maxRetries = currentResolved.maxRetries;
            for (int attempt = 0; ; attempt++) {
                if (attempt > 0) {
                    retriedCalls.incrementAndGet();
                }
                try {
                    VKAiTransportOps.HttpResult response = VKAiTransportOps.executeHttpJson(
                            providerHttpClients,
                            currentResolved.clientName,
                            currentResolved.url,
                            currentResolved.headers,
                            currentResolved.requestBody,
                            currentResolved.connectTimeoutMs,
                            currentResolved.readTimeoutMs,
                            false,
                            false
                    );
                    int status = response.statusCode();
                    statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();

                    if (shouldRetryByStatus(status, attempt, maxRetries)) {
                        sleepBackoff(attempt);
                        continue;
                    }

                    if (currentResolved.failOnNon2xx && (status < 200 || status >= 300)) {
                        recordFail(start, false, false);
                        throw new VKAiException(VKAiErrorCode.HTTP_STATUS,
                                "AI chat failed with status=" + status + ", body="
                                        + VKAiRuntimeSupportOps.abbreviate(response.body(), 256), status);
                    }

                    VKAiJsonOps.ParsedProviderResponse parsed = VKAiJsonOps.parseProviderResponse(response.body());

                    // Ext 7：token 预算检查
                    checkTokenBudget(resolved.tokenBudgetTokens, parsed.usage(), currentResolved.clientName);

                    List<VKAiToolCallResult> toolResults = executeToolCalls(parsed.toolCalls(), currentResolved);

                    VKAiChatResponse chatResponse = new VKAiChatResponse(
                            parsed.text(),
                            parsed.finishReason(),
                            parsed.usage(),
                            System.currentTimeMillis() - start,
                            parsed.providerRequestId(),
                            status,
                            toolResults
                    );

                    if (config.isMetricsEnabled()) {
                        successCalls.incrementAndGet();
                        totalCostMs.addAndGet(chatResponse.getLatencyMs());
                        totalPromptTokens.addAndGet(chatResponse.getUsage().getPromptTokens());
                        totalCompletionTokens.addAndGet(chatResponse.getUsage().getCompletionTokens());
                        totalTokens.addAndGet(chatResponse.getUsage().getTotalTokens());
                    }
                    if (config.isLogEnabled()) {
                        logCall(currentResolved, status, chatResponse.getLatencyMs(), attempt);
                    }
                    audit("CHAT_RESPONSE", currentResolved.clientName,
                            "status=" + status + ", finishReason=" + chatResponse.getFinishReason()
                                    + ", toolCalls=" + toolResults.size()
                                    + ", agentLoop=" + agentLoops);

                    lastResponse = chatResponse;

                    // Ext 2：Agentic 循环 — 若 finish_reason=tool_calls 且未超限则继续
                    if ("tool_calls".equals(parsed.finishReason())
                            && !parsed.toolCalls().isEmpty()
                            && agentLoops < maxAgentLoops) {
                        agentLoops++;
                        // 延迟初始化 agentMessages
                        if (agentMessages == null) {
                            agentMessages = extractMessagesFromPayload(currentResolved.requestBody);
                        }
                        // 追加 assistant 消息（含 tool_calls）
                        agentMessages.add(buildAssistantToolCallsMessage(parsed));
                        // 追加每个 tool 结果消息
                        for (VKAiToolCallResult tr : toolResults) {
                            agentMessages.add(buildToolResultMessage(tr));
                        }
                        // 重建 payload 并继续循环
                        currentResolved = rebuildResolved(currentResolved, agentMessages);
                        continue outer;
                    }
                    break outer;

                } catch (VKAiException e) {
                    // Bug 2 修复：HTTP_STATUS 重试耗尽后必须 recordFail，不能静默 throw
                    if (e.getCode() == VKAiErrorCode.HTTP_STATUS) {
                        if (shouldRetryByStatus(e.getStatusCode(), attempt, maxRetries)) {
                            sleepBackoff(attempt);
                            continue;
                        }
                        recordFail(start, false, false);
                    }
                    if (e.getCode() == VKAiErrorCode.TIMEOUT) {
                        if (shouldRetryByTimeout(attempt, maxRetries)) {
                            sleepBackoff(attempt);
                            continue;
                        }
                        recordFail(start, true, false);
                    }
                    if (e.getCode() == VKAiErrorCode.NETWORK_ERROR) {
                        if (shouldRetryByNetwork(attempt, maxRetries)) {
                            sleepBackoff(attempt);
                            continue;
                        }
                        recordFail(start, false, true);
                    }
                    throw e;
                }
            }
        }
        return lastResponse;
    }

    // -------------------------------------------------------------------------
    // Ext 2：Agentic 循环辅助方法
    // -------------------------------------------------------------------------

    /** 从已序列化的 requestBody JSON 中解析 messages 列表（用于 agent loop 追加消息）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMessagesFromPayload(String requestBody) {
        try {
            Map<?, ?> root = VKJson.fromJson(requestBody, Map.class);
            Object msgs = root.get("messages");
            if (msgs instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>(list.size() + 4);
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> copy = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : m.entrySet()) {
                            if (e.getKey() != null) {
                                copy.put(String.valueOf(e.getKey()), e.getValue());
                            }
                        }
                        out.add(copy);
                    }
                }
                return out;
            }
        } catch (Exception ignore) {
        }
        return new ArrayList<>();
    }

    /** 构造 OpenAI 格式的 assistant 消息（含 tool_calls 字段）。 */
    private Map<String, Object> buildAssistantToolCallsMessage(VKAiJsonOps.ParsedProviderResponse parsed) {
        List<Map<String, Object>> toolCallsList = new ArrayList<>();
        for (yueyang.vostok.ai.tool.VKAiToolCall tc : parsed.toolCalls()) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tc.getName());
            fn.put("arguments", tc.getArgumentsJson() == null ? "{}" : tc.getArgumentsJson());
            Map<String, Object> tcMap = new LinkedHashMap<>();
            tcMap.put("id", tc.getId() == null ? "" : tc.getId());
            tcMap.put("type", "function");
            tcMap.put("function", fn);
            toolCallsList.add(tcMap);
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");
        msg.put("content", parsed.text() == null ? "" : parsed.text());
        msg.put("tool_calls", toolCallsList);
        return msg;
    }

    /** 构造 OpenAI 格式的 tool result 消息。 */
    private Map<String, Object> buildToolResultMessage(VKAiToolCallResult result) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", result.getCallId() == null ? "" : result.getCallId());
        msg.put("content", result.getOutputJson() == null ? "{}" : result.getOutputJson());
        return msg;
    }

    /** 用新消息列表重建 Resolved（保持其他字段不变，仅替换 requestBody）。 */
    private Resolved rebuildResolved(Resolved base, List<Map<String, Object>> newMessages) {
        try {
            Map<?, ?> original = VKJson.fromJson(base.requestBody, Map.class);
            Map<String, Object> newPayload = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : original.entrySet()) {
                if (e.getKey() != null && !"messages".equals(String.valueOf(e.getKey()))) {
                    newPayload.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            newPayload.put("messages", newMessages);
            return new Resolved(
                    base.clientName, base.model, base.url,
                    base.systemPrompt, base.messages,
                    VKJson.toJson(newPayload),
                    base.headers, base.connectTimeoutMs, base.readTimeoutMs,
                    base.maxRetries, base.failOnNon2xx, base.allowedTools,
                    base.tokenBudgetTokens
            );
        } catch (Exception e) {
            return base;
        }
    }

    // -------------------------------------------------------------------------
    // Ext 7：Token 预算检查
    // -------------------------------------------------------------------------

    private void checkTokenBudget(Integer requestBudget, VKAiUsage usage, String clientName) {
        if (usage == null) {
            return;
        }
        // 请求级预算优先；0 = 不限制
        int budget = requestBudget != null ? requestBudget : config.getTokenBudgetPerRequest();
        if (budget <= 0) {
            return;
        }
        int total = usage.getTotalTokens();
        if (total > budget) {
            audit("TOKEN_BUDGET_EXCEEDED", clientName,
                    "budget=" + budget + ", actual=" + total);
            throw new VKAiException(VKAiErrorCode.TOKEN_BUDGET_EXCEEDED,
                    "Token budget exceeded: budget=" + budget + ", actual=" + total);
        }
    }

    private VKAiChatResponse chatStream(Resolved resolved, long start) {
        int maxRetries = resolved.maxRetries;
        for (int attempt = 0; ; attempt++) {
            if (attempt > 0) {
                retriedCalls.incrementAndGet();
            }
            try {
                final AtomicLong statusRef = new AtomicLong(0);
                final AtomicReference<VKHttpStreamSession> sessionRef = new AtomicReference<>();
                VKAiChatDeltaStreamImpl stream = new VKAiChatDeltaStreamImpl(() -> {
                    VKHttpStreamSession session = sessionRef.get();
                    if (session != null) {
                        session.cancel();
                    }
                });
                VKHttpRequest httpRequest = VKAiTransportOps.buildHttpRequest(
                        providerHttpClients,
                        resolved.clientName,
                        resolved.url,
                        resolved.headers,
                        resolved.requestBody,
                        resolved.connectTimeoutMs,
                        resolved.readTimeoutMs,
                        true,
                        resolved.failOnNon2xx
                );
                VKHttpStreamSession session = Vostok.Http.openSse(httpRequest, new VKHttpSseListener() {
                    @Override
                    public void onOpen(VKHttpResponseMeta meta) {
                        if (meta != null) {
                            statusRef.set(meta.statusCode());
                        }
                    }

                    @Override
                    public void onEvent(VKHttpSseEvent event) {
                        if (event == null) {
                            return;
                        }
                        try {
                            boolean done = handleSseData(event.getData(), stream);
                            if (done) {
                                stream.complete();
                                VKHttpStreamSession session = sessionRef.get();
                                if (session != null) {
                                    session.cancel();
                                }
                            }
                        } catch (VKAiException ex) {
                            stream.fail(ex);
                            VKHttpStreamSession session = sessionRef.get();
                            if (session != null) {
                                session.cancel();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        stream.fail(VKAiTransportOps.mapStreamThrowable(t));
                    }

                    @Override
                    public void onComplete() {
                        stream.complete();
                    }
                });
                sessionRef.set(session);
                int status = statusRef.get() > 0 ? (int) statusRef.get() : 200;
                statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();

                long latency = System.currentTimeMillis() - start;
                // Bug 1 修复：不在此处计 successCalls，改由 stream 的 terminateCallback 在流真正结束时触发
                if (config.isMetricsEnabled()) {
                    final long streamStart = start;
                    final Resolved resolvedRef = resolved;
                    final int statusFinal = status;
                    final int attemptFinal = attempt;
                    stream.onTerminate(success -> {
                        long streamLatency = System.currentTimeMillis() - streamStart;
                        if (success) {
                            successCalls.incrementAndGet();
                        } else {
                            recordFail(streamStart, false, false);
                        }
                        totalCostMs.addAndGet(streamLatency);
                        if (config.isLogEnabled()) {
                            logCall(resolvedRef, statusFinal, streamLatency, attemptFinal);
                        }
                    });
                }
                audit("CHAT_STREAM_RESPONSE", resolved.clientName, "status=" + status);
                return new VKAiChatResponse(
                        null,
                        null,
                        new VKAiUsage(0, 0, 0),
                        latency,
                        null,
                        status,
                        List.of(),
                        true,
                        stream
                );
            } catch (VKAiException e) {
                if (e.getCode() == VKAiErrorCode.HTTP_STATUS && shouldRetryByStatus(e.getStatusCode(), attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                if (e.getCode() == VKAiErrorCode.TIMEOUT) {
                    if (shouldRetryByTimeout(attempt, maxRetries)) {
                        sleepBackoff(attempt);
                        continue;
                    }
                    recordFail(start, true, false);
                }
                if (e.getCode() == VKAiErrorCode.NETWORK_ERROR) {
                    if (shouldRetryByNetwork(attempt, maxRetries)) {
                        sleepBackoff(attempt);
                        continue;
                    }
                    recordFail(start, false, true);
                }
                throw e;
            } catch (VKHttpException e) {
                if (e.getCode() == VKHttpErrorCode.HTTP_STATUS && e.getStatusCode() != null) {
                    statusCounts.computeIfAbsent(e.getStatusCode(), k -> new AtomicLong()).incrementAndGet();
                }
                VKAiException mapped = VKAiTransportOps.mapHttpException("AI chat stream failed", e, true);
                if (mapped.getCode() == VKAiErrorCode.HTTP_STATUS && shouldRetryByStatus(mapped.getStatusCode(), attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                if (mapped.getCode() == VKAiErrorCode.TIMEOUT && shouldRetryByTimeout(attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                if (mapped.getCode() == VKAiErrorCode.NETWORK_ERROR && shouldRetryByNetwork(attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                if (mapped.getCode() == VKAiErrorCode.TIMEOUT) {
                    recordFail(start, true, false);
                } else if (mapped.getCode() == VKAiErrorCode.NETWORK_ERROR) {
                    recordFail(start, false, true);
                }
                throw mapped;
            }
        }
    }

    private boolean handleSseData(String data, VKAiChatDeltaStreamImpl stream) {
        if (data == null || data.isBlank()) {
            return false;
        }
        if ("[DONE]".equals(data)) {
            return true;
        }
        Map<?, ?> root;
        try {
            root = VKJson.fromJson(data, Map.class);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Invalid stream chunk JSON", e);
        }
        Object idObj = root.get("id");
        if (idObj != null) {
            stream.setProviderRequestId(String.valueOf(idObj));
        }
        List<?> choices = VKAiJsonOps.asList(root.get("choices"));
        for (Object it : choices) {
            Map<?, ?> choice = VKAiJsonOps.asMap(it);
            Map<?, ?> delta = VKAiJsonOps.asMap(choice.get("delta"));
            Object content = delta.get("content");
            if (content != null) {
                stream.emitDelta(String.valueOf(content));
            }
            Object finish = choice.get("finish_reason");
            if (finish != null) {
                stream.setFinishReason(String.valueOf(finish));
            }
            // Ext 1：累积流式 tool_calls delta（OpenAI streaming tool call 格式）
            List<?> toolCallDeltas = VKAiJsonOps.asList(delta.get("tool_calls"));
            for (Object tcRaw : toolCallDeltas) {
                Map<?, ?> tcMap = VKAiJsonOps.asMap(tcRaw);
                int tcIndex = VKAiJsonOps.asInt(tcMap.get("index"));
                stream.accumulateToolCallDelta(tcIndex, tcMap);
            }
        }
        Map<?, ?> usage = VKAiJsonOps.asMap(root.get("usage"));
        if (!usage.isEmpty()) {
            stream.setFinalUsage(new VKAiUsage(
                    VKAiJsonOps.asInt(usage.get("prompt_tokens")),
                    VKAiJsonOps.asInt(usage.get("completion_tokens")),
                    VKAiJsonOps.asInt(usage.get("total_tokens"))
            ));
        }
        return false;
    }

    public List<VKAiEmbedding> embed(VKAiEmbeddingRequest request) {
        ensureInit();
        if (request == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiEmbeddingRequest is null");
        }
        if (request.getInputs().isEmpty()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Embedding inputs are empty");
        }
        ModelResolved modelResolved = resolveModel(request.getModel(), VKAiModelType.EMBEDDING, "embedding");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelResolved.providerModel());
        payload.put("input", request.getInputs());
        String payloadJson = VKJson.toJson(payload);
        String cacheKey = "vk:ai:embed:"
                + VKAiRuntimeSupportOps.shortHash(modelResolved.modelName + "|" + payloadJson);

        String responseBody = null;
        if (config.isRagCacheEnabled() && config.getEmbeddingCacheTtlMs() > 0) {
            responseBody = cacheGetString(cacheKey);
            if (responseBody != null) {
                audit("EMBED_CACHE_HIT", modelResolved.modelName, "key=" + cacheKey);
            }
        }
        if (responseBody == null) {
            VKAiTransportOps.HttpResult response = executeWithRetry(
                    modelResolved.modelName,
                    VKAiRuntimeSupportOps.joinBaseAndPath(modelResolved.model.getBaseUrl(), modelResolved.model.getPath()),
                    buildHeaders(modelResolved.model),
                    payloadJson,
                    modelResolved.model.getConnectTimeoutMs() > 0 ? modelResolved.model.getConnectTimeoutMs() : config.getConnectTimeoutMs(),
                    modelResolved.model.getReadTimeoutMs() > 0 ? modelResolved.model.getReadTimeoutMs() : config.getReadTimeoutMs(),
                    modelResolved.model.getMaxRetries() >= 0 ? modelResolved.model.getMaxRetries() : config.getMaxRetries(),
                    modelResolved.model.getFailOnNon2xx() == null ? config.isFailOnNon2xx() : modelResolved.model.getFailOnNon2xx(),
                    "EMBED_REQUEST"
            );
            responseBody = response.body();
            if (config.isRagCacheEnabled() && config.getEmbeddingCacheTtlMs() > 0) {
                cacheSetString(cacheKey, responseBody, config.getEmbeddingCacheTtlMs());
            }
            // Bug 5 修复：重命名为 EMBED_HTTP_RESPONSE，避免与后续 EMBED_RESPONSE 重复
            audit("EMBED_HTTP_RESPONSE", modelResolved.modelName, "status=" + response.statusCode());
        }

        Map<?, ?> root;
        try {
            root = VKJson.fromJson(responseBody, Map.class);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Invalid embedding response JSON", e);
        }
        List<?> data = VKAiJsonOps.asList(root.get("data"));
        List<VKAiEmbedding> out = new ArrayList<>(data.size());
        for (Object item : data) {
            Map<?, ?> m = VKAiJsonOps.asMap(item);
            int index = VKAiJsonOps.asInt(m.get("index"));
            List<Double> vec = VKAiJsonOps.toDoubleList(VKAiJsonOps.asList(m.get("embedding")));
            out.add(new VKAiEmbedding(index, vec));
        }
        // Bug 5 修复：保留一条含向量数量的最终 audit
        audit("EMBED_RESPONSE", modelResolved.modelName, "vectors=" + out.size());
        return out;
    }

    public VKAiRerankResponse rerank(VKAiRerankRequest request) {
        ensureInit();
        if (request == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiRerankRequest is null");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Rerank query is blank");
        }
        if (request.getDocuments().isEmpty()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Rerank documents are empty");
        }

        ModelResolved modelResolved = resolveModel(request.getModel(), VKAiModelType.RERANK, "rerank");

        long start = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelResolved.providerModel());
        payload.put("query", request.getQuery());
        payload.put("documents", request.getDocuments());
        if (request.getTopK() != null) {
            payload.put("top_k", request.getTopK());
        }
        String payloadJson = VKJson.toJson(payload);
        String cacheKey = "vk:ai:rerank:"
                + VKAiRuntimeSupportOps.shortHash(modelResolved.modelName + "|" + payloadJson);

        String responseBody = null;
        if (config.isRagCacheEnabled() && config.getRerankCacheTtlMs() > 0) {
            responseBody = cacheGetString(cacheKey);
            if (responseBody != null) {
                audit("RERANK_CACHE_HIT", modelResolved.modelName, "key=" + cacheKey);
            }
        }
        if (responseBody == null) {
            VKAiTransportOps.HttpResult response = executeWithRetry(
                    modelResolved.modelName,
                    VKAiRuntimeSupportOps.joinBaseAndPath(modelResolved.model.getBaseUrl(), modelResolved.model.getPath()),
                    buildHeaders(modelResolved.model),
                    payloadJson,
                    modelResolved.model.getConnectTimeoutMs() > 0 ? modelResolved.model.getConnectTimeoutMs() : config.getConnectTimeoutMs(),
                    modelResolved.model.getReadTimeoutMs() > 0 ? modelResolved.model.getReadTimeoutMs() : config.getReadTimeoutMs(),
                    modelResolved.model.getMaxRetries() >= 0 ? modelResolved.model.getMaxRetries() : config.getMaxRetries(),
                    modelResolved.model.getFailOnNon2xx() == null ? config.isFailOnNon2xx() : modelResolved.model.getFailOnNon2xx(),
                    "RERANK_REQUEST"
            );
            responseBody = response.body();
            if (config.isRagCacheEnabled() && config.getRerankCacheTtlMs() > 0) {
                cacheSetString(cacheKey, responseBody, config.getRerankCacheTtlMs());
            }
        }

        Map<?, ?> root;
        try {
            root = VKJson.fromJson(responseBody, Map.class);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Invalid rerank response JSON", e);
        }

        List<?> results = VKAiJsonOps.asList(root.get("results"));
        List<VKAiRerankResult> out = new ArrayList<>(results.size());
        for (Object item : results) {
            Map<?, ?> m = VKAiJsonOps.asMap(item);
            int index = VKAiJsonOps.asInt(m.get("index"));
            double score = VKAiJsonOps.asDouble(m.get("score"));
            String doc = index >= 0 && index < request.getDocuments().size() ? request.getDocuments().get(index) : null;
            out.add(new VKAiRerankResult(index, score, doc));
        }
        return new VKAiRerankResponse(out, System.currentTimeMillis() - start);
    }

    public VKAiRagResponse rag(VKAiRagRequest request) {
        ensureInit();
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG query is blank");
        }
        if (request.getChatModel() == null || request.getChatModel().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG chatModel is blank");
        }
        if (request.getEmbeddingModel() == null || request.getEmbeddingModel().isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG embeddingModel is blank");
        }
        if (request.isRerankEnabled() && (request.getRerankModel() == null || request.getRerankModel().isBlank())) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "RAG rerankModel is blank");
        }

        String retrievalQuery = request.isQueryRewriteEnabled()
                ? VKAiRagOps.rewriteRagQueryLight(request.getQuery())
                : request.getQuery();
        int effectiveTopK = request.isDynamicTopKEnabled()
                ? VKAiRagOps.computeDynamicTopK(request.getTopK(), retrievalQuery)
                : request.getTopK();
        int vectorTopK = request.isDynamicTopKEnabled()
                ? VKAiRagOps.dynamicCandidateTopK(request.getVectorTopK(), effectiveTopK)
                : Math.max(request.getTopK(), request.getVectorTopK());
        int keywordTopK = request.isDynamicTopKEnabled()
                ? VKAiRagOps.dynamicCandidateTopK(request.getKeywordTopK(), effectiveTopK)
                : Math.max(request.getTopK(), request.getKeywordTopK());

        List<VKAiEmbedding> queryEmbeddings = embed(new VKAiEmbeddingRequest()
                .model(request.getEmbeddingModel())
                .input(retrievalQuery));
        if (queryEmbeddings.isEmpty()) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Embedding response is empty");
        }

        // Ext 5：将 metadataFilter 透传给向量检索和关键词检索
        java.util.Map<String, String> metaFilter = request.getMetadataFilter();
        List<VKAiVectorHit> vectorHits = vectorStore.search(
                queryEmbeddings.get(0).getVector(), vectorTopK, metaFilter);
        List<VKAiVectorHit> keywordHits = vectorStore.searchByKeywords(
                retrievalQuery, keywordTopK, metaFilter);
        List<VKAiVectorHit> merged = VKAiRagOps.mergeHybridHits(vectorHits, keywordHits, request);
        if (request.isMergeSimilarChunksEnabled()) {
            merged = VKAiRagOps.mergeSimilarChunks(merged);
        }
        List<VKAiVectorHit> hits = request.isRerankEnabled()
                ? rerankHits(request, request.getRerankModel(), merged, retrievalQuery, effectiveTopK)
                : merged;
        if (request.isContextCompressionEnabled()) {
            hits = VKAiRagOps.compressContextHits(hits, effectiveTopK, request.getContextMaxCharsPerChunk(), request.getContextMaxChars());
        }
        if (hits.size() > effectiveTopK) {
            hits = List.copyOf(hits.subList(0, effectiveTopK));
        }

        StringBuilder context = new StringBuilder(256);
        int i = 1;
        for (VKAiVectorHit hit : hits) {
            context.append("[").append(i++).append("] ").append(hit.getText()).append('\n');
        }

        String systemPrompt = request.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "Answer only from context. If unknown, say unknown.";
        }

        String answerCacheKey = "vk:ai:rag-answer:" + VKAiRuntimeSupportOps.shortHash(VKAiRuntimeSupportOps.buildRagAnswerCacheMaterial(
                request,
                request.getChatModel(),
                request.getEmbeddingModel(),
                request.getRerankModel(),
                systemPrompt,
                hits));
        if (config.isRagCacheEnabled() && config.getRagAnswerCacheTtlMs() > 0) {
            String cachedAnswer = cacheGetString(answerCacheKey);
            if (cachedAnswer != null) {
                VKAiChatResponse cached = VKAiRuntimeSupportOps.decodeCachedChatResponse(cachedAnswer);
                if (cached != null) {
                    audit("RAG_ANSWER_CACHE_HIT", request.getChatModel(), "key=" + answerCacheKey);
                    return new VKAiRagResponse(cached, hits);
                }
            }
        }

        VKAiChatResponse answer = chat(new VKAiChatRequest()
                .model(request.getChatModel())
                .system(systemPrompt)
                .message("user", "Question:\n" + request.getQuery() + "\n\nContext:\n" + context));
        if (config.isRagCacheEnabled() && config.getRagAnswerCacheTtlMs() > 0) {
            cacheSetString(answerCacheKey, VKAiRuntimeSupportOps.encodeChatResponseForCache(answer), config.getRagAnswerCacheTtlMs());
        }
        return new VKAiRagResponse(answer, hits);
    }

    public void healthCheckRag(String embeddingModelName, String rerankModelName, boolean includeRerank) {
        ensureInit();
        ModelResolved embeddingModel = resolveModel(embeddingModelName, VKAiModelType.EMBEDDING, "embedding");
        try {
            embed(new VKAiEmbeddingRequest()
                    .model(embeddingModel.modelName)
                    .input("health check"));
        } catch (VKAiException e) {
            throw mapRagPrecheckException("embedding", embeddingModel.modelName, e);
        }

        if (!includeRerank) {
            return;
        }
        ModelResolved rerankModel = resolveModel(rerankModelName, VKAiModelType.RERANK, "rerank");
        try {
            rerank(new VKAiRerankRequest()
                    .model(rerankModel.modelName)
                    .query("health check")
                    .document("doc one")
                    .document("doc two")
                    .topK(1));
        } catch (VKAiException e) {
            throw mapRagPrecheckException("rerank", rerankModel.modelName, e);
        }
    }

    public VKAiMetrics metrics() {
        Map<Integer, Long> status = new LinkedHashMap<>();
        for (Map.Entry<Integer, AtomicLong> e : statusCounts.entrySet()) {
            status.put(e.getKey(), e.getValue().get());
        }
        return new VKAiMetrics(
                totalCalls.get(),
                successCalls.get(),
                failedCalls.get(),
                retriedCalls.get(),
                timeoutCalls.get(),
                networkErrorCalls.get(),
                totalCostMs.get(),
                totalPromptTokens.get(),
                totalCompletionTokens.get(),
                totalTokens.get(),
                Map.copyOf(status)
        );
    }

    public void resetMetrics() {
        totalCalls.set(0);
        successCalls.set(0);
        failedCalls.set(0);
        retriedCalls.set(0);
        timeoutCalls.set(0);
        networkErrorCalls.set(0);
        totalCostMs.set(0);
        totalPromptTokens.set(0);
        totalCompletionTokens.set(0);
        totalTokens.set(0);
        statusCounts.clear();
    }

    private List<VKAiToolCallResult> executeToolCalls(List<VKAiToolCall> calls, Resolved resolved) {
        if (calls.isEmpty()) {
            return List.of();
        }
        if (!config.isToolCallingEnabled()) {
            return List.of();
        }

        int limit = Math.min(calls.size(), config.getMaxToolCallsPerRequest());
        List<VKAiToolCallResult> results = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            VKAiToolCall call = calls.get(i);
            results.add(callTool(call, resolved.allowedTools, resolved.clientName));
        }
        return results;
    }

    private Resolved resolve(VKAiChatRequest request) {
        ModelResolved modelResolved = resolveModel(request.getModel(), VKAiModelType.CHAT, "chat");

        List<VKAiMessage> preparedMessages = VKAiChatRequestOps.trimHistoryMessages(request);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        for (VKAiMessage msg : preparedMessages) {
            if (msg == null || msg.getRole() == null || msg.getRole().isBlank()) {
                continue;
            }
            String content = msg.getContent() == null ? "" : msg.getContent();
            messages.add(Map.of("role", msg.getRole(), "content", content));
        }
        if (messages.isEmpty()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Chat request has no messages");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelResolved.providerModel());
        payload.put("messages", messages);
        if (request.getTemperature() != null) {
            payload.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            payload.put("max_tokens", request.getMaxTokens());
        }
        if (request.isStream()) {
            payload.put("stream", true);
        }
        // Ext 4：结构化输出 / response_format
        if (request.getResponseFormat() != null && !request.getResponseFormat().isBlank()) {
            String fmt = request.getResponseFormat().trim();
            if ("json_schema".equals(fmt) && request.getResponseJsonSchema() != null
                    && !request.getResponseJsonSchema().isBlank()) {
                payload.put("response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", VKAiJsonOps.parseJsonOrEmptyObject(request.getResponseJsonSchema())));
            } else {
                payload.put("response_format", Map.of("type", fmt));
            }
        }

        Set<String> allowedTools = VKAiChatRequestOps.normalizeToolAllowlist(request.getAllowedTools());
        if (!allowedTools.isEmpty()) {
            List<Map<String, Object>> declaredTools = new ArrayList<>();
            for (String toolName : allowedTools) {
                VKAiTool tool = tools.get(toolName);
                if (tool == null) {
                    continue;
                }
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tool.name());
                function.put("description", tool.description() == null ? "" : tool.description());
                function.put("parameters", VKAiJsonOps.parseJsonOrEmptyObject(tool.inputJsonSchema()));
                declaredTools.add(Map.of("type", "function", "function", function));
            }
            if (!declaredTools.isEmpty()) {
                payload.put("tools", declaredTools);
            }
        }

        long connectTimeoutMs = modelResolved.model.getConnectTimeoutMs() > 0 ? modelResolved.model.getConnectTimeoutMs() : config.getConnectTimeoutMs();
        long readTimeoutMs = modelResolved.model.getReadTimeoutMs() > 0 ? modelResolved.model.getReadTimeoutMs() : config.getReadTimeoutMs();
        int maxRetries = modelResolved.model.getMaxRetries() >= 0 ? modelResolved.model.getMaxRetries() : config.getMaxRetries();
        boolean failOnNon2xx = modelResolved.model.getFailOnNon2xx() == null ? config.isFailOnNon2xx() : modelResolved.model.getFailOnNon2xx();

        Map<String, String> headers = buildHeaders(modelResolved.model);

        String url = VKAiRuntimeSupportOps.joinBaseAndPath(modelResolved.model.getBaseUrl(), modelResolved.model.getPath());
        return new Resolved(
                modelResolved.modelName,
                modelResolved.modelName,
                url,
                request.getSystemPrompt(),
                preparedMessages,
                VKJson.toJson(payload),
                Map.copyOf(headers),
                connectTimeoutMs,
                readTimeoutMs,
                maxRetries,
                failOnNon2xx,
                allowedTools,
                request.getTokenBudgetTokens()
        );
    }

    private ModelResolved resolveModel(String requestModel, VKAiModelType expected, String stage) {
        if (requestModel != null && !requestModel.isBlank()) {
            String name = requestModel.trim();
            VKAiModelConfig model = models.get(name);
            if (model == null) {
                throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Ai model not found: " + name + " (" + stage + ")");
            }
            ensureUsableModel(model, name, expected, stage);
            return new ModelResolved(name, model.copy());
        }

        // Perf 6：O(1) 反向索引查找，替代全量遍历
        Set<String> typeSet = modelNamesByType.get(expected);
        List<String> candidates = typeSet == null ? List.of() : new ArrayList<>(typeSet);
        if (candidates.isEmpty()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "No model registered for stage: " + stage);
        }
        if (candidates.size() > 1) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model is required for stage: " + stage);
        }
        String name = candidates.get(0);
        VKAiModelConfig model = models.get(name);
        ensureUsableModel(model, name, expected, stage);
        return new ModelResolved(name, model.copy());
    }

    private void ensureUsableModel(VKAiModelConfig model, String modelName, VKAiModelType expected, String stage) {
        if (model == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Ai model not found: " + modelName + " (" + stage + ")");
        }
        if (model.getType() != expected) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                    "Ai model type mismatch: " + modelName + ", expected=" + expected + ", actual=" + model.getType());
        }
        if (model.getBaseUrl() == null || model.getBaseUrl().isBlank()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model baseUrl is blank: " + modelName);
        }
        if (model.getPath() == null || model.getPath().isBlank()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model path is blank: " + modelName);
        }
        if (model.getModel() == null || model.getModel().isBlank()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Model value is blank: " + modelName);
        }
    }

    private VKAiException mapRagPrecheckException(String stage, String model, VKAiException e) {
        if (e.getCode() == VKAiErrorCode.HTTP_STATUS && e.getStatusCode() != null && e.getStatusCode() == 404) {
            return new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                    "RAG precheck failed at " + stage + " (404). model=" + model
                            + ". Check " + stage + " endpoint path and model layering config.", e);
        }
        return new VKAiException(e.getCode(),
                "RAG precheck failed at " + stage + ". model=" + model + ", cause=" + e.getMessage(), e);
    }

    private Map<String, String> buildHeaders(VKAiModelConfig model) {
        Map<String, String> headers = new LinkedHashMap<>(model.getDefaultHeaders());
        if (model.getApiKey() != null && !model.getApiKey().isBlank()) {
            headers.putIfAbsent("Authorization", "Bearer " + model.getApiKey().trim());
        }
        headers.putIfAbsent("X-Vostok-AI-Provider", model.getProvider().code());
        return headers;
    }

    private VKAiTransportOps.HttpResult executeWithRetry(String clientName,
                                                         String url,
                                                         Map<String, String> headers,
                                                         String body,
                                                         long connectTimeoutMs,
                                                         long readTimeoutMs,
                                                         int maxRetries,
                                                         boolean failOnNon2xx,
                                                         String auditType) {
        long start = System.currentTimeMillis();
        totalCalls.incrementAndGet();
        audit(auditType, clientName, "url=" + url);
        for (int attempt = 0; ; attempt++) {
            if (attempt > 0) {
                retriedCalls.incrementAndGet();
            }
            try {
                VKAiTransportOps.HttpResult response = VKAiTransportOps.executeHttpJson(
                        providerHttpClients,
                        clientName,
                        url,
                        headers,
                        body,
                        connectTimeoutMs,
                        readTimeoutMs,
                        false,
                        false
                );
                int status = response.statusCode();
                statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();

                if (shouldRetryByStatus(status, attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                if (failOnNon2xx && (status < 200 || status >= 300)) {
                    recordFail(start, false, false);
                    throw new VKAiException(VKAiErrorCode.HTTP_STATUS,
                            "AI request failed with status=" + status + ", body="
                                    + VKAiRuntimeSupportOps.abbreviate(response.body(), 256), status);
                }
                if (config.isMetricsEnabled()) {
                    successCalls.incrementAndGet();
                    totalCostMs.addAndGet(System.currentTimeMillis() - start);
                }
                return response;
            } catch (VKAiException e) {
                // Bug 2 修复：HTTP_STATUS 重试耗尽后需要 recordFail
                if (e.getCode() == VKAiErrorCode.HTTP_STATUS) {
                    if (shouldRetryByStatus(e.getStatusCode(), attempt, maxRetries)) {
                        sleepBackoff(attempt);
                        continue;
                    }
                    recordFail(start, false, false);
                    throw e;
                }
                if (e.getCode() == VKAiErrorCode.TIMEOUT) {
                    if (shouldRetryByTimeout(attempt, maxRetries)) {
                        sleepBackoff(attempt);
                        continue;
                    }
                    recordFail(start, true, false);
                    throw e;
                }
                if (e.getCode() == VKAiErrorCode.NETWORK_ERROR) {
                    if (shouldRetryByNetwork(attempt, maxRetries)) {
                        sleepBackoff(attempt);
                        continue;
                    }
                    recordFail(start, false, true);
                    throw e;
                }
                throw e;
            }
        }
    }

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (!initialized) {
                config = new VKAiConfig();
                initialized = true;
                providerHttpClients.clear();
            }
        }
    }

    private void recordFail(long start, boolean timeout, boolean network) {
        if (!config.isMetricsEnabled()) {
            return;
        }
        failedCalls.incrementAndGet();
        totalCostMs.addAndGet(System.currentTimeMillis() - start);
        if (timeout) {
            timeoutCalls.incrementAndGet();
        }
        if (network) {
            networkErrorCalls.incrementAndGet();
        }
    }

    private boolean shouldRetryByStatus(Integer status, int attempt, int maxRetries) {
        if (status == null || attempt >= maxRetries) {
            return false;
        }
        return config.getRetryOnStatuses().contains(status);
    }

    private boolean shouldRetryByTimeout(int attempt, int maxRetries) {
        return config.isRetryOnTimeout() && attempt < maxRetries;
    }

    private boolean shouldRetryByNetwork(int attempt, int maxRetries) {
        return config.isRetryOnNetworkError() && attempt < maxRetries;
    }

    private void sleepBackoff(int attempt) {
        long backoff = config.getRetryBackoffMs();
        for (int i = 0; i < attempt; i++) {
            backoff = Math.min(config.getMaxRetryBackoffMs(), backoff * 2);
        }
        try {
            Thread.sleep(Math.max(1, backoff));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void audit(String type, String clientName, String detail) {
        if (!config.isAuditEnabled()) {
            return;
        }
        VKAiAuditRecord record = new VKAiAuditRecord(System.currentTimeMillis(), type, clientName, detail);
        synchronized (auditRecords) {
            auditRecords.addLast(record);
            while (auditRecords.size() > config.getMaxAuditRecords()) {
                auditRecords.pollFirst();
            }
        }
        if (config.isLogEnabled()) {
            try {
                Vostok.Log.info("Vostok.AI.audit type={} client={} detail={}", type, clientName, detail);
            } catch (Throwable ignore) {
            }
        }
    }

    private void clearRagIndexes() {
        docVersionChunks.clear();
        latestVersionByDoc.clear();
        chunkFingerprintById.clear();
        fingerprintRefCounts.clear();
        // Bug 3 修复：同步清理 fingerprint 向量缓存
        fingerprintToVector.clear();
    }

    private void removeOldVersions(String documentId, String keepVersion) {
        String prevVersion = latestVersionByDoc.get(documentId);
        if (prevVersion == null || prevVersion.equals(keepVersion)) {
            return;
        }
        String key = VKAiRagOps.docKey(documentId, prevVersion);
        Set<String> staleChunkIds = docVersionChunks.remove(key);
        if (staleChunkIds == null || staleChunkIds.isEmpty()) {
            return;
        }
        vectorStore.deleteByIds(new ArrayList<>(staleChunkIds));
        for (String chunkId : staleChunkIds) {
            String fingerprint = chunkFingerprintById.remove(chunkId);
            if (fingerprint == null) {
                continue;
            }
            AtomicLong ref = fingerprintRefCounts.get(fingerprint);
            if (ref == null) {
                continue;
            }
            long left = ref.decrementAndGet();
            if (left <= 0) {
                fingerprintRefCounts.remove(fingerprint);
                // Bug 3 修复：引用计数归零时同步清除向量缓存，防止内存泄漏
                fingerprintToVector.remove(fingerprint);
            }
        }
    }

    private List<VKAiVectorHit> rerankHits(VKAiRagRequest request,
                                           String rerankModelName,
                                           List<VKAiVectorHit> hits,
                                           String retrievalQuery,
                                           int effectiveTopK) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<String> docs = new ArrayList<>(hits.size());
        for (VKAiVectorHit hit : hits) {
            docs.add(hit.getText());
        }
        VKAiRerankRequest rerankRequest = new VKAiRerankRequest()
                .model(request.getRerankModel())
                .query(retrievalQuery)
                .documents(docs)
                .topK(Math.min(effectiveTopK, docs.size()));
        VKAiRerankResponse rerank;
        long timeoutMs = config.getRagRerankTimeoutMs();
        if (timeoutMs > 0) {
            try {
                // Bug 8 修复：使用专用 IO 线程池，不阻塞 ForkJoinPool.commonPool
                rerank = CompletableFuture.supplyAsync(() -> rerank(rerankRequest), AI_IO_EXECUTOR)
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                audit("RAG_DEGRADE", rerankModelName, "reason=rerank_timeout, timeoutMs=" + timeoutMs);
                return hits;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                audit("RAG_DEGRADE", rerankModelName, "reason=rerank_error");
                return hits;
            }
        } else {
            rerank = rerank(rerankRequest);
        }
        List<VKAiVectorHit> out = new ArrayList<>(rerank.getResults().size());
        for (VKAiRerankResult r : rerank.getResults()) {
            if (r.getIndex() < 0 || r.getIndex() >= hits.size()) {
                continue;
            }
            VKAiVectorHit raw = hits.get(r.getIndex());
            out.add(new VKAiVectorHit(raw.getId(), raw.getText(), r.getScore(), raw.getMetadata()));
        }
        return out.isEmpty() ? hits : out;
    }

    private String cacheGetString(String key) {
        if (key == null || key.isBlank() || !config.isRagCacheEnabled()) {
            return null;
        }
        try {
            if (!Vostok.Cache.started()) {
                return null;
            }
            return Vostok.Cache.get(key, String.class);
        } catch (Throwable e) {
            audit("CACHE_BYPASS", null, "op=get,key=" + VKAiRuntimeSupportOps.abbreviate(key, 120));
            return null;
        }
    }

    private void cacheSetString(String key, String value, long ttlMs) {
        if (key == null || key.isBlank() || value == null || !config.isRagCacheEnabled() || ttlMs <= 0) {
            return;
        }
        try {
            if (!Vostok.Cache.started()) {
                return;
            }
            Vostok.Cache.set(key, value, ttlMs);
        } catch (Throwable e) {
            audit("CACHE_BYPASS", null, "op=set,key=" + VKAiRuntimeSupportOps.abbreviate(key, 120));
        }
    }

    private void logCall(Resolved resolved, int status, long costMs, int retries) {
        try {
            Vostok.Log.info("Vostok.AI chat status={} costMs={} retries={} url={} client={}",
                    status,
                    costMs,
                    retries,
                    resolved.url,
                    resolved.clientName == null ? "-" : resolved.clientName);
        } catch (Throwable ignore) {
        }
    }

    private record Resolved(
            String clientName,
            String model,
            String url,
            String systemPrompt,
            List<VKAiMessage> messages,
            String requestBody,
            Map<String, String> headers,
            long connectTimeoutMs,
            long readTimeoutMs,
            int maxRetries,
            boolean failOnNon2xx,
            Set<String> allowedTools,
            // Ext 7：请求级 token 预算（null 表示使用全局配置）
            Integer tokenBudgetTokens
    ) {
    }

    private record ModelResolved(
            String modelName,
            VKAiModelConfig model
    ) {
        String providerModel() {
            return model == null ? null : model.getModel();
        }
    }

}
