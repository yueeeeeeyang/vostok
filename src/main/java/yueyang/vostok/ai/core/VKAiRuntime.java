package yueyang.vostok.ai.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.ai.VKAiAuditRecord;
import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.VKAiChatResponse;
import yueyang.vostok.ai.provider.VKAiClientConfig;
import yueyang.vostok.ai.VKAiConfig;
import yueyang.vostok.ai.rag.VKAiEmbedding;
import yueyang.vostok.ai.rag.VKAiEmbeddingRequest;
import yueyang.vostok.ai.rag.VKAiInMemoryVectorStore;
import yueyang.vostok.ai.VKAiMessage;
import yueyang.vostok.ai.VKAiMetrics;
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
import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.util.json.VKJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VKAiRuntime {
    private static final Object LOCK = new Object();
    private static final VKAiRuntime INSTANCE = new VKAiRuntime();

    private final ConcurrentHashMap<String, VKAiClientConfig> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, HttpClient> httpClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VKAiTool> tools = new ConcurrentHashMap<>();
    private final ArrayDeque<VKAiAuditRecord> auditRecords = new ArrayDeque<>();
    private final ThreadLocal<String> clientContext = new ThreadLocal<>();
    private volatile VKAiVectorStore vectorStore = new VKAiInMemoryVectorStore();
    private final ConcurrentHashMap<String, Set<String>> docVersionChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> latestVersionByDoc = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> chunkFingerprintById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> fingerprintRefCounts = new ConcurrentHashMap<>();

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
            httpClients.clear();
        }
    }

    public void reinit(VKAiConfig cfg) {
        synchronized (LOCK) {
            config = cfg == null ? new VKAiConfig() : cfg.copy();
            initialized = true;
            httpClients.clear();
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
            clients.clear();
            clientContext.remove();
            httpClients.clear();
            tools.clear();
            clearAudits();
            clearRagIndexes();
            resetMetrics();
            config = new VKAiConfig();
            initialized = false;
        }
    }

    public void registerClient(String name, VKAiClientConfig cfg) {
        ensureInit();
        if (name == null || name.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Client name is blank");
        }
        if (cfg == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiClientConfig is null");
        }
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Client baseUrl is blank");
        }
        clients.put(name.trim(), cfg.copy());
    }

    public void withClient(String name, Runnable action) {
        if (action == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Runnable is null");
        }
        withClient(name, () -> {
            action.run();
            return null;
        });
    }

    public <T> T withClient(String name, Supplier<T> supplier) {
        ensureInit();
        if (supplier == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Supplier is null");
        }
        String prev = clientContext.get();
        if (name == null || name.isBlank()) {
            clientContext.remove();
        } else {
            String n = name.trim();
            if (!clients.containsKey(n)) {
                throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Ai client not found: " + n);
            }
            clientContext.set(n);
        }
        try {
            return supplier.get();
        } finally {
            if (prev == null) {
                clientContext.remove();
            } else {
                clientContext.set(prev);
            }
        }
    }

    public Set<String> clientNames() {
        return Set.copyOf(clients.keySet());
    }

    public String currentClientName() {
        return clientContext.get();
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

        List<VKAiEmbedding> embeddings = embed(new VKAiEmbeddingRequest()
                .client(request.getClientName())
                .model(request.getModel())
                .inputs(chunks));
        if (embeddings.size() != chunks.size()) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Embedding result size mismatch for RAG ingest");
        }

        List<VKAiVectorDoc> docs = new ArrayList<>(chunks.size());
        List<String> insertedChunkIds = new ArrayList<>(chunks.size());
        int skipped = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String fingerprint = sha256Hex(normalizeForDedup(chunk));
            if (request.isDeduplicate() && fingerprintRefCounts.containsKey(fingerprint)) {
                skipped++;
                continue;
            }

            String chunkId = docId + ":" + version + ":" + i;
            Map<String, String> metadata = new LinkedHashMap<>(request.getMetadata());
            metadata.put("vk_doc_id", docId);
            metadata.put("vk_doc_version", version);
            metadata.put("vk_chunk_index", String.valueOf(i));
            metadata.put("vk_chunk_size", String.valueOf(chunk.length()));
            docs.add(new VKAiVectorDoc(chunkId, chunk, embeddings.get(i).getVector(), metadata));
            insertedChunkIds.add(chunkId);

            chunkFingerprintById.put(chunkId, fingerprint);
            fingerprintRefCounts.computeIfAbsent(fingerprint, k -> new AtomicLong()).incrementAndGet();
        }

        if (!docs.isEmpty()) {
            vectorStore.upsert(docs);
        }
        docVersionChunks.put(docKey(docId, version), new LinkedHashSet<>(insertedChunkIds));
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
            String risk = firstToolInputSecurityRisk(input);
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
                    "tool=" + toolName + ", success=true, costMs=" + costMs + ", output=" + abbreviate(output, 180));
            return callResult;
        } catch (VKAiException e) {
            throw e;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            audit("TOOL_CALL", resolvedClientName,
                    "tool=" + toolName + ", success=false, costMs=" + costMs + ", err=" + abbreviate(e.getMessage(), 160));
            throw new VKAiException(VKAiErrorCode.TOOL_EXECUTION_ERROR, "Tool execute failed: " + toolName, e);
        }
    }

    public List<VKAiAuditRecord> audits(int limit) {
        int size = Math.max(0, limit);
        synchronized (auditRecords) {
            if (size == 0 || auditRecords.isEmpty()) {
                return List.of();
            }
            List<VKAiAuditRecord> out = new ArrayList<>(Math.min(size, auditRecords.size()));
            int skip = Math.max(0, auditRecords.size() - size);
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
            String risk = firstSecurityRiskFromPrompt(resolved.systemPrompt, resolved.messages);
            if (risk != null && config.isBlockOnSecurityRisk()) {
                recordFail(start, false, false);
                audit("CHAT_SECURITY_BLOCK", resolved.clientName, "risk=" + risk);
                throw new VKAiException(VKAiErrorCode.SECURITY_BLOCKED, "Chat input blocked by security policy: " + risk);
            }
        }

        audit("CHAT_REQUEST", resolved.clientName,
                "model=" + resolved.model + ", msgCount=" + resolved.messages.size() + ", allowedTools=" + resolved.allowedTools);

        int maxRetries = resolved.maxRetries;
        for (int attempt = 0; ; attempt++) {
            if (attempt > 0) {
                retriedCalls.incrementAndGet();
            }
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(resolved.url))
                        .timeout(Duration.ofMillis(resolved.readTimeoutMs))
                        .header("Content-Type", "application/json")
                        .headers(resolved.headersArray)
                        .POST(HttpRequest.BodyPublishers.ofString(resolved.requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpClient httpClient = getOrCreateHttpClient(resolved.connectTimeoutMs);
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();

                if (shouldRetryByStatus(status, attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }

                if (resolved.failOnNon2xx && (status < 200 || status >= 300)) {
                    recordFail(start, false, false);
                    throw new VKAiException(VKAiErrorCode.HTTP_STATUS,
                            "AI chat failed with status=" + status + ", body=" + abbreviate(response.body(), 256), status);
                }

                ParsedProviderResponse parsed = parseProviderResponse(response.body());
                List<VKAiToolCallResult> toolResults = executeToolCalls(parsed.toolCalls, resolved);

                VKAiChatResponse chatResponse = new VKAiChatResponse(
                        parsed.text,
                        parsed.finishReason,
                        parsed.usage,
                        System.currentTimeMillis() - start,
                        parsed.providerRequestId,
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
                    logCall(resolved, status, chatResponse.getLatencyMs(), attempt);
                }
                audit("CHAT_RESPONSE", resolved.clientName,
                        "status=" + status + ", finishReason=" + chatResponse.getFinishReason() + ", toolCalls=" + toolResults.size());
                return chatResponse;
            } catch (VKAiException e) {
                if (e.getCode() == VKAiErrorCode.HTTP_STATUS && shouldRetryByStatus(e.getStatusCode(), attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw e;
            } catch (HttpTimeoutException e) {
                if (shouldRetryByTimeout(attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                recordFail(start, true, false);
                throw new VKAiException(VKAiErrorCode.TIMEOUT, "AI chat timeout", e);
            } catch (IOException e) {
                if (shouldRetryByNetwork(attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                recordFail(start, false, true);
                throw new VKAiException(VKAiErrorCode.NETWORK_ERROR, "AI network error", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordFail(start, false, false);
                throw new VKAiException(VKAiErrorCode.STATE_ERROR, "AI chat interrupted", e);
            }
        }
    }

    public <T> T chatJson(VKAiChatRequest request, Class<T> type) {
        if (type == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Type is null");
        }
        VKAiChatResponse response = chat(request);
        try {
            return VKJson.fromJson(response.getText(), type);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.JSON_PARSE_ERROR, "Failed to parse chat content as JSON", e);
        }
    }

    public CompletableFuture<VKAiChatResponse> chatAsync(VKAiChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    public <T> CompletableFuture<T> chatJsonAsync(VKAiChatRequest request, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> chatJson(request, type));
    }

    public List<VKAiEmbedding> embed(VKAiEmbeddingRequest request) {
        ensureInit();
        if (request == null) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "VKAiEmbeddingRequest is null");
        }
        if (request.getInputs().isEmpty()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Embedding inputs are empty");
        }

        ClientResolved cr = resolveClient(request.getClientName());
        String model = resolveEmbeddingModel(request.getModel(), cr.client());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", request.getInputs());
        String payloadJson = VKJson.toJson(payload);
        String cacheKey = "vk:ai:embed:" + shortHash(cr.name() + "|" + model + "|" + payloadJson);

        String responseBody = null;
        if (config.isRagCacheEnabled() && config.getEmbeddingCacheTtlMs() > 0) {
            responseBody = cacheGetString(cacheKey);
            if (responseBody != null) {
                audit("EMBED_CACHE_HIT", cr.name(), "key=" + cacheKey);
            }
        }
        if (responseBody == null) {
            HttpResponse<String> response = executeWithRetry(
                    cr.name(),
                    joinBaseAndPath(cr.client().getBaseUrl(), cr.client().getEmbeddingPath()),
                    buildHeaders(cr.client()),
                    payloadJson,
                    cr.client().getConnectTimeoutMs() > 0 ? cr.client().getConnectTimeoutMs() : config.getConnectTimeoutMs(),
                    cr.client().getReadTimeoutMs() > 0 ? cr.client().getReadTimeoutMs() : config.getReadTimeoutMs(),
                    cr.client().getMaxRetries() >= 0 ? cr.client().getMaxRetries() : config.getMaxRetries(),
                    cr.client().getFailOnNon2xx() == null ? config.isFailOnNon2xx() : cr.client().getFailOnNon2xx(),
                    "EMBED_REQUEST"
            );
            responseBody = response.body();
            if (config.isRagCacheEnabled() && config.getEmbeddingCacheTtlMs() > 0) {
                cacheSetString(cacheKey, responseBody, config.getEmbeddingCacheTtlMs());
            }
            audit("EMBED_RESPONSE", cr.name(), "status=" + response.statusCode());
        }

        Map<?, ?> root;
        try {
            root = VKJson.fromJson(responseBody, Map.class);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Invalid embedding response JSON", e);
        }
        List<?> data = asList(root.get("data"));
        List<VKAiEmbedding> out = new ArrayList<>(data.size());
        for (Object item : data) {
            Map<?, ?> m = asMap(item);
            int index = asInt(m.get("index"));
            List<Double> vec = toDoubleList(asList(m.get("embedding")));
            out.add(new VKAiEmbedding(index, vec));
        }
        audit("EMBED_RESPONSE", cr.name(), "vectors=" + out.size());
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

        ClientResolved cr = resolveClient(request.getClientName());
        String model = resolveRerankModel(request.getModel(), cr.client());

        long start = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("query", request.getQuery());
        payload.put("documents", request.getDocuments());
        if (request.getTopK() != null) {
            payload.put("top_k", request.getTopK());
        }
        String payloadJson = VKJson.toJson(payload);
        String cacheKey = "vk:ai:rerank:" + shortHash(cr.name() + "|" + model + "|" + payloadJson);

        String responseBody = null;
        if (config.isRagCacheEnabled() && config.getRerankCacheTtlMs() > 0) {
            responseBody = cacheGetString(cacheKey);
            if (responseBody != null) {
                audit("RERANK_CACHE_HIT", cr.name(), "key=" + cacheKey);
            }
        }
        if (responseBody == null) {
            HttpResponse<String> response = executeWithRetry(
                    cr.name(),
                    joinBaseAndPath(cr.client().getBaseUrl(), cr.client().getRerankPath()),
                    buildHeaders(cr.client()),
                    payloadJson,
                    cr.client().getConnectTimeoutMs() > 0 ? cr.client().getConnectTimeoutMs() : config.getConnectTimeoutMs(),
                    cr.client().getReadTimeoutMs() > 0 ? cr.client().getReadTimeoutMs() : config.getReadTimeoutMs(),
                    cr.client().getMaxRetries() >= 0 ? cr.client().getMaxRetries() : config.getMaxRetries(),
                    cr.client().getFailOnNon2xx() == null ? config.isFailOnNon2xx() : cr.client().getFailOnNon2xx(),
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

        List<?> results = asList(root.get("results"));
        List<VKAiRerankResult> out = new ArrayList<>(results.size());
        for (Object item : results) {
            Map<?, ?> m = asMap(item);
            int index = asInt(m.get("index"));
            double score = asDouble(m.get("score"));
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
        String chatClientName = resolveRagClientName(request.getChatClientName(), request.getClientName());
        String embeddingClientName = resolveRagClientName(request.getEmbeddingClientName(), request.getClientName());
        String rerankClientName = resolveRagClientName(request.getRerankClientName(), request.getClientName());

        String retrievalQuery = request.isQueryRewriteEnabled()
                ? rewriteRagQueryLight(request.getQuery())
                : request.getQuery();
        int effectiveTopK = request.isDynamicTopKEnabled()
                ? computeDynamicTopK(request.getTopK(), retrievalQuery)
                : request.getTopK();
        int vectorTopK = request.isDynamicTopKEnabled()
                ? dynamicCandidateTopK(request.getVectorTopK(), effectiveTopK)
                : Math.max(request.getTopK(), request.getVectorTopK());
        int keywordTopK = request.isDynamicTopKEnabled()
                ? dynamicCandidateTopK(request.getKeywordTopK(), effectiveTopK)
                : Math.max(request.getTopK(), request.getKeywordTopK());

        List<VKAiEmbedding> queryEmbeddings = embed(new VKAiEmbeddingRequest()
                .client(embeddingClientName)
                .model(request.getEmbeddingModel())
                .input(retrievalQuery));
        if (queryEmbeddings.isEmpty()) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Embedding response is empty");
        }

        List<VKAiVectorHit> vectorHits = searchVector(queryEmbeddings.get(0).getVector(), vectorTopK);
        List<VKAiVectorHit> keywordHits = searchKeywords(retrievalQuery, keywordTopK);
        List<VKAiVectorHit> merged = mergeHybridHits(vectorHits, keywordHits, request);
        if (request.isMergeSimilarChunksEnabled()) {
            merged = mergeSimilarChunks(merged);
        }
        List<VKAiVectorHit> hits = request.isRerankEnabled()
                ? rerankHits(request, rerankClientName, merged, retrievalQuery, effectiveTopK)
                : merged;
        if (request.isContextCompressionEnabled()) {
            hits = compressContextHits(hits, effectiveTopK, request.getContextMaxCharsPerChunk(), request.getContextMaxChars());
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

        String answerCacheKey = "vk:ai:rag-answer:" + shortHash(buildRagAnswerCacheMaterial(
                request,
                chatClientName,
                embeddingClientName,
                rerankClientName,
                systemPrompt,
                hits));
        if (config.isRagCacheEnabled() && config.getRagAnswerCacheTtlMs() > 0) {
            String cachedAnswer = cacheGetString(answerCacheKey);
            if (cachedAnswer != null) {
                VKAiChatResponse cached = decodeCachedChatResponse(cachedAnswer);
                if (cached != null) {
                    audit("RAG_ANSWER_CACHE_HIT", chatClientName, "key=" + answerCacheKey);
                    return new VKAiRagResponse(cached, hits);
                }
            }
        }

        VKAiChatResponse answer = chat(new VKAiChatRequest()
                .client(chatClientName)
                .model(request.getModel())
                .system(systemPrompt)
                .message("user", "Question:\n" + request.getQuery() + "\n\nContext:\n" + context));
        if (config.isRagCacheEnabled() && config.getRagAnswerCacheTtlMs() > 0) {
            cacheSetString(answerCacheKey, encodeChatResponseForCache(answer), config.getRagAnswerCacheTtlMs());
        }
        return new VKAiRagResponse(answer, hits);
    }

    public void healthCheckRag(String clientName, boolean includeRerank) {
        ensureInit();
        ClientResolved cr = resolveClient(clientName);
        String embeddingModel = resolveEmbeddingModel(null, cr.client());
        try {
            embed(new VKAiEmbeddingRequest()
                    .client(cr.name())
                    .model(embeddingModel)
                    .input("health check"));
        } catch (VKAiException e) {
            throw mapRagPrecheckException("embedding", cr.name(), embeddingModel, e);
        }

        if (!includeRerank) {
            return;
        }
        String rerankModel = resolveRerankModel(null, cr.client());
        try {
            rerank(new VKAiRerankRequest()
                    .client(cr.name())
                    .model(rerankModel)
                    .query("health check")
                    .document("doc one")
                    .document("doc two")
                    .topK(1));
        } catch (VKAiException e) {
            throw mapRagPrecheckException("rerank", cr.name(), rerankModel, e);
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

    private ParsedProviderResponse parseProviderResponse(String body) {
        Map<?, ?> root;
        try {
            root = VKJson.fromJson(body, Map.class);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR,
                    "Failed to parse provider response as JSON", e);
        }

        Object idObj = root.get("id");
        String providerRequestId = idObj == null ? null : String.valueOf(idObj);

        List<?> choices = asList(root.get("choices"));
        if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Provider response missing choices[0]");
        }

        String finishReason = choice.get("finish_reason") == null ? null : String.valueOf(choice.get("finish_reason"));
        Map<?, ?> message = asMap(choice.get("message"));
        String text = message.get("content") == null ? "" : String.valueOf(message.get("content"));

        Map<?, ?> usageMap = asMap(root.get("usage"));
        int promptTokens = asInt(usageMap.get("prompt_tokens"));
        int completionTokens = asInt(usageMap.get("completion_tokens"));
        int totalTokensVal = asInt(usageMap.get("total_tokens"));
        VKAiUsage usage = new VKAiUsage(promptTokens, completionTokens, totalTokensVal);

        List<VKAiToolCall> toolCalls = parseToolCalls(message.get("tool_calls"));
        return new ParsedProviderResponse(providerRequestId, text, finishReason, usage, toolCalls);
    }

    private static List<VKAiToolCall> parseToolCalls(Object raw) {
        List<?> list = asList(raw);
        if (list.isEmpty()) {
            return List.of();
        }
        List<VKAiToolCall> out = new ArrayList<>();
        for (Object item : list) {
            Map<?, ?> m = asMap(item);
            if (m.isEmpty()) {
                continue;
            }
            String id = m.get("id") == null ? null : String.valueOf(m.get("id"));
            Map<?, ?> fn = asMap(m.get("function"));
            String name = fn.get("name") == null ? null : String.valueOf(fn.get("name"));
            String args = fn.get("arguments") == null ? "{}" : String.valueOf(fn.get("arguments"));
            if (name == null || name.isBlank()) {
                continue;
            }
            out.add(new VKAiToolCall(id, name, args));
        }
        return out;
    }

    private Resolved resolve(VKAiChatRequest request) {
        ClientResolved clientResolved = resolveClient(request.getClientName());
        String clientName = clientResolved.name();
        VKAiClientConfig client = clientResolved.client();

        String model = resolveChatModel(request.getModel(), client);

        List<VKAiMessage> preparedMessages = trimHistoryMessages(request);
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
        payload.put("model", model);
        payload.put("messages", messages);
        if (request.getTemperature() != null) {
            payload.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            payload.put("max_tokens", request.getMaxTokens());
        }

        Set<String> allowedTools = normalizeToolAllowlist(request.getAllowedTools());
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
                function.put("parameters", parseJsonOrEmptyObject(tool.inputJsonSchema()));
                declaredTools.add(Map.of("type", "function", "function", function));
            }
            if (!declaredTools.isEmpty()) {
                payload.put("tools", declaredTools);
            }
        }

        long connectTimeoutMs = client.getConnectTimeoutMs() > 0 ? client.getConnectTimeoutMs() : config.getConnectTimeoutMs();
        long readTimeoutMs = client.getReadTimeoutMs() > 0 ? client.getReadTimeoutMs() : config.getReadTimeoutMs();
        int maxRetries = client.getMaxRetries() >= 0 ? client.getMaxRetries() : config.getMaxRetries();
        boolean failOnNon2xx = client.getFailOnNon2xx() == null ? config.isFailOnNon2xx() : client.getFailOnNon2xx();

        Map<String, String> headers = buildHeaders(client);

        String url = joinBaseAndPath(client.getBaseUrl(), client.getChatPath());
        return new Resolved(
                clientName,
                model,
                url,
                request.getSystemPrompt(),
                preparedMessages,
                VKJson.toJson(payload),
                toHeaderArray(headers),
                connectTimeoutMs,
                readTimeoutMs,
                maxRetries,
                failOnNon2xx,
                allowedTools
        );
    }

    private List<VKAiMessage> trimHistoryMessages(VKAiChatRequest request) {
        List<VKAiMessage> raw = request.getMessages();
        if (raw.isEmpty() || !request.isHistoryTrimEnabled()) {
            return raw;
        }
        int maxMessages = request.getHistoryMaxMessages() == null ? 12 : Math.max(1, request.getHistoryMaxMessages());
        int maxChars = request.getHistoryMaxChars() == null ? 4000 : Math.max(128, request.getHistoryMaxChars());

        List<VKAiMessage> reversed = new ArrayList<>();
        int totalChars = 0;
        for (int i = raw.size() - 1; i >= 0; i--) {
            VKAiMessage msg = raw.get(i);
            if (msg == null || msg.getRole() == null || msg.getRole().isBlank()) {
                continue;
            }
            String content = msg.getContent() == null ? "" : msg.getContent().trim();
            if (reversed.isEmpty() && content.length() > maxChars) {
                content = content.substring(content.length() - maxChars);
            }
            if (!reversed.isEmpty() && totalChars + content.length() > maxChars) {
                break;
            }
            if (reversed.size() >= maxMessages) {
                break;
            }
            reversed.add(new VKAiMessage(msg.getRole(), content));
            totalChars += content.length();
        }
        List<VKAiMessage> out = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            out.add(reversed.get(i));
        }
        return out;
    }

    private Map<String, Object> parseJsonOrEmptyObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            Map<?, ?> map = VKJson.fromJson(json, Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private Set<String> normalizeToolAllowlist(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String name : raw) {
            if (name != null && !name.isBlank()) {
                out.add(name.trim());
            }
        }
        return Set.copyOf(out);
    }

    private HttpClient getOrCreateHttpClient(long connectTimeoutMs) {
        return httpClients.computeIfAbsent(connectTimeoutMs,
                timeout -> HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.max(1, timeout))).build());
    }

    private ClientResolved resolveClient(String rawClientName) {
        String clientName = rawClientName;
        if ((clientName == null || clientName.isBlank()) && clientContext.get() != null) {
            clientName = clientContext.get();
        }
        if (clientName == null || clientName.isBlank()) {
            if (clients.size() == 1) {
                String only = clients.keySet().iterator().next();
                return new ClientResolved(only, clients.values().iterator().next());
            }
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                    "AI client is not selected. Use request.client(name) or withClient(name, ...)");
        }
        String normalized = clientName.trim();
        VKAiClientConfig client = clients.get(normalized);
        if (client == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Ai client not found: " + normalized);
        }
        return new ClientResolved(normalized, client);
    }

    private String resolveRagClientName(String specificClientName, String fallbackClientName) {
        return firstNonBlank(specificClientName, fallbackClientName);
    }

    private String resolveChatModel(String requestModel, VKAiClientConfig client) {
        String model = firstNonBlank(requestModel, client.getModel(), config.getDefaultModel());
        if (model == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Chat model is not configured");
        }
        return model;
    }

    private String resolveEmbeddingModel(String requestModel, VKAiClientConfig client) {
        String model = firstNonBlank(
                requestModel,
                client.getEmbeddingModel(),
                config.getDefaultEmbeddingModel(),
                client.getModel(),
                config.getDefaultModel()
        );
        if (model == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                    "Embedding model is not configured. Set request.embeddingModel / client.embeddingModel / ai.defaultEmbeddingModel");
        }
        return model;
    }

    private String resolveRerankModel(String requestModel, VKAiClientConfig client) {
        String model = firstNonBlank(
                requestModel,
                client.getRerankModel(),
                config.getDefaultRerankModel(),
                client.getModel(),
                config.getDefaultModel()
        );
        if (model == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                    "Rerank model is not configured. Set request.rerankModel / client.rerankModel / ai.defaultRerankModel");
        }
        return model;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private VKAiException mapRagPrecheckException(String stage, String clientName, String model, VKAiException e) {
        if (e.getCode() == VKAiErrorCode.HTTP_STATUS && e.getStatusCode() != null && e.getStatusCode() == 404) {
            return new VKAiException(VKAiErrorCode.CONFIG_ERROR,
                    "RAG precheck failed at " + stage + " (404). client=" + clientName + ", model=" + model
                            + ". Check " + stage + " endpoint path and model layering config.", e);
        }
        return new VKAiException(e.getCode(),
                "RAG precheck failed at " + stage + ". client=" + clientName + ", model=" + model + ", cause=" + e.getMessage(), e);
    }

    private Map<String, String> buildHeaders(VKAiClientConfig client) {
        Map<String, String> headers = new LinkedHashMap<>(client.getDefaultHeaders());
        if (client.getApiKey() != null && !client.getApiKey().isBlank()) {
            headers.putIfAbsent("Authorization", "Bearer " + client.getApiKey().trim());
        }
        headers.putIfAbsent("X-Vostok-AI-Provider", client.getProvider());
        return headers;
    }

    private HttpResponse<String> executeWithRetry(String clientName,
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
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .header("Content-Type", "application/json")
                        .headers(toHeaderArray(headers))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpClient client = getOrCreateHttpClient(connectTimeoutMs);
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();

                if (shouldRetryByStatus(status, attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                if (failOnNon2xx && (status < 200 || status >= 300)) {
                    recordFail(start, false, false);
                    throw new VKAiException(VKAiErrorCode.HTTP_STATUS,
                            "AI request failed with status=" + status + ", body=" + abbreviate(response.body(), 256), status);
                }
                if (config.isMetricsEnabled()) {
                    successCalls.incrementAndGet();
                    totalCostMs.addAndGet(System.currentTimeMillis() - start);
                }
                return response;
            } catch (VKAiException e) {
                if (e.getCode() == VKAiErrorCode.HTTP_STATUS && shouldRetryByStatus(e.getStatusCode(), attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw e;
            } catch (HttpTimeoutException e) {
                if (shouldRetryByTimeout(attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                recordFail(start, true, false);
                throw new VKAiException(VKAiErrorCode.TIMEOUT, "AI request timeout", e);
            } catch (IOException e) {
                if (shouldRetryByNetwork(attempt, maxRetries)) {
                    sleepBackoff(attempt);
                    continue;
                }
                recordFail(start, false, true);
                throw new VKAiException(VKAiErrorCode.NETWORK_ERROR, "AI network error", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                recordFail(start, false, false);
                throw new VKAiException(VKAiErrorCode.STATE_ERROR, "AI request interrupted", e);
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
                httpClients.clear();
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

    private String firstSecurityRiskFromPrompt(String systemPrompt, List<VKAiMessage> messages) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            String r = firstPromptSecurityRisk(systemPrompt);
            if (r != null) {
                return "system:" + r;
            }
        }
        if (messages == null) {
            return null;
        }
        int idx = 0;
        for (VKAiMessage it : messages) {
            String content = it == null ? null : it.getContent();
            if (content == null || content.isBlank()) {
                idx++;
                continue;
            }
            String r = firstPromptSecurityRisk(content);
            if (r != null) {
                return "message[" + idx + "]:" + r;
            }
            idx++;
        }
        return null;
    }

    private String firstPromptSecurityRisk(String value) {
        VKSecurityCheckResult xss = Vostok.Security.checkXss(value);
        if (!xss.isSafe()) {
            return "xss";
        }
        VKSecurityCheckResult cmd = Vostok.Security.checkCommandInjection(value == null ? null : value.replace('\n', ' ').replace('\r', ' '));
        if (!cmd.isSafe()) {
            return "command-injection";
        }
        VKSecurityCheckResult path = Vostok.Security.checkPathTraversal(value);
        if (!path.isSafe()) {
            return "path-traversal";
        }
        return null;
    }

    private String firstToolInputSecurityRisk(String value) {
        VKSecurityCheckResult xss = Vostok.Security.checkXss(value);
        if (!xss.isSafe()) {
            return "xss";
        }
        VKSecurityCheckResult cmd = Vostok.Security.checkCommandInjection(value);
        if (!cmd.isSafe()) {
            return "command-injection";
        }
        VKSecurityCheckResult path = Vostok.Security.checkPathTraversal(value);
        if (!path.isSafe()) {
            return "path-traversal";
        }
        return null;
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
    }

    private void removeOldVersions(String documentId, String keepVersion) {
        String prevVersion = latestVersionByDoc.get(documentId);
        if (prevVersion == null || prevVersion.equals(keepVersion)) {
            return;
        }
        String key = docKey(documentId, prevVersion);
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
            }
        }
    }

    private List<VKAiVectorHit> mergeHybridHits(List<VKAiVectorHit> vectorHits,
                                                List<VKAiVectorHit> keywordHits,
                                                VKAiRagRequest request) {
        Map<String, Double> combined = new LinkedHashMap<>();
        Map<String, VKAiVectorHit> hitById = new LinkedHashMap<>();
        combineHits(combined, hitById, vectorHits, request.getVectorWeight());
        combineHits(combined, hitById, keywordHits, request.getKeywordWeight());

        List<VKAiVectorHit> merged = new ArrayList<>(combined.size());
        for (Map.Entry<String, Double> e : combined.entrySet()) {
            VKAiVectorHit h = hitById.get(e.getKey());
            if (h != null) {
                merged.add(new VKAiVectorHit(h.getId(), h.getText(), e.getValue(), h.getMetadata()));
            }
        }
        merged.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        return deduplicateByText(merged, Math.max(request.getTopK() * 3, request.getTopK()));
    }

    private int computeDynamicTopK(int baseTopK, String query) {
        int base = Math.max(1, baseTopK);
        String[] terms = tokenizeForRewrite(query);
        int termCount = terms.length;
        if (termCount <= 3) {
            return Math.min(base, 2);
        }
        if (termCount <= 8) {
            return Math.min(base, 3);
        }
        if (termCount >= 20) {
            return Math.min(Math.max(base + 1, 4), 8);
        }
        return base;
    }

    private int dynamicCandidateTopK(int configuredTopK, int effectiveTopK) {
        int floor = Math.max(1, effectiveTopK);
        int cap = Math.max(floor, floor * 3);
        return Math.max(floor, Math.min(Math.max(1, configuredTopK), cap));
    }

    private void combineHits(Map<String, Double> combined,
                             Map<String, VKAiVectorHit> hitById,
                             List<VKAiVectorHit> hits,
                             double weight) {
        if (hits == null || hits.isEmpty() || weight <= 0.0) {
            return;
        }
        double maxScore = 0.0;
        for (VKAiVectorHit h : hits) {
            if (h != null && h.getScore() > maxScore) {
                maxScore = h.getScore();
            }
        }
        if (maxScore <= 0.0) {
            return;
        }
        for (VKAiVectorHit h : hits) {
            if (h == null || h.getId() == null) {
                continue;
            }
            double normalized = Math.max(0.0, h.getScore()) / maxScore;
            combined.put(h.getId(), combined.getOrDefault(h.getId(), 0.0) + weight * normalized);
            hitById.putIfAbsent(h.getId(), h);
        }
    }

    private List<VKAiVectorHit> deduplicateByText(List<VKAiVectorHit> hits, int limit) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<VKAiVectorHit> out = new ArrayList<>(Math.min(limit, hits.size()));
        for (VKAiVectorHit hit : hits) {
            if (hit == null || hit.getText() == null) {
                continue;
            }
            String key = normalizeForDedup(hit.getText());
            if (seen.add(key)) {
                out.add(hit);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private List<VKAiVectorHit> mergeSimilarChunks(List<VKAiVectorHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, List<VKAiVectorHit>> grouped = new LinkedHashMap<>();
        List<VKAiVectorHit> passthrough = new ArrayList<>();
        for (VKAiVectorHit hit : hits) {
            if (hit == null) {
                continue;
            }
            String docId = hit.getMetadata().get("vk_doc_id");
            Integer idx = parseChunkIndex(hit);
            if (docId == null || idx == null) {
                passthrough.add(hit);
                continue;
            }
            String version = hit.getMetadata().get("vk_doc_version");
            String key = docId + "#" + (version == null ? "" : version);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(hit);
        }

        List<VKAiVectorHit> merged = new ArrayList<>(passthrough);
        for (List<VKAiVectorHit> group : grouped.values()) {
            group.sort(Comparator.comparingInt(h -> parseChunkIndex(h)));
            VKAiVectorHit current = null;
            int prevIdx = Integer.MIN_VALUE;
            for (VKAiVectorHit hit : group) {
                int idx = parseChunkIndex(hit);
                if (current == null) {
                    current = hit;
                    prevIdx = idx;
                    continue;
                }
                boolean adjacent = idx - prevIdx <= 1;
                boolean similar = lexicalSimilarity(current.getText(), hit.getText()) >= 0.6;
                if (adjacent || similar) {
                    current = mergeTwoHits(current, hit);
                    prevIdx = idx;
                } else {
                    merged.add(current);
                    current = hit;
                    prevIdx = idx;
                }
            }
            if (current != null) {
                merged.add(current);
            }
        }
        merged.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        return merged;
    }

    private VKAiVectorHit mergeTwoHits(VKAiVectorHit a, VKAiVectorHit b) {
        String left = a.getText() == null ? "" : a.getText().trim();
        String right = b.getText() == null ? "" : b.getText().trim();
        String text = left.contains(right) ? left : (right.contains(left) ? right : (left + "\n" + right));
        Map<String, String> metadata = new LinkedHashMap<>(a.getMetadata());
        Integer ai = parseChunkIndex(a);
        Integer bi = parseChunkIndex(b);
        if (ai != null && bi != null) {
            metadata.put("vk_chunk_span", Math.min(ai, bi) + "-" + Math.max(ai, bi));
        }
        String id = a.getId().equals(b.getId()) ? a.getId() : a.getId() + "~" + b.getId();
        return new VKAiVectorHit(id, text, Math.max(a.getScore(), b.getScore()), metadata);
    }

    private Integer parseChunkIndex(VKAiVectorHit hit) {
        if (hit == null || hit.getMetadata() == null) {
            return null;
        }
        String idx = hit.getMetadata().get("vk_chunk_index");
        if (idx == null || idx.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(idx);
        } catch (Exception e) {
            return null;
        }
    }

    private double lexicalSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        LinkedHashSet<String> sa = new LinkedHashSet<>(List.of(tokenizeForRewrite(a)));
        LinkedHashSet<String> sb = new LinkedHashSet<>(List.of(tokenizeForRewrite(b)));
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String t : sa) {
            if (sb.contains(t)) {
                inter++;
            }
        }
        int union = sa.size() + sb.size() - inter;
        if (union <= 0) {
            return 0.0;
        }
        return (double) inter / union;
    }

    private List<VKAiVectorHit> compressContextHits(List<VKAiVectorHit> hits,
                                                    int limit,
                                                    int maxPerChunkChars,
                                                    int maxTotalChars) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        int maxPer = Math.max(64, maxPerChunkChars);
        int maxTotal = Math.max(maxPer, maxTotalChars);
        List<VKAiVectorHit> out = new ArrayList<>(Math.min(limit, hits.size()));
        int total = 0;
        for (VKAiVectorHit hit : hits) {
            if (out.size() >= limit) {
                break;
            }
            String text = compressSnippet(hit.getText(), maxPer);
            if (text.isBlank()) {
                continue;
            }
            if (total + text.length() > maxTotal) {
                int remaining = maxTotal - total;
                if (remaining < 32) {
                    break;
                }
                text = compressSnippet(text, remaining);
                if (text.isBlank()) {
                    break;
                }
            }
            total += text.length();
            out.add(new VKAiVectorHit(hit.getId(), text, hit.getScore(), hit.getMetadata()));
        }
        return out;
    }

    private String compressSnippet(String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        int max = Math.max(32, maxChars);
        if (text.length() <= max) {
            return text;
        }
        int cut = max;
        int min = Math.max(20, max / 2);
        for (int i = max - 1; i >= min; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == ';' || c == '；') {
                cut = i + 1;
                break;
            }
        }
        String snippet = text.substring(0, Math.min(cut, text.length())).trim();
        return snippet.length() < text.length() ? snippet + "..." : snippet;
    }

    private String rewriteRagQueryLight(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String normalized = rawQuery.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 96) {
            return normalized;
        }
        String candidate = pickQuestionCandidate(normalized);
        List<String> keywords = extractLightKeywords(normalized, 6);
        String out = candidate;
        for (String kw : keywords) {
            if (!out.toLowerCase().contains(kw.toLowerCase())) {
                out += " " + kw;
            }
        }
        if (out.length() > 128) {
            out = out.substring(0, 128);
        }
        return out.trim();
    }

    private String pickQuestionCandidate(String normalized) {
        String[] parts = normalized.split("[。！？!?;；\\n\\r]");
        String best = "";
        for (String p : parts) {
            String s = p == null ? "" : p.trim();
            if (s.isBlank()) {
                continue;
            }
            boolean hasQuestionMarker = s.contains("如何") || s.contains("怎么") || s.contains("为什么")
                    || s.contains("什么") || s.contains("请问") || s.endsWith("?") || s.endsWith("？");
            if (hasQuestionMarker) {
                best = s;
            } else if (best.isBlank() && s.length() > best.length()) {
                best = s;
            }
        }
        if (best.isBlank()) {
            best = normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
        }
        return best.length() > 96 ? best.substring(0, 96) : best;
    }

    private List<String> extractLightKeywords(String input, int limit) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher latin = Pattern.compile("[a-zA-Z0-9_\\-]{3,}").matcher(input.toLowerCase());
        while (latin.find() && out.size() < limit) {
            out.add(latin.group());
        }
        Matcher han = Pattern.compile("[\\p{IsHan}]{2,6}").matcher(input);
        while (han.find() && out.size() < limit) {
            out.add(han.group());
        }
        return new ArrayList<>(out);
    }

    private String[] tokenizeForRewrite(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        String[] arr = text.toLowerCase().split("[^\\p{IsHan}a-z0-9_\\-]+");
        List<String> out = new ArrayList<>(arr.length);
        for (String it : arr) {
            if (it != null && !it.isBlank()) {
                out.add(it);
            }
        }
        return out.toArray(new String[0]);
    }

    private List<VKAiVectorHit> rerankHits(VKAiRagRequest request,
                                           String rerankClientName,
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
                .client(rerankClientName)
                .model(request.getRerankModel())
                .query(retrievalQuery)
                .documents(docs)
                .topK(Math.min(effectiveTopK, docs.size()));
        VKAiRerankResponse rerank;
        long timeoutMs = config.getRagRerankTimeoutMs();
        if (timeoutMs > 0) {
            try {
                rerank = CompletableFuture.supplyAsync(() -> rerank(rerankRequest))
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                audit("RAG_DEGRADE", rerankClientName, "reason=rerank_timeout, timeoutMs=" + timeoutMs);
                return hits;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                audit("RAG_DEGRADE", rerankClientName, "reason=rerank_error");
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

    private static String docKey(String documentId, String version) {
        return documentId + "#" + version;
    }

    private static String normalizeForDedup(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String x = Integer.toHexString(b & 0xff);
                if (x.length() == 1) {
                    sb.append('0');
                }
                sb.append(x);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
            audit("CACHE_BYPASS", null, "op=get,key=" + abbreviate(key, 120));
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
            audit("CACHE_BYPASS", null, "op=set,key=" + abbreviate(key, 120));
        }
    }

    private static String shortHash(String raw) {
        String digest = sha256Hex(raw == null ? "" : raw);
        return digest.length() > 24 ? digest.substring(0, 24) : digest;
    }

    private static String buildRagAnswerCacheMaterial(VKAiRagRequest request,
                                                      String chatClientName,
                                                      String embeddingClientName,
                                                      String rerankClientName,
                                                      String systemPrompt,
                                                      List<VKAiVectorHit> hits) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(chatClientName).append('|')
                .append(embeddingClientName).append('|')
                .append(rerankClientName).append('|')
                .append(request.getModel()).append('|')
                .append(request.getEmbeddingModel()).append('|')
                .append(request.getRerankModel()).append('|')
                .append(request.getQuery()).append('|')
                .append(request.getTopK()).append('|')
                .append(systemPrompt == null ? "" : systemPrompt);
        for (VKAiVectorHit hit : hits) {
            sb.append('|').append(hit.getId()).append('@').append(hit.getScore());
            String version = hit.getMetadata() == null ? null : hit.getMetadata().get("vk_doc_version");
            if (version != null) {
                sb.append('#').append(version);
            }
        }
        return sb.toString();
    }

    private static String encodeChatResponseForCache(VKAiChatResponse answer) {
        if (answer == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", answer.getText());
        payload.put("finishReason", answer.getFinishReason());
        payload.put("providerRequestId", answer.getProviderRequestId());
        payload.put("statusCode", answer.getStatusCode());
        VKAiUsage usage = answer.getUsage();
        if (usage != null) {
            payload.put("promptTokens", usage.getPromptTokens());
            payload.put("completionTokens", usage.getCompletionTokens());
            payload.put("totalTokens", usage.getTotalTokens());
        }
        return VKJson.toJson(payload);
    }

    private static VKAiChatResponse decodeCachedChatResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> payload = VKJson.fromJson(raw, Map.class);
            String text = payload.get("text") == null ? "" : String.valueOf(payload.get("text"));
            String finishReason = payload.get("finishReason") == null ? "cached" : String.valueOf(payload.get("finishReason"));
            String requestId = payload.get("providerRequestId") == null ? "cached" : String.valueOf(payload.get("providerRequestId"));
            int statusCode = asInt(payload.get("statusCode"));
            VKAiUsage usage = new VKAiUsage(
                    asInt(payload.get("promptTokens")),
                    asInt(payload.get("completionTokens")),
                    asInt(payload.get("totalTokens"))
            );
            return new VKAiChatResponse(text, finishReason, usage, 0, requestId, statusCode, List.of());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return 0.0;
        }
    }

    private static List<Double> toDoubleList(List<?> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Double> out = new ArrayList<>(input.size());
        for (Object it : input) {
            out.add(asDouble(it));
        }
        return out;
    }

    private static String[] toHeaderArray(Map<String, String> headers) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            out.add(e.getKey());
            out.add(e.getValue());
        }
        return out.toArray(new String[0]);
    }

    private static String joinBaseAndPath(String baseUrl, String path) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (path == null || path.isBlank()) {
            return b;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
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
            String[] headersArray,
            long connectTimeoutMs,
            long readTimeoutMs,
            int maxRetries,
            boolean failOnNon2xx,
            Set<String> allowedTools
    ) {
    }

    private record ParsedProviderResponse(
            String providerRequestId,
            String text,
            String finishReason,
            VKAiUsage usage,
            List<VKAiToolCall> toolCalls
    ) {
    }

    private record ClientResolved(String name, VKAiClientConfig client) {
    }
}
