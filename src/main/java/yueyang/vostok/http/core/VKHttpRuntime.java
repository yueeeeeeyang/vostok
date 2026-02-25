package yueyang.vostok.http.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.http.VKHttpChunkListener;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.VKHttpRequest;
import yueyang.vostok.http.VKHttpResponse;
import yueyang.vostok.http.VKHttpResponseMeta;
import yueyang.vostok.http.VKHttpSseEvent;
import yueyang.vostok.http.VKHttpSseListener;
import yueyang.vostok.http.VKHttpStreamSession;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class VKHttpRuntime {
    private static final Object LOCK = new Object();
    private static final VKHttpRuntime INSTANCE = new VKHttpRuntime();

    private static final String DEFAULT_CLIENT_KEY = "__default__";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE");

    private final ConcurrentHashMap<String, VKHttpClientConfig> clients = new ConcurrentHashMap<>();
    private final ThreadLocal<String> clientContext = new ThreadLocal<>();
    private final HttpMetrics metrics = new HttpMetrics();

    private final ConcurrentHashMap<String, ClientRuntimeEntry> clientRuntimeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResilienceRuntime> resilienceCache = new ConcurrentHashMap<>();
    private final AtomicLong executeSeq = new AtomicLong();
    private final AtomicLong clientBuilds = new AtomicLong();
    private volatile ExecutorService streamExecutor;

    private volatile VKHttpConfig config = new VKHttpConfig();
    private volatile boolean initialized;

    private VKHttpRuntime() {
    }

    public static VKHttpRuntime getInstance() {
        return INSTANCE;
    }

    public void init(VKHttpConfig cfg) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = (cfg == null ? new VKHttpConfig() : cfg.copy());
            initialized = true;
            clearClientRuntimes();
            refreshStreamExecutor();
        }
    }

    public void reinit(VKHttpConfig cfg) {
        synchronized (LOCK) {
            config = (cfg == null ? new VKHttpConfig() : cfg.copy());
            initialized = true;
            clearClientRuntimes();
            refreshStreamExecutor();
        }
    }

    public boolean started() {
        return initialized;
    }

    public VKHttpConfig config() {
        return config.copy();
    }

    public void close() {
        synchronized (LOCK) {
            clients.clear();
            clientContext.remove();
            metrics.reset();
            clearClientRuntimes();
            clientBuilds.set(0);
            executeSeq.set(0);
            closeStreamExecutor();
            config = new VKHttpConfig();
            initialized = false;
        }
    }

    public void registerClient(String name, VKHttpClientConfig cfg) {
        ensureInit();
        if (name == null || name.isBlank()) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "Client name is blank");
        }
        if (cfg == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "VKHttpClientConfig is null");
        }
        clients.put(name.trim(), cfg.copy());
        clearClientRuntimes();
    }

    public void withClient(String name, Runnable action) {
        if (action == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "Runnable is null");
        }
        withClient(name, () -> {
            action.run();
            return null;
        });
    }

    public <T> T withClient(String name, Supplier<T> supplier) {
        ensureInit();
        if (supplier == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "Supplier is null");
        }
        String prev = clientContext.get();
        if (name == null || name.isBlank()) {
            clientContext.remove();
        } else {
            String n = name.trim();
            if (!clients.containsKey(n)) {
                throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR, "Http client not found: " + n);
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

    public VKHttpResponse execute(VKHttpRequest request) {
        ensureInit();
        if (request == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "VKHttpRequest is null");
        }

        maybeEvictIdleRuntimes();

        long start = System.currentTimeMillis();
        ResolvedRequest resolved = resolveRequest(request, false);

        int attempts = 0;
        while (true) {
            attempts++;
            long retryIndex = attempts - 1L;
            boolean bulkheadAcquired = false;
            try {
                if (resolved.policy().rateLimitEnabled() && resolved.resilience().rateLimiter != null
                        && !resolved.resilience().rateLimiter.tryAcquire()) {
                    throw new VKHttpException(VKHttpErrorCode.RATE_LIMITED, "Http rate limit exceeded");
                }
                if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null) {
                    resolved.resilience().circuitBreaker.beforeCall();
                }
                if (resolved.policy().bulkheadEnabled() && resolved.resilience().bulkhead != null) {
                    resolved.resilience().bulkhead.acquire();
                    bulkheadAcquired = true;
                }

                HttpResponse<byte[]> raw = executeOnce(resolved);
                byte[] body = raw.body() == null ? new byte[0] : raw.body();
                if (body.length > resolved.policy().maxResponseBodyBytes()) {
                    throw new VKHttpException(VKHttpErrorCode.RESPONSE_TOO_LARGE,
                            "Response body exceeds limit: " + body.length + " > " + resolved.policy().maxResponseBodyBytes());
                }

                int status = raw.statusCode();
                if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null) {
                    if (resolved.policy().circuitRecordStatuses().contains(status)) {
                        resolved.resilience().circuitBreaker.onFailure();
                    } else {
                        resolved.resilience().circuitBreaker.onSuccess();
                    }
                }
                if (shouldRetryByStatus(resolved, status, retryIndex)) {
                    if (config.isMetricsEnabled()) {
                        metrics.recordRetry();
                    }
                    long waitMs = computeRetryDelayMs(resolved, retryIndex, raw.headers().firstValue("Retry-After").orElse(null));
                    sleepRetry(waitMs);
                    continue;
                }

                VKHttpResponse response = new VKHttpResponse(status, raw.headers().map(), body);
                long costMs = System.currentTimeMillis() - start;
                if (config.isMetricsEnabled()) {
                    metrics.recordResponse(response.statusCode(), costMs);
                }
                logCall(resolved, response.statusCode(), costMs, retryIndex);

                if (resolved.policy().failOnNon2xx() && !response.is2xx()) {
                    throw new VKHttpException(VKHttpErrorCode.HTTP_STATUS,
                            "HTTP call failed with status=" + response.statusCode(), response.statusCode());
                }
                return response;
            } catch (VKHttpException e) {
                if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null
                        && shouldRecordCircuitFailure(e)) {
                    resolved.resilience().circuitBreaker.onFailure();
                }
                if (shouldRetryByError(resolved, e, retryIndex)) {
                    if (config.isMetricsEnabled()) {
                        metrics.recordRetry();
                    }
                    long waitMs = computeRetryDelayMs(resolved, retryIndex, null);
                    sleepRetry(waitMs);
                    continue;
                }

                if (config.isMetricsEnabled() && e.getCode() != VKHttpErrorCode.HTTP_STATUS) {
                    metrics.recordFailure(e.getCode(), System.currentTimeMillis() - start);
                }
                throw e;
            } finally {
                if (bulkheadAcquired && resolved.resilience().bulkhead != null) {
                    resolved.resilience().bulkhead.release();
                }
            }
        }
    }

    public <T> T executeJson(VKHttpRequest request, Class<T> type) {
        if (type == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "Response type is null");
        }
        return execute(request).bodyJson(type);
    }

    public CompletableFuture<VKHttpResponse> executeAsync(VKHttpRequest request) {
        return CompletableFuture.supplyAsync(() -> execute(request));
    }

    public <T> CompletableFuture<T> executeJsonAsync(VKHttpRequest request, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> executeJson(request, type));
    }

    public VKHttpStreamSession openSse(VKHttpRequest request, VKHttpSseListener listener) {
        return openStreamInternal(request, listener, null);
    }

    public void executeSse(VKHttpRequest request, VKHttpSseListener listener) {
        StreamSessionImpl session = requireSession(openSse(request, listener));
        session.await();
    }

    public CompletableFuture<Void> executeSseAsync(VKHttpRequest request, VKHttpSseListener listener) {
        return CompletableFuture.runAsync(() -> executeSse(request, listener));
    }

    public VKHttpStreamSession openStream(VKHttpRequest request, VKHttpChunkListener listener) {
        return openStreamInternal(request, null, listener);
    }

    public void executeStream(VKHttpRequest request, VKHttpChunkListener listener) {
        StreamSessionImpl session = requireSession(openStream(request, listener));
        session.await();
    }

    public CompletableFuture<Void> executeStreamAsync(VKHttpRequest request, VKHttpChunkListener listener) {
        return CompletableFuture.runAsync(() -> executeStream(request, listener));
    }

    private VKHttpStreamSession openStreamInternal(VKHttpRequest request, VKHttpSseListener sseListener, VKHttpChunkListener chunkListener) {
        ensureInit();
        if (request == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "VKHttpRequest is null");
        }
        if (sseListener == null && chunkListener == null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "Stream listener is null");
        }
        if (sseListener != null && chunkListener != null) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "Only one stream listener type is allowed");
        }

        maybeEvictIdleRuntimes();
        long start = System.currentTimeMillis();
        ResolvedRequest resolved = resolveRequest(request, true);
        if (!resolved.policy().streamEnabled()) {
            throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR, "Http stream is disabled");
        }

        boolean bulkheadAcquired = false;
        try {
            if (resolved.policy().rateLimitEnabled() && resolved.resilience().rateLimiter != null
                    && !resolved.resilience().rateLimiter.tryAcquire()) {
                throw new VKHttpException(VKHttpErrorCode.RATE_LIMITED, "Http rate limit exceeded");
            }
            if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null) {
                resolved.resilience().circuitBreaker.beforeCall();
            }
            if (resolved.policy().bulkheadEnabled() && resolved.resilience().bulkhead != null) {
                resolved.resilience().bulkhead.acquire();
                bulkheadAcquired = true;
            }

            HttpResponse<InputStream> raw = executeOnceStream(resolved);
            int status = raw.statusCode();
            if (resolved.policy().failOnNon2xx() && (status < 200 || status >= 300)) {
                String body = readBodySafely(raw.body(), resolved.policy().maxResponseBodyBytes());
                closeQuietly(raw.body());
                if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null) {
                    if (resolved.policy().circuitRecordStatuses().contains(status)) {
                        resolved.resilience().circuitBreaker.onFailure();
                    } else {
                        resolved.resilience().circuitBreaker.onSuccess();
                    }
                }
                if (config.isMetricsEnabled()) {
                    metrics.recordResponse(status, System.currentTimeMillis() - start);
                }
                logCall(resolved, status, System.currentTimeMillis() - start, 0);
                throw new VKHttpException(VKHttpErrorCode.HTTP_STATUS,
                        "HTTP stream failed with status=" + status + ", body=" + abbreviate(body, 256),
                        status);
            }

            String contentType = firstHeader(raw.headers().map(), "Content-Type");
            if (sseListener != null && (contentType == null || !contentType.toLowerCase().contains("text/event-stream"))) {
                closeQuietly(raw.body());
                if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null) {
                    resolved.resilience().circuitBreaker.onFailure();
                }
                throw new VKHttpException(VKHttpErrorCode.STREAM_OPEN_FAILED,
                        "SSE requires content-type text/event-stream, actual=" + contentType);
            }

            StreamSessionImpl session = new StreamSessionImpl(
                    raw.body(),
                    bulkheadAcquired ? resolved.resilience().bulkhead : null,
                    resolved.policy().circuitEnabled() ? resolved.resilience().circuitBreaker : null,
                    resolved.policy().circuitRecordStatuses().contains(status)
            );

            if (config.isMetricsEnabled()) {
                metrics.recordResponse(status, System.currentTimeMillis() - start);
                metrics.recordStreamOpen();
            }
            if (config.isLogEnabled()) {
                Vostok.Log.info("Vostok.Http stream-open {} {} status={} client={}",
                        resolved.method(), resolved.url(), status, resolved.clientName());
            }
            VKHttpResponseMeta meta = new VKHttpResponseMeta(status, raw.headers().map());
            ExecutorService executor = streamExecutor;
            if (executor == null) {
                throw new VKHttpException(VKHttpErrorCode.STATE_ERROR, "Http stream executor not initialized");
            }
            try {
                executor.execute(() -> {
                    if (sseListener != null) {
                        consumeSse(meta, resolved, sseListener, session);
                    } else {
                        consumeChunks(meta, resolved.policy().streamIdleTimeoutMs(), chunkListener, session);
                    }
                });
            } catch (RuntimeException e) {
                VKHttpException ex = new VKHttpException(VKHttpErrorCode.STATE_ERROR, "Http stream task rejected", e);
                if (config.isMetricsEnabled()) {
                    metrics.recordStreamError();
                }
                closeQuietly(raw.body());
                throw ex;
            }
            return session;
        } catch (VKHttpException e) {
            if (bulkheadAcquired && resolved.resilience().bulkhead != null) {
                resolved.resilience().bulkhead.release();
            }
            if (resolved.policy().circuitEnabled() && resolved.resilience().circuitBreaker != null
                    && shouldRecordCircuitFailure(e)) {
                resolved.resilience().circuitBreaker.onFailure();
            }
            if (config.isMetricsEnabled() && e.getCode() != VKHttpErrorCode.HTTP_STATUS) {
                metrics.recordFailure(e.getCode(), System.currentTimeMillis() - start);
            }
            throw e;
        }
    }

    private static StreamSessionImpl requireSession(VKHttpStreamSession session) {
        if (session instanceof StreamSessionImpl impl) {
            return impl;
        }
        throw new VKHttpException(VKHttpErrorCode.STATE_ERROR, "Unexpected stream session implementation");
    }

    public VKHttpMetrics metrics() {
        return metrics.snapshot();
    }

    public void resetMetrics() {
        metrics.reset();
    }

    int clientRuntimeCountForTests() {
        return clientRuntimeCache.size();
    }

    long clientBuildCountForTests() {
        return clientBuilds.get();
    }

    private HttpResponse<byte[]> executeOnce(ResolvedRequest resolved) {
        try {
            CompletableFuture<HttpResponse<byte[]>> cf = resolved.runtime().client.sendAsync(
                    resolved.httpRequest(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (resolved.policy().readTimeoutMs() > 0) {
                try {
                    return cf.get(resolved.policy().readTimeoutMs(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    cf.cancel(true);
                    throw new VKHttpException(VKHttpErrorCode.READ_TIMEOUT,
                            "HTTP read timeout exceeded: " + resolved.policy().readTimeoutMs() + "ms", e);
                }
            }
            return cf.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP request interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof VKHttpException ex) {
                throw ex;
            }
            if (cause instanceof HttpConnectTimeoutException) {
                throw new VKHttpException(VKHttpErrorCode.CONNECT_TIMEOUT, "HTTP connect timeout", cause);
            }
            if (cause instanceof HttpTimeoutException) {
                throw new VKHttpException(VKHttpErrorCode.TOTAL_TIMEOUT, "HTTP total timeout", cause);
            }
            if (cause instanceof IOException) {
                throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP request failed", cause);
            }
            throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP request failed", cause);
        }
    }

    private HttpResponse<InputStream> executeOnceStream(ResolvedRequest resolved) {
        try {
            return resolved.runtime().client.send(resolved.httpRequest(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpConnectTimeoutException e) {
            throw new VKHttpException(VKHttpErrorCode.CONNECT_TIMEOUT, "HTTP connect timeout", e);
        } catch (HttpTimeoutException e) {
            throw new VKHttpException(VKHttpErrorCode.TOTAL_TIMEOUT, "HTTP total timeout", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP stream interrupted", e);
        } catch (IOException e) {
            throw new VKHttpException(VKHttpErrorCode.STREAM_OPEN_FAILED, "HTTP stream open failed", e);
        }
    }

    private static String readBodySafely(InputStream in, long maxBytes) {
        if (in == null) {
            return "";
        }
        long limit = Math.max(1024, maxBytes);
        try (InputStream input = in) {
            byte[] all = input.readNBytes((int) Math.min(Integer.MAX_VALUE, limit));
            return new String(all, StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            return "";
        }
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen)) + "...";
    }

    private boolean shouldRetryByStatus(ResolvedRequest resolved, int status, long retryIndex) {
        if (retryIndex >= resolved.policy().maxRetries()) {
            return false;
        }
        if (!resolved.policy().retryOnStatuses().contains(status)) {
            return false;
        }
        return isRetryMethodAllowed(resolved);
    }

    private boolean shouldRetryByError(ResolvedRequest resolved, VKHttpException e, long retryIndex) {
        if (retryIndex >= resolved.policy().maxRetries()) {
            return false;
        }
        if (!isRetryMethodAllowed(resolved)) {
            return false;
        }

        VKHttpErrorCode code = e.getCode();
        if (code == VKHttpErrorCode.NETWORK_ERROR) {
            return resolved.policy().retryOnNetworkError();
        }
        if (code == VKHttpErrorCode.CONNECT_TIMEOUT
                || code == VKHttpErrorCode.READ_TIMEOUT
                || code == VKHttpErrorCode.TOTAL_TIMEOUT
                || code == VKHttpErrorCode.TIMEOUT) {
            return resolved.policy().retryOnTimeout();
        }
        return false;
    }

    private boolean isRetryMethodAllowed(ResolvedRequest resolved) {
        if (!resolved.policy().retryMethods().contains(resolved.method())) {
            return false;
        }
        if (SAFE_METHODS.contains(resolved.method())) {
            return true;
        }
        if (!resolved.policy().requireIdempotencyKeyForUnsafeRetry()) {
            return true;
        }
        String key = resolved.policy().idempotencyKeyHeader();
        if (key == null || key.isBlank()) {
            return false;
        }
        String value = resolved.headers().get(key);
        return value != null && !value.isBlank();
    }

    private long computeRetryDelayMs(ResolvedRequest resolved, long retryIndex, String retryAfterHeader) {
        if (resolved.policy().respectRetryAfter() && retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            Long fromHeader = parseRetryAfterMs(retryAfterHeader);
            if (fromHeader != null && fromHeader > 0) {
                return Math.min(fromHeader, resolved.policy().maxRetryDelayMs());
            }
        }

        long wait = resolved.policy().retryBackoffBaseMs() << Math.min(20, retryIndex);
        wait = Math.min(wait, resolved.policy().retryBackoffMaxMs());
        if (resolved.policy().retryJitterEnabled()) {
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, wait / 3));
            wait += jitter;
        }
        return Math.min(wait, resolved.policy().maxRetryDelayMs());
    }

    private static Long parseRetryAfterMs(String headerValue) {
        String v = headerValue.trim();
        try {
            long seconds = Long.parseLong(v);
            return Math.max(0, seconds) * 1000;
        } catch (Exception ignore) {
        }
        try {
            ZonedDateTime dt = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
            long diff = dt.toInstant().toEpochMilli() - System.currentTimeMillis();
            return Math.max(0, diff);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static void sleepRetry(long waitMs) {
        if (waitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "Retry sleep interrupted", e);
        }
    }

    private static boolean shouldRecordCircuitFailure(VKHttpException e) {
        VKHttpErrorCode code = e.getCode();
        return code != VKHttpErrorCode.HTTP_STATUS
                && code != VKHttpErrorCode.RATE_LIMITED
                && code != VKHttpErrorCode.BULKHEAD_REJECTED
                && code != VKHttpErrorCode.CIRCUIT_OPEN;
    }

    private void consumeSse(VKHttpResponseMeta meta,
                            ResolvedRequest resolved,
                            VKHttpSseListener listener,
                            StreamSessionImpl session) {
        Throwable failure = null;
        boolean endedByDone = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(session.input(), StandardCharsets.UTF_8))) {
            safeInvoke(() -> listener.onOpen(meta));
            CompletableFuture<Void> watchdog = startIdleWatchdog(resolved.policy().streamIdleTimeoutMs(), session);
            SseAccumulator event = new SseAccumulator();
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                session.touch();
                if (line.isEmpty()) {
                    if (!event.isEmpty()) {
                        VKHttpSseEvent out = event.toEvent();
                        validateSseEventSize(out, resolved.policy().sseMaxEventBytes());
                        if ("[DONE]".equals(out.getData())) {
                            if (resolved.policy().sseEmitDoneEvent()) {
                                safeInvoke(() -> listener.onEvent(out));
                            }
                            endedByDone = true;
                            break;
                        }
                        safeInvoke(() -> listener.onEvent(out));
                        if (config.isMetricsEnabled()) {
                            metrics.recordSseEvent();
                        }
                        event.reset();
                    }
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                event.appendLine(line);
            }
            if (!endedByDone && !event.isEmpty() && !session.isCancelled()) {
                VKHttpSseEvent out = event.toEvent();
                validateSseEventSize(out, resolved.policy().sseMaxEventBytes());
                if (!"[DONE]".equals(out.getData()) || resolved.policy().sseEmitDoneEvent()) {
                    safeInvoke(() -> listener.onEvent(out));
                    if (config.isMetricsEnabled()) {
                        metrics.recordSseEvent();
                    }
                }
            }
            if (watchdog != null) {
                watchdog.cancel(true);
            }
        } catch (Throwable e) {
            if (!session.isCancelled()) {
                failure = e;
            }
        }

        if (session.idleTimeoutTriggered()) {
            failure = new VKHttpException(VKHttpErrorCode.STREAM_IDLE_TIMEOUT, "HTTP stream idle timeout");
        }
        if (failure != null) {
            VKHttpException streamError = asStreamError(failure);
            safeNotify(() -> listener.onError(streamError));
            if (config.isMetricsEnabled()) {
                metrics.recordStreamError();
            }
            session.fail(streamError, config.isMetricsEnabled());
            return;
        }
        safeNotify(listener::onComplete);
        if (config.isMetricsEnabled()) {
            metrics.recordStreamClose();
        }
        session.complete(config.isMetricsEnabled());
    }

    private void consumeChunks(VKHttpResponseMeta meta, long idleTimeoutMs, VKHttpChunkListener listener, StreamSessionImpl session) {
        Throwable failure = null;
        try (InputStream in = session.input()) {
            safeInvoke(() -> listener.onOpen(meta));
            CompletableFuture<Void> watchdog = startIdleWatchdog(idleTimeoutMs, session);
            byte[] buf = new byte[2048];
            int n;
            while (!session.isCancelled() && (n = in.read(buf)) >= 0) {
                session.touch();
                if (n == 0) {
                    continue;
                }
                byte[] chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
                safeInvoke(() -> listener.onChunk(chunk));
            }
            if (watchdog != null) {
                watchdog.cancel(true);
            }
        } catch (Throwable e) {
            if (!session.isCancelled()) {
                failure = e;
            }
        }
        if (session.idleTimeoutTriggered()) {
            failure = new VKHttpException(VKHttpErrorCode.STREAM_IDLE_TIMEOUT, "HTTP stream idle timeout");
        }
        if (failure != null) {
            VKHttpException streamError = asStreamError(failure);
            safeNotify(() -> listener.onError(streamError));
            if (config.isMetricsEnabled()) {
                metrics.recordStreamError();
            }
            session.fail(streamError, config.isMetricsEnabled());
            return;
        }
        safeNotify(listener::onComplete);
        if (config.isMetricsEnabled()) {
            metrics.recordStreamClose();
        }
        session.complete(config.isMetricsEnabled());
    }

    private CompletableFuture<Void> startIdleWatchdog(long idleTimeoutMs, StreamSessionImpl session) {
        if (idleTimeoutMs <= 0) {
            return null;
        }
        ExecutorService executor = streamExecutor;
        if (executor == null) {
            return null;
        }
        return CompletableFuture.runAsync(() -> {
            long intervalMs = Math.min(500, Math.max(50, idleTimeoutMs / 4));
            while (session.isOpen() && !session.isCancelled()) {
                long idle = System.currentTimeMillis() - session.lastActiveAt();
                if (idle > idleTimeoutMs) {
                    session.markIdleTimeout();
                    session.cancel();
                    return;
                }
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, executor);
    }

    private static void validateSseEventSize(VKHttpSseEvent event, int maxBytes) {
        if (event == null || event.getData() == null) {
            return;
        }
        int len = event.getData().getBytes(StandardCharsets.UTF_8).length;
        if (len > maxBytes) {
            throw new VKHttpException(VKHttpErrorCode.SSE_PARSE_ERROR,
                    "SSE event exceeds max size: " + len + " > " + maxBytes);
        }
    }

    private static void safeInvoke(Runnable action) {
        try {
            action.run();
        } catch (VKHttpException e) {
            throw e;
        } catch (Throwable e) {
            throw new VKHttpException(VKHttpErrorCode.STREAM_CONSUMER_BACKPRESSURE, "HTTP stream listener failed", e);
        }
    }

    private static void safeNotify(Runnable action) {
        try {
            action.run();
        } catch (Throwable ignore) {
        }
    }

    private static VKHttpException asStreamError(Throwable throwable) {
        if (throwable instanceof VKHttpException e) {
            return e;
        }
        if (throwable instanceof IOException) {
            return new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP stream read failed", throwable);
        }
        return new VKHttpException(VKHttpErrorCode.STREAM_CLOSED, "HTTP stream failed", throwable);
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignore) {
        }
    }

    private void logCall(ResolvedRequest resolved, int status, long costMs, long retryCount) {
        if (!config.isLogEnabled()) {
            return;
        }
        try {
            Vostok.Log.info("Vostok.Http {} {} status={} costMs={} retries={} client={}",
                    resolved.method(),
                    resolved.url(),
                    status,
                    costMs,
                    retryCount,
                    resolved.clientName() == null ? "-" : resolved.clientName());
        } catch (Throwable ignore) {
        }
    }

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (!initialized) {
                config = new VKHttpConfig();
                initialized = true;
                clearClientRuntimes();
                refreshStreamExecutor();
            }
        }
    }

    private ResolvedRequest resolveRequest(VKHttpRequest request, boolean streamMode) {
        String clientName = request.getClientName();
        if ((clientName == null || clientName.isBlank()) && clientContext.get() != null) {
            clientName = clientContext.get();
        }
        VKHttpClientConfig clientCfg = null;
        if (clientName != null && !clientName.isBlank()) {
            clientCfg = clients.get(clientName.trim());
            if (clientCfg == null) {
                throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR, "Http client not found: " + clientName);
            }
        }

        String method = request.getMethod() == null ? "GET" : request.getMethod().trim().toUpperCase();
        String raw = request.getUrlOrPath();
        if (raw == null || raw.isBlank()) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "url/path is blank");
        }

        String withPath = applyPathParams(raw, request.getPathParams());
        Map<String, List<String>> query = new LinkedHashMap<>(request.getQueryParams());
        Map<String, String> headers = new LinkedHashMap<>(config.getDefaultHeaders());

        if (clientCfg != null) {
            headers.putAll(clientCfg.getDefaultHeaders());
            if (clientCfg.getAuth() != null) {
                clientCfg.getAuth().apply(headers, query);
            }
        }
        if (request.getAuth() != null) {
            request.getAuth().apply(headers, query);
        }
        headers.putAll(request.getHeaders());

        boolean followRedirects = resolveBool(null, clientCfg == null ? null : clientCfg.getFollowRedirects(), config.isFollowRedirects());
        long connectTimeoutMs = resolvePositive(null, clientCfg == null ? -1 : clientCfg.getConnectTimeoutMs(), config.getConnectTimeoutMs());
        long totalTimeoutMs = resolvePositive(request.getTotalTimeoutMs(), clientCfg == null ? -1 : clientCfg.getTotalTimeoutMs(), config.getTotalTimeoutMs());
        long readTimeoutMs = resolvePositive(request.getReadTimeoutMs(), clientCfg == null ? -1 : clientCfg.getReadTimeoutMs(), config.getReadTimeoutMs());

        int maxRetries = resolveRetries(request.getMaxRetries(), clientCfg == null ? -1 : clientCfg.getMaxRetries(), config.getMaxRetries());
        long maxRetryDelayMs = resolvePositive(null, clientCfg == null ? -1 : clientCfg.getMaxRetryDelayMs(), config.getMaxRetryDelayMs());

        boolean retryOnNetwork = resolveBool(request.getRetryOnNetworkError(), clientCfg == null ? null : clientCfg.getRetryOnNetworkError(), config.isRetryOnNetworkError());
        boolean retryOnTimeout = resolveBool(request.getRetryOnTimeout(), clientCfg == null ? null : clientCfg.getRetryOnTimeout(), config.isRetryOnTimeout());
        boolean respectRetryAfter = resolveBool(null, clientCfg == null ? null : clientCfg.getRespectRetryAfter(), config.isRespectRetryAfter());
        boolean requireIdempotency = resolveBool(null,
                clientCfg == null ? null : clientCfg.getRequireIdempotencyKeyForUnsafeRetry(),
                config.isRequireIdempotencyKeyForUnsafeRetry());

        String idempotencyKeyHeader = clientCfg != null && clientCfg.getIdempotencyKeyHeader() != null && !clientCfg.getIdempotencyKeyHeader().isBlank()
                ? clientCfg.getIdempotencyKeyHeader().trim()
                : config.getIdempotencyKeyHeader();

        boolean failOnNon2xx = resolveBool(request.getFailOnNon2xx(), clientCfg == null ? null : clientCfg.getFailOnNon2xx(), config.isFailOnNon2xx());
        long maxResponseBodyBytes = resolvePositive(request.getMaxResponseBodyBytes(), clientCfg == null ? -1 : clientCfg.getMaxResponseBodyBytes(), config.getMaxResponseBodyBytes());

        Set<Integer> retryStatuses = request.getRetryOnStatuses().isEmpty()
                ? (clientCfg == null || clientCfg.getRetryOnStatuses().isEmpty() ? config.getRetryOnStatuses() : clientCfg.getRetryOnStatuses())
                : request.getRetryOnStatuses();

        Set<String> retryMethods = request.getRetryMethods().isEmpty()
                ? (clientCfg == null || clientCfg.getRetryMethods().isEmpty() ? config.getRetryMethods() : clientCfg.getRetryMethods())
                : request.getRetryMethods();

        String targetUrl;
        String selectedClientName = clientName == null || clientName.isBlank() ? DEFAULT_CLIENT_KEY : clientName.trim();
        if (isAbsoluteUrl(withPath)) {
            targetUrl = appendQuery(withPath, query);
        } else {
            String baseUrl = clientCfg == null ? null : clientCfg.getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR,
                        "Relative path requires named client with baseUrl");
            }
            targetUrl = appendQuery(joinBaseAndPath(baseUrl, withPath), query);
        }

        if (request.getContentType() != null && !request.getContentType().isBlank()) {
            headers.putIfAbsent("Content-Type", request.getContentType());
        }
        String userAgent = clientCfg != null && clientCfg.getUserAgent() != null && !clientCfg.getUserAgent().isBlank()
                ? clientCfg.getUserAgent().trim()
                : config.getUserAgent();
        if (userAgent != null && !userAgent.isBlank()) {
            headers.putIfAbsent("User-Agent", userAgent);
        }

        String runtimeKey = buildRuntimeKey(selectedClientName, connectTimeoutMs, followRedirects, clientCfg);
        ClientRuntimeEntry runtime = getOrCreateClientRuntime(runtimeKey, connectTimeoutMs, followRedirects, clientCfg);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl));
        long streamTimeoutMs = resolveLong(null, clientCfg == null ? null : clientCfg.getStreamTotalTimeoutMs(), config.getStreamTotalTimeoutMs());
        long effectiveTimeoutMs = streamMode
                ? Math.max(0, streamTimeoutMs)
                : totalTimeoutMs;
        if (effectiveTimeoutMs > 0) {
            reqBuilder.timeout(Duration.ofMillis(Math.max(1, effectiveTimeoutMs)));
        }

        byte[] body = request.getBody();
        if (body.length == 0 && ("GET".equals(method) || "HEAD".equals(method))) {
            reqBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }

        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                reqBuilder.header(e.getKey(), e.getValue());
            }
        }

        boolean rateLimitEnabled = resolveInt(null, clientCfg == null ? null : clientCfg.getRateLimitQps(), config.getRateLimitQps()) > 0;
        int rateLimitQps = resolveInt(null, clientCfg == null ? null : clientCfg.getRateLimitQps(), config.getRateLimitQps());
        int rateLimitBurst = resolveInt(null, clientCfg == null ? null : clientCfg.getRateLimitBurst(), config.getRateLimitBurst());
        if (rateLimitBurst <= 0) {
            rateLimitBurst = rateLimitQps;
        }

        boolean circuitEnabled = resolveBool(null, clientCfg == null ? null : clientCfg.getCircuitEnabled(), config.isCircuitEnabled());
        int circuitWindowSize = resolveInt(null, clientCfg == null ? null : clientCfg.getCircuitWindowSize(), config.getCircuitWindowSize());
        int circuitMinCalls = resolveInt(null, clientCfg == null ? null : clientCfg.getCircuitMinCalls(), config.getCircuitMinCalls());
        int circuitFailureRateThreshold = resolveInt(null, clientCfg == null ? null : clientCfg.getCircuitFailureRateThreshold(), config.getCircuitFailureRateThreshold());
        long circuitOpenWaitMs = resolveLong(null, clientCfg == null ? null : clientCfg.getCircuitOpenWaitMs(), config.getCircuitOpenWaitMs());
        int circuitHalfOpenMaxCalls = resolveInt(null, clientCfg == null ? null : clientCfg.getCircuitHalfOpenMaxCalls(), config.getCircuitHalfOpenMaxCalls());
        Set<Integer> circuitRecordStatuses = (clientCfg == null || clientCfg.getCircuitRecordStatuses().isEmpty())
                ? config.getCircuitRecordStatuses()
                : clientCfg.getCircuitRecordStatuses();

        boolean bulkheadEnabled = resolveBool(null, clientCfg == null ? null : clientCfg.getBulkheadEnabled(), config.isBulkheadEnabled());
        int bulkheadMaxConcurrent = resolveInt(null, clientCfg == null ? null : clientCfg.getBulkheadMaxConcurrent(), config.getBulkheadMaxConcurrent());
        int bulkheadQueueSize = resolveInt(null, clientCfg == null ? null : clientCfg.getBulkheadQueueSize(), config.getBulkheadQueueSize());
        long bulkheadAcquireTimeoutMs = resolveLong(null, clientCfg == null ? null : clientCfg.getBulkheadAcquireTimeoutMs(), config.getBulkheadAcquireTimeoutMs());
        boolean streamEnabled = resolveBool(null, clientCfg == null ? null : clientCfg.getStreamEnabled(), config.isStreamEnabled());
        long streamIdleTimeoutMs = resolveLong(null, clientCfg == null ? null : clientCfg.getStreamIdleTimeoutMs(), config.getStreamIdleTimeoutMs());
        long streamTotalTimeoutMs = resolveLong(null, clientCfg == null ? null : clientCfg.getStreamTotalTimeoutMs(), config.getStreamTotalTimeoutMs());
        int sseMaxEventBytes = resolveInt(null, clientCfg == null ? null : clientCfg.getSseMaxEventBytes(), config.getSseMaxEventBytes());
        boolean sseEmitDoneEvent = resolveBool(null, clientCfg == null ? null : clientCfg.getSseEmitDoneEvent(), config.isSseEmitDoneEvent());
        int streamQueueCapacity = resolveInt(null, clientCfg == null ? null : clientCfg.getStreamQueueCapacity(), config.getStreamQueueCapacity());

        ResolvedPolicy policy = new ResolvedPolicy(
                maxRetries,
                new LinkedHashSet<>(retryStatuses),
                normalizeMethods(retryMethods),
                retryOnNetwork,
                retryOnTimeout,
                respectRetryAfter,
                config.getRetryBackoffBaseMs(),
                config.getRetryBackoffMaxMs(),
                config.isRetryJitterEnabled(),
                maxRetryDelayMs,
                failOnNon2xx,
                maxResponseBodyBytes,
                readTimeoutMs,
                requireIdempotency,
                idempotencyKeyHeader,
                rateLimitEnabled,
                rateLimitQps,
                rateLimitBurst,
                circuitEnabled,
                circuitWindowSize,
                circuitMinCalls,
                circuitFailureRateThreshold,
                circuitOpenWaitMs,
                circuitHalfOpenMaxCalls,
                new LinkedHashSet<>(circuitRecordStatuses),
                bulkheadEnabled,
                bulkheadMaxConcurrent,
                bulkheadQueueSize,
                bulkheadAcquireTimeoutMs,
                streamEnabled,
                streamIdleTimeoutMs,
                streamTotalTimeoutMs,
                sseMaxEventBytes,
                sseEmitDoneEvent,
                streamQueueCapacity
        );

        runtime.lastUsedAt = System.currentTimeMillis();
        ResilienceRuntime resilience = getOrCreateResilienceRuntime(runtimeKey, policy);

        return new ResolvedRequest(
                selectedClientName,
                method,
                targetUrl,
                headers,
                runtime,
                resilience,
                reqBuilder.build(),
                policy
        );
    }

    private ClientRuntimeEntry getOrCreateClientRuntime(String runtimeKey,
                                                        long connectTimeoutMs,
                                                        boolean followRedirects,
                                                        VKHttpClientConfig clientCfg) {
        ClientRuntimeEntry existing = clientRuntimeCache.get(runtimeKey);
        if (existing != null) {
            existing.lastUsedAt = System.currentTimeMillis();
            return existing;
        }

        synchronized (LOCK) {
            ClientRuntimeEntry again = clientRuntimeCache.get(runtimeKey);
            if (again != null) {
                again.lastUsedAt = System.currentTimeMillis();
                return again;
            }

            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMs)))
                    .followRedirects(followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);

            SSLContext sslContext = resolveSslContext(clientCfg);
            if (sslContext != null) {
                builder.sslContext(sslContext);
            }

            ClientRuntimeEntry created = new ClientRuntimeEntry(builder.build(), System.currentTimeMillis());
            clientRuntimeCache.put(runtimeKey, created);
            clientBuilds.incrementAndGet();
            return created;
        }
    }

    private ResilienceRuntime getOrCreateResilienceRuntime(String runtimeKey, ResolvedPolicy policy) {
        String key = runtimeKey + "|rs:" + policy.resilienceKey();
        ResilienceRuntime existing = resilienceCache.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (LOCK) {
            ResilienceRuntime again = resilienceCache.get(key);
            if (again != null) {
                return again;
            }
            ResilienceRuntime created = new ResilienceRuntime(
                    policy.rateLimitEnabled() ? new TokenBucketLimiter(policy.rateLimitQps(), policy.rateLimitBurst()) : null,
                    policy.bulkheadEnabled() ? new Bulkhead(policy.bulkheadMaxConcurrent(), policy.bulkheadQueueSize(), policy.bulkheadAcquireTimeoutMs()) : null,
                    policy.circuitEnabled() ? new CircuitBreaker(policy.circuitWindowSize(), policy.circuitMinCalls(),
                            policy.circuitFailureRateThreshold(), policy.circuitOpenWaitMs(), policy.circuitHalfOpenMaxCalls()) : null
            );
            resilienceCache.put(key, created);
            return created;
        }
    }

    private String buildRuntimeKey(String clientName, long connectTimeoutMs, boolean followRedirects, VKHttpClientConfig clientCfg) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(clientName).append('|')
                .append(connectTimeoutMs).append('|')
                .append(followRedirects).append('|');

        if (clientCfg == null) {
            sb.append("no-ssl");
            return sb.toString();
        }

        if (clientCfg.getSslContext() != null) {
            sb.append("ssl-context:").append(System.identityHashCode(clientCfg.getSslContext()));
            return sb.toString();
        }

        sb.append("ts:")
                .append(nullToEmpty(clientCfg.getTrustStorePath())).append('|')
                .append(nullToEmpty(clientCfg.getTrustStoreType())).append('|')
                .append("ks:")
                .append(nullToEmpty(clientCfg.getKeyStorePath())).append('|')
                .append(nullToEmpty(clientCfg.getKeyStoreType()));
        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private SSLContext resolveSslContext(VKHttpClientConfig clientCfg) {
        if (clientCfg == null) {
            return null;
        }
        if (clientCfg.getSslContext() != null) {
            return clientCfg.getSslContext();
        }
        boolean hasTrust = clientCfg.getTrustStorePath() != null && !clientCfg.getTrustStorePath().isBlank();
        boolean hasKey = clientCfg.getKeyStorePath() != null && !clientCfg.getKeyStorePath().isBlank();
        if (!hasTrust && !hasKey) {
            return null;
        }
        try {
            KeyManagerFactory kmf = null;
            if (hasKey) {
                KeyStore keyStore = KeyStore.getInstance(clientCfg.getKeyStoreType());
                Path keyPath = Path.of(clientCfg.getKeyStorePath());
                if (!Files.exists(keyPath)) {
                    throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR, "KeyStore file not found: " + keyPath);
                }
                try (InputStream in = Files.newInputStream(keyPath)) {
                    char[] pwd = asChars(clientCfg.getKeyStorePassword());
                    keyStore.load(in, pwd);
                    kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    char[] keyPwd = asChars(
                            clientCfg.getKeyStoreKeyPassword() == null
                                    ? clientCfg.getKeyStorePassword()
                                    : clientCfg.getKeyStoreKeyPassword()
                    );
                    kmf.init(keyStore, keyPwd);
                }
            }

            TrustManagerFactory tmf = null;
            if (hasTrust) {
                KeyStore trustStore = KeyStore.getInstance(clientCfg.getTrustStoreType());
                Path trustPath = Path.of(clientCfg.getTrustStorePath());
                if (!Files.exists(trustPath)) {
                    throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR, "TrustStore file not found: " + trustPath);
                }
                try (InputStream in = Files.newInputStream(trustPath)) {
                    trustStore.load(in, asChars(clientCfg.getTrustStorePassword()));
                    tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                }
            }

            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(
                    kmf == null ? null : kmf.getKeyManagers(),
                    tmf == null ? null : tmf.getTrustManagers(),
                    null
            );
            return ssl;
        } catch (VKHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new VKHttpException(VKHttpErrorCode.CONFIG_ERROR, "Failed to build SSLContext from client config", e);
        }
    }

    private static char[] asChars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private void clearClientRuntimes() {
        clientRuntimeCache.clear();
        resilienceCache.clear();
    }

    private void refreshStreamExecutor() {
        ExecutorService old = streamExecutor;
        int threads = Math.max(1, config.getStreamExecutorThreads());
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "vostok-http-stream-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        };
        streamExecutor = Executors.newFixedThreadPool(threads, factory);
        if (old != null) {
            old.shutdownNow();
        }
    }

    private void closeStreamExecutor() {
        ExecutorService old = streamExecutor;
        streamExecutor = null;
        if (old != null) {
            old.shutdownNow();
        }
    }

    private void maybeEvictIdleRuntimes() {
        long seq = executeSeq.incrementAndGet();
        if ((seq & 0xFF) != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttlMs = Math.max(1000, config.getClientReuseIdleEvictMs());
        for (Map.Entry<String, ClientRuntimeEntry> e : clientRuntimeCache.entrySet()) {
            if (now - e.getValue().lastUsedAt > ttlMs) {
                clientRuntimeCache.remove(e.getKey(), e.getValue());
            }
        }
    }

    private static Set<String> normalizeMethods(Set<String> methods) {
        Set<String> out = new LinkedHashSet<>();
        if (methods == null) {
            return out;
        }
        for (String m : methods) {
            if (m != null && !m.isBlank()) {
                out.add(m.trim().toUpperCase());
            }
        }
        return out;
    }

    private static boolean isAbsoluteUrl(String value) {
        String s = value.toLowerCase();
        return s.startsWith("http://") || s.startsWith("https://");
    }

    private static String joinBaseAndPath(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private static String applyPathParams(String raw, Map<String, String> pathParams) {
        String out = raw;
        for (Map.Entry<String, String> e : pathParams.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", urlEncode(e.getValue()));
        }
        return out;
    }

    private static String appendQuery(String base, Map<String, List<String>> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        char start = base.contains("?") ? '&' : '?';
        boolean first = true;
        for (Map.Entry<String, List<String>> e : queryParams.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            for (String value : e.getValue()) {
                if (first) {
                    sb.append(start);
                    first = false;
                } else {
                    sb.append('&');
                }
                sb.append(urlEncode(e.getKey()));
                sb.append('=');
                sb.append(urlEncode(value));
            }
        }
        return sb.toString();
    }

    public static String urlEncode(String value) {
        String v = value == null ? "" : value;
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static long resolvePositive(Long requestValue, long clientValue, long globalValue) {
        if (requestValue != null && requestValue > 0) {
            return requestValue;
        }
        if (clientValue > 0) {
            return clientValue;
        }
        return Math.max(0, globalValue);
    }

    private static int resolveRetries(Integer requestValue, int clientValue, int globalValue) {
        if (requestValue != null) {
            return Math.max(0, requestValue);
        }
        if (clientValue >= 0) {
            return clientValue;
        }
        return Math.max(0, globalValue);
    }

    private static boolean resolveBool(Boolean requestValue, Boolean clientValue, boolean globalValue) {
        if (requestValue != null) {
            return requestValue;
        }
        if (clientValue != null) {
            return clientValue;
        }
        return globalValue;
    }

    private static int resolveInt(Integer requestValue, Integer clientValue, int globalValue) {
        if (requestValue != null) {
            return requestValue;
        }
        if (clientValue != null) {
            return clientValue;
        }
        return globalValue;
    }

    private static long resolveLong(Long requestValue, Long clientValue, long globalValue) {
        if (requestValue != null) {
            return requestValue;
        }
        if (clientValue != null) {
            return clientValue;
        }
        return globalValue;
    }

}
