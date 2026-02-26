package yueyang.vostok;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.ai.VKAiAuditRecord;
import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.VKAiChatResponse;
import yueyang.vostok.ai.VKAiChatDeltaStream;
import yueyang.vostok.ai.VKAiSession;
import yueyang.vostok.ai.VKAiSessionMessage;
import yueyang.vostok.ai.core.VKAiDataMemoryStore;
import yueyang.vostok.ai.VKAiConfig;
import yueyang.vostok.ai.provider.VKAiModelConfig;
import yueyang.vostok.ai.provider.VKAiModelType;
import yueyang.vostok.ai.rag.VKAiEmbedding;
import yueyang.vostok.ai.rag.VKAiEmbeddingRequest;
import yueyang.vostok.ai.VKAiMetrics;
import yueyang.vostok.ai.rag.VKAiRagRequest;
import yueyang.vostok.ai.rag.VKAiRagResponse;
import yueyang.vostok.ai.rag.VKAiRagIngestRequest;
import yueyang.vostok.ai.rag.VKAiRagIngestResult;
import yueyang.vostok.ai.rag.VKAiRerankRequest;
import yueyang.vostok.ai.rag.VKAiRerankResponse;
import yueyang.vostok.ai.rag.VKAiRerankResult;
import yueyang.vostok.ai.tool.VKAiTool;
import yueyang.vostok.ai.tool.VKAiToolCall;
import yueyang.vostok.ai.tool.VKAiToolCallResult;
import yueyang.vostok.ai.tool.VKAiToolResult;
import yueyang.vostok.ai.rag.VKAiVectorDoc;
import yueyang.vostok.ai.rag.VKAiVectorHit;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;
import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.dialect.VKDialectType;
import yueyang.vostok.util.json.VKJson;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class VostokAiTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger retryCounter = new AtomicInteger();
    private final AtomicInteger rerankCounter = new AtomicInteger();
    private final AtomicInteger embeddingCounter = new AtomicInteger();
    private final AtomicInteger ragChatCounter = new AtomicInteger();
    private final AtomicInteger rerankAltCounter = new AtomicInteger();
    private final AtomicInteger embeddingAltCounter = new AtomicInteger();
    private final AtomicInteger ragChatAltCounter = new AtomicInteger();
    private final AtomicInteger streamChatCounter = new AtomicInteger();
    private final AtomicInteger chatMessageCount = new AtomicInteger();
    private final AtomicReference<String> lastEmbeddingInput = new AtomicReference<>();
    private final AtomicReference<String> lastRagUserPrompt = new AtomicReference<>();
    private final AtomicReference<String> lastEmbeddingModel = new AtomicReference<>();
    private final AtomicReference<String> lastRerankModel = new AtomicReference<>();
    private final AtomicReference<String> lastSessionModel = new AtomicReference<>();
    private final AtomicInteger lastSessionMsgCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/v1/chat/completions", this::handleChat);
        server.createContext("/v1/chat/json", this::handleJsonChat);
        server.createContext("/v1/chat/retry", this::handleRetryChat);
        server.createContext("/v1/chat/fail", this::handleFailChat);
        server.createContext("/v1/chat/invalid", this::handleInvalidJsonChat);
        server.createContext("/v1/chat/clientA", this::handleClientA);
        server.createContext("/v1/chat/clientB", this::handleClientB);
        server.createContext("/v1/chat/tool", this::handleToolChat);
        server.createContext("/v1/chat/tool-missing", this::handleToolMissingChat);
        server.createContext("/v1/chat/rag", this::handleRagChat);
        server.createContext("/v1/chat/rag-alt", this::handleRagChatAlt);
        server.createContext("/v1/chat/stream", this::handleStreamChat);
        server.createContext("/v1/chat/session", this::handleSessionChat);
        server.createContext("/v1/embeddings", this::handleEmbeddings);
        server.createContext("/v1/embeddings-alt", this::handleEmbeddingsAlt);
        server.createContext("/v1/rerank", this::handleRerank);
        server.createContext("/v1/rerank-alt", this::handleRerankAlt);

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        Vostok.AI.close();
        Vostok.Data.close();
        Vostok.Cache.close();
        Vostok.Cache.init(new VKCacheConfig().providerType(VKCacheProviderType.MEMORY).codec("string"));
        Vostok.AI.init(new VKAiConfig()
                .connectTimeoutMs(500)
                .readTimeoutMs(1000)
                .maxRetries(1)
                .retryBackoffMs(20)
                .maxRetryBackoffMs(60)
                .logEnabled(false)
                .auditEnabled(true)
                .securityCheckEnabled(true)
                .blockOnSecurityRisk(true)
                .embeddingCacheTtlMs(30_000)
                .rerankCacheTtlMs(30_000)
                .ragAnswerCacheTtlMs(30_000));

        registerModelSet("demo", null, null, null,
                "demo-chat", "demo-embed", "demo-rerank");
        Vostok.AI.clearTools();
        Vostok.AI.clearAudits();
        Vostok.AI.clearVectorStore();
        retryCounter.set(0);
        rerankCounter.set(0);
        embeddingCounter.set(0);
        ragChatCounter.set(0);
        rerankAltCounter.set(0);
        embeddingAltCounter.set(0);
        ragChatAltCounter.set(0);
        streamChatCounter.set(0);
        chatMessageCount.set(0);
        lastEmbeddingInput.set(null);
        lastRagUserPrompt.set(null);
        lastEmbeddingModel.set(null);
        lastRerankModel.set(null);
        lastSessionModel.set(null);
        lastSessionMsgCount.set(0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        Vostok.AI.close();
        Vostok.Data.close();
        Vostok.Cache.close();
    }

    @Test
    void testChatSuccessAndUsage() {
        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .model("demo-chat")
                .system("You are assistant")
                .message("user", "hello"));

        assertEquals("hello from ai", response.getText());
        assertEquals("stop", response.getFinishReason());
        assertEquals("req-1", response.getProviderRequestId());
        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(5, response.getUsage().getCompletionTokens());
        assertEquals(15, response.getUsage().getTotalTokens());

        VKAiMetrics metrics = Vostok.AI.metrics();
        assertEquals(1, metrics.totalCalls());
        assertEquals(1, metrics.successCalls());
        assertEquals(0, metrics.failedCalls());
        assertEquals(1L, metrics.statusCounts().get(200));
    }

    @Test
    void testChatJsonToPojo() {
        registerModelSet("json", "/v1/chat/json", null, null,
                "json-chat", "json-embed", "json-rerank");

        ScoreResult result = Vostok.AI.chatJson(new VKAiChatRequest()
                .model("json-chat")
                .message("user", "return json"), ScoreResult.class);

        assertEquals("neo", result.name);
        assertEquals(99, result.score);
    }

    @Test
    void testModelSelectionAcrossDifferentRegisteredModels() {
        registerModelSet("a", "/v1/chat/clientA", null, null,
                "a-chat", "a-embed", "a-rerank");
        registerModelSet("b", "/v1/chat/clientB", null, null,
                "b-chat", "b-embed", "b-rerank");

        String a = Vostok.AI.chat(new VKAiChatRequest().model("a-chat").message("user", "x")).getText();
        String b = Vostok.AI.chat(new VKAiChatRequest().model("b-chat").message("user", "x")).getText();

        assertEquals("from-client-a", a);
        assertEquals("from-client-b", b);
    }

    @Test
    void testRetryOn5xxAndMetrics() {
        registerModelSet("retry", "/v1/chat/retry", null, null,
                "retry-chat", "retry-embed", "retry-rerank");

        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .model("retry-chat")
                .message("user", "retry"));

        assertEquals("retry-ok", response.getText());
        assertEquals(2, retryCounter.get());

        VKAiMetrics metrics = Vostok.AI.metrics();
        assertTrue(metrics.retriedCalls() >= 1);
        assertTrue(metrics.statusCounts().containsKey(503));
        assertTrue(metrics.statusCounts().containsKey(200));
    }

    @Test
    void testFailOnNon2xx() {
        registerModelSet("fail", "/v1/chat/fail", null, null,
                "fail-chat", "fail-embed", "fail-rerank");

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest().model("fail-chat").message("user", "x")));

        assertEquals(VKAiErrorCode.HTTP_STATUS, ex.getCode());
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void testJsonParseError() {
        registerModelSet("invalid", "/v1/chat/invalid", null, null,
                "invalid-chat", "invalid-embed", "invalid-rerank");

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chatJson(new VKAiChatRequest().model("invalid-chat").message("user", "x"), ScoreResult.class));

        assertEquals(VKAiErrorCode.JSON_PARSE_ERROR, ex.getCode());
    }

    @Test
    void testChatAsync() throws Exception {
        VKAiChatResponse response = Vostok.AI.chatAsync(new VKAiChatRequest()
                        .model("demo-chat")
                        .message("user", "async"))
                .get(2, TimeUnit.SECONDS);

        assertEquals("hello from ai", response.getText());
    }

    @Test
    void testChatStreamDeltaReading() {
        registerModelSet("stream", "/v1/chat/stream", null, null,
                "stream-chat", "stream-embed", "stream-rerank");

        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .model("stream-chat")
                .stream(true)
                .message("user", "stream please"));

        assertTrue(response.isStreaming());
        assertNull(response.getText());
        assertNotNull(response.stream());

        StringBuilder sb = new StringBuilder();
        VKAiChatDeltaStream stream = response.stream();
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (stream.hasNext(200)) {
                sb.append(stream.next().getContentDelta());
                continue;
            }
            if (stream.isDone()) {
                break;
            }
        }
        assertEquals("hello", sb.toString());
        assertTrue(stream.isDone());
        assertEquals("stop", stream.finishReason());
        assertEquals("req-stream", stream.providerRequestId());
        assertEquals(5, stream.finalUsage().getTotalTokens());
        assertEquals(1, streamChatCounter.get());
    }

    @Test
    void testChatJsonRejectsStream() {
        registerModelSet("stream", "/v1/chat/stream", null, null,
                "stream-chat", "stream-embed", "stream-rerank");

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chatJson(new VKAiChatRequest()
                        .model("stream-chat")
                        .stream(true)
                        .message("user", "json"), ScoreResult.class));
        assertEquals(VKAiErrorCode.INVALID_ARGUMENT, ex.getCode());
    }

    @Test
    void testSessionMemoryAndModelSwitch() {
        registerModelSet("session", "/v1/chat/session", null, null,
                "session-chat", "session-embed", "session-rerank");
        Vostok.AI.registerModel("model-b", new VKAiModelConfig()
                .type(VKAiModelType.CHAT)
                .provider("openai-compatible")
                .baseUrl(baseUrl)
                .path("/v1/chat/session")
                .apiKey("demo-key")
                .model("model-b"));

        VKAiSession session = Vostok.AI.createSession("session-chat");
        assertNotNull(session.getSessionId());
        assertEquals("session-chat", session.getCurrentModel());

        VKAiChatResponse r1 = Vostok.AI.chatSession(session.getSessionId(), "hi");
        assertTrue(r1.getText().contains("session-chat"));
        assertEquals("session-chat", lastSessionModel.get());
        assertEquals(1, lastSessionMsgCount.get());

        VKAiChatResponse r2 = Vostok.AI.chatSession(session.getSessionId(), "again");
        assertTrue(r2.getText().contains("session-chat"));
        assertEquals(3, lastSessionMsgCount.get());

        Vostok.AI.switchSessionModel(session.getSessionId(), "model-b");
        VKAiChatResponse r3 = Vostok.AI.chatSession(session.getSessionId(), "third");
        assertTrue(r3.getText().contains("model-b"));
        assertEquals("model-b", lastSessionModel.get());
        assertEquals(5, lastSessionMsgCount.get());

        List<VKAiSessionMessage> messages = Vostok.AI.sessionMessages(session.getSessionId());
        assertEquals(6, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("user", messages.get(4).getRole());
        assertEquals("assistant", messages.get(5).getRole());
    }


    
    @Test
    void testSessionPersistenceWithDataStore() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:ai_session_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Vostok.Data.init(new VKDataConfig()
                .url(jdbcUrl)
                .username("sa")
                .password("")
                .driver("org.h2.Driver")
                .dialect(VKDialectType.MYSQL), "yueyang.vostok");
        try (var conn = java.sql.DriverManager.getConnection(jdbcUrl, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE vk_ai_session (session_id VARCHAR(64) PRIMARY KEY, current_model VARCHAR(128), created_at BIGINT, updated_at BIGINT, metadata_json VARCHAR(4000))");
            stmt.execute("CREATE TABLE vk_ai_session_message (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id VARCHAR(64), seq BIGINT, role VARCHAR(32), content VARCHAR(4000), model VARCHAR(128), created_at BIGINT)");
        }

        registerModelSet("session", "/v1/chat/session", null, null,
                "session-chat", "session-embed", "session-rerank");
        Vostok.AI.setMemoryStore(new VKAiDataMemoryStore());

        VKAiSession session = Vostok.AI.createSession("session-chat");
        Vostok.AI.chatSession(session.getSessionId(), "persist me");
        Vostok.AI.chatSession(session.getSessionId(), "persist me too");

        List<VKAiSessionMessage> messages = Vostok.AI.sessionMessages(session.getSessionId());
        assertEquals(4, messages.size());

        VKAiSession loaded = Vostok.AI.session(session.getSessionId());
        assertEquals("session-chat", loaded.getCurrentModel());
    }


    
    @Test
    void testModelValidationAndMissingModel() {
        VKAiException registerError = assertThrows(VKAiException.class,
                () -> Vostok.AI.registerModel("bad", new VKAiModelConfig()));
        assertEquals(VKAiErrorCode.CONFIG_ERROR, registerError.getCode());

        Vostok.AI.close();
        Vostok.AI.init(new VKAiConfig());

        VKAiException noModel = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest().message("user", "x")));
        assertEquals(VKAiErrorCode.CONFIG_ERROR, noModel.getCode());
    }

    @Test
    void testManualToolCall() {
        Vostok.AI.registerTool(sumTool());

        VKAiToolCallResult result = Vostok.AI.callTool(new VKAiToolCall("c1", "sum", "{\"a\":7,\"b\":8}"));

        assertTrue(result.isSuccess());
        assertEquals("sum", result.getToolName());
        Map<?, ?> out = VKJson.fromJson(result.getOutputJson(), Map.class);
        assertEquals(15, ((Number) out.get("result")).intValue());
    }

    @Test
    void testChatAutoToolCallingAndAudit() {
        registerModelSet("tool", "/v1/chat/tool", null, null,
                "tool-chat", "tool-embed", "tool-rerank");
        Vostok.AI.registerTool(sumTool());

        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .model("tool-chat")
                .allowTool("sum")
                .message("user", "calc"));

        assertEquals("tool-call", response.getFinishReason());
        assertEquals(1, response.getToolResults().size());
        assertEquals("sum", response.getToolResults().get(0).getToolName());

        List<VKAiAuditRecord> audits = Vostok.AI.audits(20);
        assertTrue(audits.stream().anyMatch(a -> "TOOL_CALL".equals(a.getType())));
    }

    @Test
    void testToolDeniedByAllowList() {
        registerModelSet("tool", "/v1/chat/tool", null, null,
                "tool-chat", "tool-embed", "tool-rerank");
        Vostok.AI.registerTool(sumTool());

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest()
                        .model("tool-chat")
                        .allowTool("other")
                        .message("user", "calc")));

        assertEquals(VKAiErrorCode.TOOL_DENIED, ex.getCode());
    }

    @Test
    void testToolNotFound() {
        registerModelSet("tool-missing", "/v1/chat/tool-missing", null, null,
                "tool-missing-chat", "tool-missing-embed", "tool-missing-rerank");

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest()
                        .model("tool-missing-chat")
                        .allowTool("missing_tool")
                        .message("user", "calc")));

        assertEquals(VKAiErrorCode.TOOL_NOT_FOUND, ex.getCode());
    }

    @Test
    void testSecurityBlockForPrompt() {
        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest()
                        .model("demo-chat")
                        .message("user", "<script>alert(1)</script>")));
        assertEquals(VKAiErrorCode.SECURITY_BLOCKED, ex.getCode());
    }

    @Test
    void testSecurityBlockForToolInput() {
        Vostok.AI.registerTool(sumTool());
        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.callTool(new VKAiToolCall("c2", "sum", "{\"cmd\":\"echo ok; rm -rf /tmp/x\"}")));
        assertEquals(VKAiErrorCode.SECURITY_BLOCKED, ex.getCode());
    }

    @Test
    void testAuditLimitAndClear() {
        Vostok.AI.chat(new VKAiChatRequest().model("demo-chat").message("user", "hello"));
        Vostok.AI.chat(new VKAiChatRequest().model("demo-chat").message("user", "hello2"));

        List<VKAiAuditRecord> last = Vostok.AI.audits(1);
        assertEquals(1, last.size());

        Vostok.AI.clearAudits();
        assertTrue(Vostok.AI.audits(10).isEmpty());
    }

    @Test
    void testEmbedding() {
        List<VKAiEmbedding> out = Vostok.AI.embed(new VKAiEmbeddingRequest()
                .model("demo-embed")
                .input("java")
                .input("python"));
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).getVector().size());
    }

    @Test
    void testEmbeddingCacheHit() {
        List<VKAiEmbedding> first = Vostok.AI.embed(new VKAiEmbeddingRequest().model("demo-embed").input("java"));
        List<VKAiEmbedding> second = Vostok.AI.embed(new VKAiEmbeddingRequest().model("demo-embed").input("java"));
        assertEquals(first.get(0).getVector(), second.get(0).getVector());
        assertEquals(1, embeddingCounter.get(), "second embed should hit cache");
    }

    @Test
    void testRerank() {
        VKAiRerankResponse res = Vostok.AI.rerank(new VKAiRerankRequest()
                .model("demo-rerank")
                .query("jvm")
                .document("python language")
                .document("java jvm tuning")
                .topK(2));
        assertEquals(2, res.getResults().size());
        VKAiRerankResult first = res.getResults().get(0);
        assertEquals(1, first.getIndex());
        assertTrue(first.getScore() >= res.getResults().get(1).getScore());
    }

    @Test
    void testRerankCacheHit() {
        Vostok.AI.rerank(new VKAiRerankRequest()
                .model("demo-rerank")
                .query("jvm")
                .document("python language")
                .document("java jvm tuning")
                .topK(2));
        Vostok.AI.rerank(new VKAiRerankRequest()
                .model("demo-rerank")
                .query("jvm")
                .document("python language")
                .document("java jvm tuning")
                .topK(2));
        assertEquals(1, rerankCounter.get(), "second rerank should hit cache");
    }

    @Test
    void testModelOverrideForEmbeddingAndRerank() {
        Vostok.AI.reinit(new VKAiConfig()
                .logEnabled(false)
                .auditEnabled(true)
                .securityCheckEnabled(true)
                .blockOnSecurityRisk(true));
        registerModelSet("layer", null, null, null,
                "layer-chat", "embed-client", "rerank-client");
        Vostok.AI.registerModel("embed-request", new VKAiModelConfig()
                .type(VKAiModelType.EMBEDDING)
                .provider("openai-compatible")
                .baseUrl(baseUrl)
                .path("/v1/embeddings")
                .apiKey("demo-key")
                .model("embed-request"));
        Vostok.AI.registerModel("rerank-request", new VKAiModelConfig()
                .type(VKAiModelType.RERANK)
                .provider("openai-compatible")
                .baseUrl(baseUrl)
                .path("/v1/rerank")
                .apiKey("demo-key")
                .model("rerank-request"));

        Vostok.AI.embed(new VKAiEmbeddingRequest().model("embed-request").input("hello"));
        assertEquals("embed-request", lastEmbeddingModel.get());
        Vostok.AI.embed(new VKAiEmbeddingRequest().model("layer-embed").input("hello2"));
        assertEquals("embed-client", lastEmbeddingModel.get());

        Vostok.AI.rerank(new VKAiRerankRequest()
                .model("rerank-request")
                .query("q")
                .document("a")
                .document("b")
                .topK(1));
        assertEquals("rerank-request", lastRerankModel.get());
        Vostok.AI.rerank(new VKAiRerankRequest()
                .model("layer-rerank")
                .query("q2")
                .document("a")
                .document("b")
                .topK(1));
        assertEquals("rerank-client", lastRerankModel.get());
    }

    @Test
    void testRagHealthCheckSuccessAndConfigErrorMapping() {
        Vostok.AI.healthCheckRag("demo-embed", "demo-rerank", true);
        assertNotNull(lastEmbeddingModel.get());
        assertNotNull(lastRerankModel.get());

        registerModelSet("bad-rag", null, null, null,
                "bad-chat", "bad-embed", "bad-rerank");

        VKAiException ex = assertThrows(VKAiException.class, () -> Vostok.AI.healthCheckRag("bad-rag-embed", null, false));
        assertEquals(VKAiErrorCode.CONFIG_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("precheck failed at embedding"));
    }

    @Test
    void testRagCanUseDifferentModelsForChatEmbeddingRerank() {
        registerModelSet("rag-chat", "/v1/chat/rag-alt", null, null,
                "rag-chat", "rag-chat-embed", "rag-chat-rerank");
        registerModelSet("rag-embed", null, "/v1/embeddings-alt", null,
                "rag-embed-chat", "rag-embed", "rag-embed-rerank");
        registerModelSet("rag-rerank", null, null, "/v1/rerank-alt",
                "rag-rerank-chat", "rag-rerank-embed", "rag-rerank");

        List<VKAiEmbedding> docs = Vostok.AI.embed(new VKAiEmbeddingRequest()
                .model("rag-embed-embed")
                .input("java runs on jvm")
                .input("python uses interpreter"));
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("mp-java", "java runs on jvm", docs.get(0).getVector(), Map.of()),
                new VKAiVectorDoc("mp-py", "python uses interpreter", docs.get(1).getVector(), Map.of())
        ));

        VKAiRagResponse response = Vostok.AI.rag(new VKAiRagRequest()
                .chatModel("rag-chat-chat")
                .embeddingModel("rag-embed-embed")
                .rerankModel("rag-rerank-rerank")
                .query("what runs on jvm?")
                .topK(1));

        assertEquals("java from alt provider", response.getAnswer().getText());
        assertTrue(embeddingAltCounter.get() >= 2, "ingest + rag query should hit embedding alt provider");
        assertTrue(rerankAltCounter.get() >= 1, "rag rerank should hit rerank alt provider");
        assertEquals(1, ragChatAltCounter.get(), "rag answer should hit chat alt provider");
    }

    @Test
    void testVectorStoreAndSearch() {
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("d1", "java guide", List.of(0.99, 0.01), Map.of("lang", "java")),
                new VKAiVectorDoc("d2", "python guide", List.of(0.01, 0.99), Map.of("lang", "python"))
        ));

        List<VKAiVectorHit> hits = Vostok.AI.searchVector(List.of(1.0, 0.0), 1);
        assertEquals(1, hits.size());
        assertEquals("d1", hits.get(0).getId());
    }

    @Test
    void testRagPipeline() {
        registerModelSet("rag", "/v1/chat/rag", "/v1/embeddings", "/v1/rerank",
                "rag-chat", "rag-embed", "rag-rerank");

        List<VKAiEmbedding> docs = Vostok.AI.embed(new VKAiEmbeddingRequest().model("rag-embed")
                .input("java runs on jvm")
                .input("python uses interpreter"));

        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("doc-java", "java runs on jvm", docs.get(0).getVector(), Map.of()),
                new VKAiVectorDoc("doc-py", "python uses interpreter", docs.get(1).getVector(), Map.of())
        ));

        VKAiRagResponse response = Vostok.AI.rag(new VKAiRagRequest()
                .chatModel("rag-chat")
                .embeddingModel("rag-embed")
                .rerankModel("rag-rerank")
                .query("what runs on jvm?")
                .topK(1));

        assertEquals(1, response.getHits().size());
        assertEquals("doc-java", response.getHits().get(0).getId());
        assertTrue(rerankCounter.get() >= 1);
        assertTrue(response.getAnswer().getText().toLowerCase().contains("jvm"));
    }

    @Test
    void testRagAnswerCacheHit() {
        registerModelSet("rag", "/v1/chat/rag", "/v1/embeddings", "/v1/rerank",
                "rag-chat", "rag-embed", "rag-rerank");
        List<VKAiEmbedding> docs = Vostok.AI.embed(new VKAiEmbeddingRequest().model("rag-embed")
                .input("java runs on jvm")
                .input("python uses interpreter"));
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("doc-java", "java runs on jvm", docs.get(0).getVector(), Map.of()),
                new VKAiVectorDoc("doc-py", "python uses interpreter", docs.get(1).getVector(), Map.of())
        ));

        Vostok.AI.rag(new VKAiRagRequest().chatModel("rag-chat").embeddingModel("rag-embed").rerankModel("rag-rerank").query("what runs on jvm?").topK(1));
        Vostok.AI.rag(new VKAiRagRequest().chatModel("rag-chat").embeddingModel("rag-embed").rerankModel("rag-rerank").query("what runs on jvm?").topK(1));
        assertEquals(1, ragChatCounter.get(), "second rag answer should hit cache");
    }

    @Test
    void testRagIngestChunkOverlapDedupAndVersion() {
        registerModelSet("rag", "/v1/chat/rag", "/v1/embeddings", "/v1/rerank",
                "rag-chat", "rag-embed", "rag-rerank");

        VKAiRagIngestResult v1 = Vostok.AI.ingestRagDocument(new VKAiRagIngestRequest()
                .model("rag-embed")
                .documentId("doc-1")
                .version("v1")
                .chunkSize(12)
                .chunkOverlap(0)
                .text("repeatblock repeatblock repeatblock ")
                .deduplicate(true));

        assertTrue(v1.getTotalChunks() >= 2);
        assertTrue(v1.getSkippedDuplicateChunks() >= 1);
        assertTrue(v1.getInsertedChunks() < v1.getTotalChunks());

        VKAiRagIngestResult v2 = Vostok.AI.ingestRagDocument(new VKAiRagIngestRequest()
                .model("rag-embed")
                .documentId("doc-1")
                .version("v2")
                .chunkSize(64)
                .chunkOverlap(8)
                .text("python interpreter runtime")
                .deduplicate(true));
        assertTrue(v2.getInsertedChunks() >= 1);

        List<VKAiVectorHit> oldHits = Vostok.AI.searchKeywords("repeatblock", 5);
        assertTrue(oldHits.isEmpty(), "old version chunks should be removed");
        List<VKAiVectorHit> pyHits = Vostok.AI.searchKeywords("interpreter", 5);
        assertFalse(pyHits.isEmpty());
        assertEquals("v2", pyHits.get(0).getMetadata().get("vk_doc_version"));
    }

    @Test
    void testRagHybridRecallVectorAndKeyword() {
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("h1", "rarekeyword-only content", List.of(0.10, 0.10), Map.of()),
                new VKAiVectorDoc("h2", "generic content", List.of(0.10, 0.10), Map.of())
        ));

        VKAiRagResponse response = Vostok.AI.rag(new VKAiRagRequest()
                .chatModel("demo-chat")
                .embeddingModel("demo-embed")
                .rerankModel("demo-rerank")
                .query("rarekeyword")
                .topK(1)
                .rerankEnabled(false)
                .vectorWeight(0.2)
                .keywordWeight(0.8));

        assertEquals(1, response.getHits().size());
        assertEquals("h1", response.getHits().get(0).getId());
    }

    @Test
    void testHistoryTrimOnChatRequest() {
        Vostok.AI.chat(new VKAiChatRequest()
                .model("demo-chat")
                .historyMaxMessages(3)
                .historyMaxChars(120)
                .message("user", "m1")
                .message("assistant", "m2")
                .message("user", "m3")
                .message("assistant", "m4")
                .message("user", "m5"));

        assertTrue(chatMessageCount.get() <= 3);
        assertTrue(chatMessageCount.get() >= 1);
    }

    @Test
    void testRagLightQueryRewrite() {
        registerModelSet("rag", "/v1/chat/rag", "/v1/embeddings", "/v1/rerank",
                "rag-chat", "rag-embed", "rag-rerank");
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("q1", "java runs on jvm", List.of(0.95, 0.05), Map.of()),
                new VKAiVectorDoc("q2", "python uses interpreter", List.of(0.05, 0.95), Map.of())
        ));

        String longQuery = "这是一段很长很长的业务背景描述，包含很多无关细节和上下文信息，"
                + "我们正在讨论服务端架构演进、部署方式、日志和监控等。请问到底什么运行在jvm上，"
                + "并且给出关键结论和依据。";
        Vostok.AI.rag(new VKAiRagRequest()
                .chatModel("rag-chat")
                .embeddingModel("rag-embed")
                .rerankModel("rag-rerank")
                .query(longQuery)
                .topK(2)
                .dynamicTopKEnabled(false));

        assertNotNull(lastEmbeddingInput.get());
        assertTrue(lastEmbeddingInput.get().length() <= 128);
        assertTrue(lastEmbeddingInput.get().contains("jvm"));
    }

    @Test
    void testRagDynamicTopK() {
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("k1", "java jvm one", List.of(0.95, 0.05), Map.of()),
                new VKAiVectorDoc("k2", "java jvm two", List.of(0.94, 0.06), Map.of()),
                new VKAiVectorDoc("k3", "java jvm three", List.of(0.93, 0.07), Map.of()),
                new VKAiVectorDoc("k4", "java jvm four", List.of(0.92, 0.08), Map.of())
        ));

        VKAiRagResponse response = Vostok.AI.rag(new VKAiRagRequest()
                .chatModel("demo-chat")
                .embeddingModel("demo-embed")
                .rerankModel("demo-rerank")
                .query("jvm")
                .topK(4)
                .queryRewriteEnabled(false)
                .rerankEnabled(false));

        assertTrue(response.getHits().size() <= 2);
    }

    @Test
    void testRagMergeSimilarChunksAndContextCompression() {
        registerModelSet("rag", "/v1/chat/rag", "/v1/embeddings", "/v1/rerank",
                "rag-chat", "rag-embed", "rag-rerank");
        String longA = "partA java jvm " + "alpha ".repeat(40);
        String longB = "partB java jvm " + "beta ".repeat(40);
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("m1", longA, List.of(0.95, 0.05),
                        Map.of("vk_doc_id", "doc-merge", "vk_doc_version", "v1", "vk_chunk_index", "0")),
                new VKAiVectorDoc("m2", longB, List.of(0.94, 0.06),
                        Map.of("vk_doc_id", "doc-merge", "vk_doc_version", "v1", "vk_chunk_index", "1")),
                new VKAiVectorDoc("m3", "python interpreter", List.of(0.05, 0.95), Map.of())
        ));

        VKAiRagResponse response = Vostok.AI.rag(new VKAiRagRequest()
                .chatModel("rag-chat")
                .embeddingModel("rag-embed")
                .rerankModel("rag-rerank")
                .query("java jvm")
                .topK(4)
                .queryRewriteEnabled(false)
                .dynamicTopKEnabled(false)
                .rerankEnabled(false)
                .contextMaxCharsPerChunk(80)
                .contextMaxChars(140));

        assertTrue(response.getHits().stream().anyMatch(h -> h.getMetadata().containsKey("vk_chunk_span")));
        assertTrue(response.getHits().stream().allMatch(h -> h.getText().length() <= 83));
        assertNotNull(lastRagUserPrompt.get());
        assertTrue(lastRagUserPrompt.get().length() < 320);
    }

    private void registerModelSet(String profileName,
                                 String chatPath,
                                 String embeddingPath,
                                 String rerankPath,
                                 String chatModel,
                                 String embeddingModel,
                                 String rerankModel) {
        String resolvedChatPath = chatPath == null ? "/v1/chat/completions" : chatPath;
        String resolvedEmbeddingPath = embeddingPath == null ? "/v1/embeddings" : embeddingPath;
        String resolvedRerankPath = rerankPath == null ? "/v1/rerank" : rerankPath;

        String chatModelId = profileName + "-chat";
        Vostok.AI.registerModel(chatModelId, new VKAiModelConfig()
                .type(VKAiModelType.CHAT)
                .provider("openai-compatible")
                .baseUrl(baseUrl)
                .path(resolvedChatPath)
                .apiKey("demo-key")
                .model(chatModel));

        if (embeddingModel != null) {
            Vostok.AI.registerModel(profileName + "-embed", new VKAiModelConfig()
                    .type(VKAiModelType.EMBEDDING)
                    .provider("openai-compatible")
                    .baseUrl(baseUrl)
                    .path(resolvedEmbeddingPath)
                    .apiKey("demo-key")
                    .model(embeddingModel));
        }

        if (rerankModel != null) {
            Vostok.AI.registerModel(profileName + "-rerank", new VKAiModelConfig()
                    .type(VKAiModelType.RERANK)
                    .provider("openai-compatible")
                    .baseUrl(baseUrl)
                    .path(resolvedRerankPath)
                    .apiKey("demo-key")
                    .model(rerankModel));
        }
    }

    private VKAiTool sumTool() {
        return new VKAiTool() {
            @Override
            public String name() {
                return "sum";
            }

            @Override
            public String description() {
                return "sum two integers";
            }

            @Override
            public String inputJsonSchema() {
                return "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\"},\"b\":{\"type\":\"integer\"}}}";
            }

            @Override
            public String outputJsonSchema() {
                return "{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"integer\"}}}";
            }

            @Override
            public VKAiToolResult invoke(String inputJson) {
                Map<?, ?> in = VKJson.fromJson(inputJson, Map.class);
                int a = in.get("a") == null ? 0 : ((Number) in.get("a")).intValue();
                int b = in.get("b") == null ? 0 : ((Number) in.get("b")).intValue();
                return VKAiToolResult.ofJson("{\"result\":" + (a + b) + "}");
            }
        };
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> req = VKJson.fromJson(body, Map.class);
        Object messages = req.get("messages");
        if (messages instanceof List<?> list) {
            chatMessageCount.set(list.size());
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (!"Bearer demo-key".equals(auth)) {
            write(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        write(exchange, 200, "{\"id\":\"req-1\",\"choices\":[{\"message\":{\"content\":\"hello from ai\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}");
    }

    private void handleJsonChat(HttpExchange exchange) throws IOException {
        write(exchange, 200, "{\"id\":\"req-json\",\"choices\":[{\"message\":{\"content\":\"{\\\"name\\\":\\\"neo\\\",\\\"score\\\":99}\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7}}");
    }

    private void handleRetryChat(HttpExchange exchange) throws IOException {
        int count = retryCounter.incrementAndGet();
        if (count == 1) {
            write(exchange, 503, "{\"error\":\"busy\"}");
            return;
        }
        write(exchange, 200, "{\"id\":\"req-retry\",\"choices\":[{\"message\":{\"content\":\"retry-ok\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}");
    }

    private void handleFailChat(HttpExchange exchange) throws IOException {
        write(exchange, 400, "{\"error\":\"bad request\"}");
    }

    private void handleInvalidJsonChat(HttpExchange exchange) throws IOException {
        write(exchange, 200, "{\"id\":\"req-invalid\",\"choices\":[{\"message\":{\"content\":\"not-json\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}");
    }

    private void handleClientA(HttpExchange exchange) throws IOException {
        write(exchange, 200, "{\"id\":\"req-a\",\"choices\":[{\"message\":{\"content\":\"from-client-a\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}");
    }

    private void handleClientB(HttpExchange exchange) throws IOException {
        write(exchange, 200, "{\"id\":\"req-b\",\"choices\":[{\"message\":{\"content\":\"from-client-b\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}");
    }

    private void handleToolChat(HttpExchange exchange) throws IOException {
        write(exchange, 200,
                "{\"id\":\"req-tool\",\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":[{\"id\":\"tc1\",\"type\":\"function\",\"function\":{\"name\":\"sum\",\"arguments\":\"{\\\"a\\\":2,\\\"b\\\":3}\"}}]},\"finish_reason\":\"tool-call\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":2,\"total_tokens\":4}}");
    }

    private void handleToolMissingChat(HttpExchange exchange) throws IOException {
        write(exchange, 200,
                "{\"id\":\"req-tool-miss\",\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":[{\"id\":\"tc2\",\"type\":\"function\",\"function\":{\"name\":\"missing_tool\",\"arguments\":\"{}\"}}]},\"finish_reason\":\"tool-call\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":2,\"total_tokens\":4}}");
    }

    private void handleEmbeddings(HttpExchange exchange) throws IOException {
        embeddingCounter.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> req = VKJson.fromJson(body, Map.class);
        String model = req.get("model") == null ? null : String.valueOf(req.get("model"));
        lastEmbeddingModel.set(model);
        if (model == null || model.isBlank() || model.contains("bad-embed")) {
            write(exchange, 404, "{\"error\":\"embedding model not found\"}");
            return;
        }
        List<?> input = (List<?>) req.get("input");
        if (input != null && !input.isEmpty()) {
            lastEmbeddingInput.set(String.valueOf(input.get(0)));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[");
        for (int i = 0; i < input.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String text = String.valueOf(input.get(i)).toLowerCase();
            double v1 = text.contains("java") || text.contains("jvm") ? 0.95 : 0.05;
            double v2 = text.contains("python") ? 0.95 : 0.05;
            sb.append("{\"index\":").append(i).append(",\"embedding\":[").append(v1).append(',').append(v2).append("]}");
        }
        sb.append("],\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}");
        write(exchange, 200, sb.toString());
    }

    private void handleRerank(HttpExchange exchange) throws IOException {
        rerankCounter.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> req = VKJson.fromJson(body, Map.class);
        String model = req.get("model") == null ? null : String.valueOf(req.get("model"));
        lastRerankModel.set(model);
        if (model == null || model.isBlank() || model.contains("bad-rerank")) {
            write(exchange, 404, "{\"error\":\"rerank model not found\"}");
            return;
        }
        String query = String.valueOf(req.get("query")).toLowerCase();
        List<?> docs = (List<?>) req.get("documents");
        int top1 = 0;
        double best = -1;
        for (int i = 0; i < docs.size(); i++) {
            String d = String.valueOf(docs.get(i)).toLowerCase();
            double score = (query.contains("jvm") && d.contains("java")) ? 0.98 : 0.2;
            if (score > best) {
                best = score;
                top1 = i;
            }
        }
        int other = top1 == 0 ? 1 : 0;
        write(exchange, 200,
                "{\"results\":[{\"index\":" + top1 + ",\"score\":0.98},{\"index\":" + other + ",\"score\":0.12}]}" );
    }

    private void handleEmbeddingsAlt(HttpExchange exchange) throws IOException {
        embeddingAltCounter.incrementAndGet();
        handleEmbeddings(exchange);
    }

    private void handleRerankAlt(HttpExchange exchange) throws IOException {
        rerankAltCounter.incrementAndGet();
        handleRerank(exchange);
    }

    private void handleRagChat(HttpExchange exchange) throws IOException {
        ragChatCounter.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> req = VKJson.fromJson(body, Map.class);
        Object messages = req.get("messages");
        if (messages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                if ("user".equals(String.valueOf(m.get("role")))) {
                    lastRagUserPrompt.set(String.valueOf(m.get("content")));
                }
            }
        }
        write(exchange, 200,
                "{\"id\":\"req-rag\",\"choices\":[{\"message\":{\"content\":\"Java runs on the JVM.\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":6,\"total_tokens\":14}}" );
    }

    private void handleRagChatAlt(HttpExchange exchange) throws IOException {
        ragChatAltCounter.incrementAndGet();
        write(exchange, 200,
                "{\"id\":\"req-rag-alt\",\"choices\":[{\"message\":{\"content\":\"java from alt provider\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":6,\"total_tokens\":14}}" );
    }

    private void handleSessionChat(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> req = VKJson.fromJson(body, Map.class);
        String model = req.get("model") == null ? "" : String.valueOf(req.get("model"));
        lastSessionModel.set(model);
        Object messages = req.get("messages");
        int size = messages instanceof List<?> list ? list.size() : 0;
        lastSessionMsgCount.set(size);
        write(exchange, 200, "{\"id\":\"req-session\",\"choices\":[{\"message\":{\"content\":\"session-ok model=" + model + " msgs=" + size + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":2,\"total_tokens\":4}}");
    }

    private void handleStreamChat(HttpExchange exchange) throws IOException {
        streamChatCounter.incrementAndGet();
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        String chunk1 = "data: {\"id\":\"req-stream\",\"choices\":[{\"delta\":{\"content\":\"hel\"}}]}\n\n";
        String chunk2 = "data: {\"id\":\"req-stream\",\"choices\":[{\"delta\":{\"content\":\"lo\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}\n\n";
        String chunk3 = "data: {\"id\":\"req-stream\",\"choices\":[{\"finish_reason\":\"stop\"}]}\n\n";
        String chunk4 = "data: [DONE]\n\n";
        exchange.getResponseBody().write(chunk1.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().write(chunk2.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().write(chunk3.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().write(chunk4.getBytes(StandardCharsets.UTF_8));
        exchange.close();
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static class ScoreResult {
        public String name;
        public int score;
    }
}
