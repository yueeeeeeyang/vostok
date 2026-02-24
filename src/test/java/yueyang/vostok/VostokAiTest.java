package yueyang.vostok;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.ai.VKAiAuditRecord;
import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.VKAiChatResponse;
import yueyang.vostok.ai.provider.VKAiClientConfig;
import yueyang.vostok.ai.VKAiConfig;
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
import yueyang.vostok.util.json.VKJson;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VostokAiTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger retryCounter = new AtomicInteger();
    private final AtomicInteger rerankCounter = new AtomicInteger();
    private final AtomicInteger embeddingCounter = new AtomicInteger();
    private final AtomicInteger ragChatCounter = new AtomicInteger();

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
        server.createContext("/v1/embeddings", this::handleEmbeddings);
        server.createContext("/v1/rerank", this::handleRerank);

        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        Vostok.AI.close();
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

        Vostok.AI.registerClient("demo", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .apiKey("demo-key"));
        Vostok.AI.clearTools();
        Vostok.AI.clearAudits();
        Vostok.AI.clearVectorStore();
        retryCounter.set(0);
        rerankCounter.set(0);
        embeddingCounter.set(0);
        ragChatCounter.set(0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        Vostok.AI.close();
        Vostok.Cache.close();
    }

    @Test
    void testChatSuccessAndUsage() {
        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .client("demo")
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
        Vostok.AI.registerClient("json", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .chatPath("/v1/chat/json"));

        ScoreResult result = Vostok.AI.chatJson(new VKAiChatRequest()
                .client("json")
                .message("user", "return json")
                .model("m-json"), ScoreResult.class);

        assertEquals("neo", result.name);
        assertEquals(99, result.score);
    }

    @Test
    void testWithClientContextAndClientSelection() {
        Vostok.AI.registerClient("a", new VKAiClientConfig().baseUrl(baseUrl).chatPath("/v1/chat/clientA"));
        Vostok.AI.registerClient("b", new VKAiClientConfig().baseUrl(baseUrl).chatPath("/v1/chat/clientB"));

        String a = Vostok.AI.withClient("a", () -> Vostok.AI.chat(new VKAiChatRequest().message("user", "x")).getText());
        String b = Vostok.AI.withClient("b", () -> Vostok.AI.chat(new VKAiChatRequest().message("user", "x")).getText());

        assertEquals("from-client-a", a);
        assertEquals("from-client-b", b);
    }

    @Test
    void testRetryOn5xxAndMetrics() {
        Vostok.AI.registerClient("retry", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .chatPath("/v1/chat/retry"));

        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .client("retry")
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
        Vostok.AI.registerClient("fail", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .chatPath("/v1/chat/fail")
                .failOnNon2xx(true));

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest().client("fail").message("user", "x")));

        assertEquals(VKAiErrorCode.HTTP_STATUS, ex.getCode());
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void testJsonParseError() {
        Vostok.AI.registerClient("invalid", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .chatPath("/v1/chat/invalid"));

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chatJson(new VKAiChatRequest().client("invalid").message("user", "x"), ScoreResult.class));

        assertEquals(VKAiErrorCode.JSON_PARSE_ERROR, ex.getCode());
    }

    @Test
    void testChatAsync() throws Exception {
        VKAiChatResponse response = Vostok.AI.chatAsync(new VKAiChatRequest()
                        .client("demo")
                        .message("user", "async"))
                .get(2, TimeUnit.SECONDS);

        assertEquals("hello from ai", response.getText());
    }

    @Test
    void testClientValidationAndMissingClient() {
        VKAiException registerError = assertThrows(VKAiException.class,
                () -> Vostok.AI.registerClient("bad", new VKAiClientConfig()));
        assertEquals(VKAiErrorCode.CONFIG_ERROR, registerError.getCode());

        Vostok.AI.close();
        Vostok.AI.init(new VKAiConfig());

        VKAiException noClient = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest().message("user", "x")));
        assertEquals(VKAiErrorCode.CONFIG_ERROR, noClient.getCode());
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
        Vostok.AI.registerClient("tool", new VKAiClientConfig().baseUrl(baseUrl).chatPath("/v1/chat/tool"));
        Vostok.AI.registerTool(sumTool());

        VKAiChatResponse response = Vostok.AI.chat(new VKAiChatRequest()
                .client("tool")
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
        Vostok.AI.registerClient("tool", new VKAiClientConfig().baseUrl(baseUrl).chatPath("/v1/chat/tool"));
        Vostok.AI.registerTool(sumTool());

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest()
                        .client("tool")
                        .allowTool("other")
                        .message("user", "calc")));

        assertEquals(VKAiErrorCode.TOOL_DENIED, ex.getCode());
    }

    @Test
    void testToolNotFound() {
        Vostok.AI.registerClient("tool-missing", new VKAiClientConfig().baseUrl(baseUrl).chatPath("/v1/chat/tool-missing"));

        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest()
                        .client("tool-missing")
                        .allowTool("missing_tool")
                        .message("user", "calc")));

        assertEquals(VKAiErrorCode.TOOL_NOT_FOUND, ex.getCode());
    }

    @Test
    void testSecurityBlockForPrompt() {
        VKAiException ex = assertThrows(VKAiException.class,
                () -> Vostok.AI.chat(new VKAiChatRequest()
                        .client("demo")
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
        Vostok.AI.chat(new VKAiChatRequest().client("demo").message("user", "hello"));
        Vostok.AI.chat(new VKAiChatRequest().client("demo").message("user", "hello2"));

        List<VKAiAuditRecord> last = Vostok.AI.audits(1);
        assertEquals(1, last.size());

        Vostok.AI.clearAudits();
        assertTrue(Vostok.AI.audits(10).isEmpty());
    }

    @Test
    void testEmbedding() {
        List<VKAiEmbedding> out = Vostok.AI.embed(new VKAiEmbeddingRequest()
                .client("demo")
                .input("java")
                .input("python"));
        assertEquals(2, out.size());
        assertEquals(2, out.get(0).getVector().size());
    }

    @Test
    void testEmbeddingCacheHit() {
        List<VKAiEmbedding> first = Vostok.AI.embed(new VKAiEmbeddingRequest().client("demo").input("java"));
        List<VKAiEmbedding> second = Vostok.AI.embed(new VKAiEmbeddingRequest().client("demo").input("java"));
        assertEquals(first.get(0).getVector(), second.get(0).getVector());
        assertEquals(1, embeddingCounter.get(), "second embed should hit cache");
    }

    @Test
    void testRerank() {
        VKAiRerankResponse res = Vostok.AI.rerank(new VKAiRerankRequest()
                .client("demo")
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
                .client("demo")
                .query("jvm")
                .document("python language")
                .document("java jvm tuning")
                .topK(2));
        Vostok.AI.rerank(new VKAiRerankRequest()
                .client("demo")
                .query("jvm")
                .document("python language")
                .document("java jvm tuning")
                .topK(2));
        assertEquals(1, rerankCounter.get(), "second rerank should hit cache");
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
        Vostok.AI.registerClient("rag", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .chatPath("/v1/chat/rag")
                .embeddingPath("/v1/embeddings"));

        List<VKAiEmbedding> docs = Vostok.AI.embed(new VKAiEmbeddingRequest().client("rag")
                .input("java runs on jvm")
                .input("python uses interpreter"));

        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("doc-java", "java runs on jvm", docs.get(0).getVector(), Map.of()),
                new VKAiVectorDoc("doc-py", "python uses interpreter", docs.get(1).getVector(), Map.of())
        ));

        VKAiRagResponse response = Vostok.AI.rag(new VKAiRagRequest()
                .client("rag")
                .query("what runs on jvm?")
                .topK(1));

        assertEquals(1, response.getHits().size());
        assertEquals("doc-java", response.getHits().get(0).getId());
        assertTrue(rerankCounter.get() >= 1);
        assertTrue(response.getAnswer().getText().toLowerCase().contains("jvm"));
    }

    @Test
    void testRagAnswerCacheHit() {
        Vostok.AI.registerClient("rag", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .chatPath("/v1/chat/rag")
                .embeddingPath("/v1/embeddings")
                .rerankPath("/v1/rerank"));
        List<VKAiEmbedding> docs = Vostok.AI.embed(new VKAiEmbeddingRequest().client("rag")
                .input("java runs on jvm")
                .input("python uses interpreter"));
        Vostok.AI.upsertVectorDocs(List.of(
                new VKAiVectorDoc("doc-java", "java runs on jvm", docs.get(0).getVector(), Map.of()),
                new VKAiVectorDoc("doc-py", "python uses interpreter", docs.get(1).getVector(), Map.of())
        ));

        Vostok.AI.rag(new VKAiRagRequest().client("rag").query("what runs on jvm?").topK(1));
        Vostok.AI.rag(new VKAiRagRequest().client("rag").query("what runs on jvm?").topK(1));
        assertEquals(1, ragChatCounter.get(), "second rag answer should hit cache");
    }

    @Test
    void testRagIngestChunkOverlapDedupAndVersion() {
        Vostok.AI.registerClient("rag", new VKAiClientConfig()
                .baseUrl(baseUrl)
                .embeddingPath("/v1/embeddings")
                .rerankPath("/v1/rerank"));

        VKAiRagIngestResult v1 = Vostok.AI.ingestRagDocument(new VKAiRagIngestRequest()
                .client("rag")
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
                .client("rag")
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
                .client("demo")
                .query("rarekeyword")
                .topK(1)
                .rerankEnabled(false)
                .vectorWeight(0.2)
                .keywordWeight(0.8));

        assertEquals(1, response.getHits().size());
        assertEquals("h1", response.getHits().get(0).getId());
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
        List<?> input = (List<?>) req.get("input");
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

    private void handleRagChat(HttpExchange exchange) throws IOException {
        ragChatCounter.incrementAndGet();
        write(exchange, 200,
                "{\"id\":\"req-rag\",\"choices\":[{\"message\":{\"content\":\"Java runs on the JVM.\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":6,\"total_tokens\":14}}" );
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
