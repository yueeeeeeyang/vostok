package yueyang.vostok.http.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.VKHttpRequest;
import yueyang.vostok.http.VKHttpResponse;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
    private final AtomicLong executeSeq = new AtomicLong();
    private final AtomicLong clientBuilds = new AtomicLong();

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
        }
    }

    public void reinit(VKHttpConfig cfg) {
        synchronized (LOCK) {
            config = (cfg == null ? new VKHttpConfig() : cfg.copy());
            initialized = true;
            clearClientRuntimes();
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
        ResolvedRequest resolved = resolveRequest(request);

        int attempts = 0;
        while (true) {
            attempts++;
            long retryIndex = attempts - 1L;
            try {
                HttpResponse<byte[]> raw = executeOnce(resolved);
                byte[] body = raw.body() == null ? new byte[0] : raw.body();
                if (body.length > resolved.policy.maxResponseBodyBytes) {
                    throw new VKHttpException(VKHttpErrorCode.RESPONSE_TOO_LARGE,
                            "Response body exceeds limit: " + body.length + " > " + resolved.policy.maxResponseBodyBytes);
                }

                int status = raw.statusCode();
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

                if (resolved.policy.failOnNon2xx && !response.is2xx()) {
                    throw new VKHttpException(VKHttpErrorCode.HTTP_STATUS,
                            "HTTP call failed with status=" + response.statusCode(), response.statusCode());
                }
                return response;
            } catch (VKHttpException e) {
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
            CompletableFuture<HttpResponse<byte[]>> cf = resolved.runtime.client.sendAsync(
                    resolved.httpRequest,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (resolved.policy.readTimeoutMs > 0) {
                try {
                    return cf.get(resolved.policy.readTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    cf.cancel(true);
                    throw new VKHttpException(VKHttpErrorCode.READ_TIMEOUT,
                            "HTTP read timeout exceeded: " + resolved.policy.readTimeoutMs + "ms", e);
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

    private boolean shouldRetryByStatus(ResolvedRequest resolved, int status, long retryIndex) {
        if (retryIndex >= resolved.policy.maxRetries) {
            return false;
        }
        if (!resolved.policy.retryOnStatuses.contains(status)) {
            return false;
        }
        return isRetryMethodAllowed(resolved);
    }

    private boolean shouldRetryByError(ResolvedRequest resolved, VKHttpException e, long retryIndex) {
        if (retryIndex >= resolved.policy.maxRetries) {
            return false;
        }
        if (!isRetryMethodAllowed(resolved)) {
            return false;
        }

        VKHttpErrorCode code = e.getCode();
        if (code == VKHttpErrorCode.NETWORK_ERROR) {
            return resolved.policy.retryOnNetworkError;
        }
        if (code == VKHttpErrorCode.CONNECT_TIMEOUT
                || code == VKHttpErrorCode.READ_TIMEOUT
                || code == VKHttpErrorCode.TOTAL_TIMEOUT
                || code == VKHttpErrorCode.TIMEOUT) {
            return resolved.policy.retryOnTimeout;
        }
        return false;
    }

    private boolean isRetryMethodAllowed(ResolvedRequest resolved) {
        if (!resolved.policy.retryMethods.contains(resolved.method)) {
            return false;
        }
        if (SAFE_METHODS.contains(resolved.method)) {
            return true;
        }
        if (!resolved.policy.requireIdempotencyKeyForUnsafeRetry) {
            return true;
        }
        String key = resolved.policy.idempotencyKeyHeader;
        if (key == null || key.isBlank()) {
            return false;
        }
        String value = resolved.headers.get(key);
        return value != null && !value.isBlank();
    }

    private long computeRetryDelayMs(ResolvedRequest resolved, long retryIndex, String retryAfterHeader) {
        if (resolved.policy.respectRetryAfter && retryAfterHeader != null && !retryAfterHeader.isBlank()) {
            Long fromHeader = parseRetryAfterMs(retryAfterHeader);
            if (fromHeader != null && fromHeader > 0) {
                return Math.min(fromHeader, resolved.policy.maxRetryDelayMs);
            }
        }

        long wait = resolved.policy.retryBackoffBaseMs << Math.min(20, retryIndex);
        wait = Math.min(wait, resolved.policy.retryBackoffMaxMs);
        if (resolved.policy.retryJitterEnabled) {
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, wait / 3));
            wait += jitter;
        }
        return Math.min(wait, resolved.policy.maxRetryDelayMs);
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

    private void logCall(ResolvedRequest resolved, int status, long costMs, long retryCount) {
        if (!config.isLogEnabled()) {
            return;
        }
        try {
            Vostok.Log.info("Vostok.Http {} {} status={} costMs={} retries={} client={}",
                    resolved.method,
                    resolved.url,
                    status,
                    costMs,
                    retryCount,
                    resolved.clientName == null ? "-" : resolved.clientName);
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
            }
        }
    }

    private ResolvedRequest resolveRequest(VKHttpRequest request) {
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
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMillis(Math.max(1, totalTimeoutMs)));

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
                idempotencyKeyHeader
        );

        runtime.lastUsedAt = System.currentTimeMillis();

        return new ResolvedRequest(
                selectedClientName,
                method,
                targetUrl,
                headers,
                runtime,
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

    private record ResolvedRequest(
            String clientName,
            String method,
            String url,
            Map<String, String> headers,
            ClientRuntimeEntry runtime,
            HttpRequest httpRequest,
            ResolvedPolicy policy
    ) {
    }

    private record ResolvedPolicy(
            int maxRetries,
            Set<Integer> retryOnStatuses,
            Set<String> retryMethods,
            boolean retryOnNetworkError,
            boolean retryOnTimeout,
            boolean respectRetryAfter,
            long retryBackoffBaseMs,
            long retryBackoffMaxMs,
            boolean retryJitterEnabled,
            long maxRetryDelayMs,
            boolean failOnNon2xx,
            long maxResponseBodyBytes,
            long readTimeoutMs,
            boolean requireIdempotencyKeyForUnsafeRetry,
            String idempotencyKeyHeader
    ) {
    }

    private static final class ClientRuntimeEntry {
        private final HttpClient client;
        private volatile long lastUsedAt;

        private ClientRuntimeEntry(HttpClient client, long lastUsedAt) {
            this.client = client;
            this.lastUsedAt = lastUsedAt;
        }
    }

    private static final class HttpMetrics {
        private final AtomicLong totalCalls = new AtomicLong();
        private final AtomicLong successCalls = new AtomicLong();
        private final AtomicLong failedCalls = new AtomicLong();
        private final AtomicLong retriedCalls = new AtomicLong();
        private final AtomicLong timeoutCalls = new AtomicLong();
        private final AtomicLong networkErrorCalls = new AtomicLong();
        private final AtomicLong totalCostMs = new AtomicLong();
        private final ConcurrentHashMap<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();

        void recordRetry() {
            retriedCalls.incrementAndGet();
        }

        void recordResponse(int status, long costMs) {
            totalCalls.incrementAndGet();
            totalCostMs.addAndGet(Math.max(0, costMs));
            if (status >= 200 && status < 300) {
                successCalls.incrementAndGet();
            } else {
                failedCalls.incrementAndGet();
            }
            statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();
        }

        void recordFailure(VKHttpErrorCode code, long costMs) {
            totalCalls.incrementAndGet();
            failedCalls.incrementAndGet();
            totalCostMs.addAndGet(Math.max(0, costMs));
            if (code == VKHttpErrorCode.CONNECT_TIMEOUT
                    || code == VKHttpErrorCode.READ_TIMEOUT
                    || code == VKHttpErrorCode.TOTAL_TIMEOUT
                    || code == VKHttpErrorCode.TIMEOUT) {
                timeoutCalls.incrementAndGet();
            }
            if (code == VKHttpErrorCode.NETWORK_ERROR) {
                networkErrorCalls.incrementAndGet();
            }
        }

        VKHttpMetrics snapshot() {
            Map<Integer, Long> statuses = new LinkedHashMap<>();
            List<Integer> keys = new ArrayList<>(statusCounts.keySet());
            keys.sort(Integer::compareTo);
            for (Integer status : keys) {
                statuses.put(status, statusCounts.get(status).get());
            }
            return new VKHttpMetrics(
                    totalCalls.get(),
                    successCalls.get(),
                    failedCalls.get(),
                    retriedCalls.get(),
                    timeoutCalls.get(),
                    networkErrorCalls.get(),
                    totalCostMs.get(),
                    statuses
            );
        }

        void reset() {
            totalCalls.set(0);
            successCalls.set(0);
            failedCalls.set(0);
            retriedCalls.set(0);
            timeoutCalls.set(0);
            networkErrorCalls.set(0);
            totalCostMs.set(0);
            statusCounts.clear();
        }
    }
}
