package yueyang.vostok.http.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpConfig;
import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.VKHttpRequest;
import yueyang.vostok.http.VKHttpResponse;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;

public final class VKHttpRuntime {
    private static final Object LOCK = new Object();
    private static final VKHttpRuntime INSTANCE = new VKHttpRuntime();

    private final ConcurrentHashMap<String, VKHttpClientConfig> clients = new ConcurrentHashMap<>();
    private final ThreadLocal<String> clientContext = new ThreadLocal<>();
    private final HttpMetrics metrics = new HttpMetrics();

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
        }
    }

    public void reinit(VKHttpConfig cfg) {
        synchronized (LOCK) {
            config = (cfg == null ? new VKHttpConfig() : cfg.copy());
            initialized = true;
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

        long start = System.currentTimeMillis();
        ResolvedRequest resolved = resolveRequest(request);

        int attempts = 0;
        while (true) {
            attempts++;
            long retryCount = attempts - 1L;
            try {
                HttpResponse<byte[]> raw = resolved.client.send(resolved.httpRequest(), HttpResponse.BodyHandlers.ofByteArray());
                byte[] body = raw.body() == null ? new byte[0] : raw.body();
                if (body.length > resolved.maxResponseBodyBytes) {
                    throw new VKHttpException(VKHttpErrorCode.RESPONSE_TOO_LARGE,
                            "Response body exceeds limit: " + body.length + " > " + resolved.maxResponseBodyBytes);
                }

                int status = raw.statusCode();
                boolean shouldRetry = retryCount < resolved.maxRetries
                        && resolved.retryMethods.contains(resolved.method)
                        && resolved.retryOnStatuses.contains(status);

                if (shouldRetry) {
                    if (config.isMetricsEnabled()) {
                        metrics.recordRetry();
                    }
                    sleepBackoff(resolved, retryCount);
                    continue;
                }

                VKHttpResponse response = new VKHttpResponse(status, raw.headers().map(), body);
                if (config.isMetricsEnabled()) {
                    metrics.recordResponse(response.statusCode(), System.currentTimeMillis() - start);
                }
                logCall(resolved, response.statusCode(), System.currentTimeMillis() - start, retryCount);

                if (resolved.failOnNon2xx && !response.is2xx()) {
                    throw new VKHttpException(VKHttpErrorCode.HTTP_STATUS,
                            "HTTP call failed with status=" + response.statusCode(), response.statusCode());
                }
                return response;
            } catch (VKHttpException e) {
                if (config.isMetricsEnabled()) {
                    metrics.recordFailure(e.getCode(), System.currentTimeMillis() - start);
                }
                throw e;
            } catch (HttpTimeoutException e) {
                boolean canRetry = retryCount < resolved.maxRetries
                        && resolved.retryMethods.contains(resolved.method)
                        && resolved.retryOnNetworkError;
                if (canRetry) {
                    if (config.isMetricsEnabled()) {
                        metrics.recordRetry();
                    }
                    sleepBackoff(resolved, retryCount);
                    continue;
                }
                if (config.isMetricsEnabled()) {
                    metrics.recordFailure(VKHttpErrorCode.TIMEOUT, System.currentTimeMillis() - start);
                }
                throw new VKHttpException(VKHttpErrorCode.TIMEOUT, "HTTP request timed out", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (config.isMetricsEnabled()) {
                    metrics.recordFailure(VKHttpErrorCode.NETWORK_ERROR, System.currentTimeMillis() - start);
                }
                throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP request interrupted", e);
            } catch (IOException e) {
                boolean canRetry = retryCount < resolved.maxRetries
                        && resolved.retryMethods.contains(resolved.method)
                        && resolved.retryOnNetworkError;
                if (canRetry) {
                    if (config.isMetricsEnabled()) {
                        metrics.recordRetry();
                    }
                    sleepBackoff(resolved, retryCount);
                    continue;
                }
                if (config.isMetricsEnabled()) {
                    metrics.recordFailure(VKHttpErrorCode.NETWORK_ERROR, System.currentTimeMillis() - start);
                }
                throw new VKHttpException(VKHttpErrorCode.NETWORK_ERROR, "HTTP request failed", e);
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

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (!initialized) {
                config = new VKHttpConfig();
                initialized = true;
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

        String method = request.getMethod();
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

        long connectTimeoutMs = resolvePositive(request.getTimeoutMs(), clientCfg == null ? -1 : clientCfg.getRequestTimeoutMs(), config.getRequestTimeoutMs());
        long requestTimeoutMs = resolvePositive(request.getTimeoutMs(), clientCfg == null ? -1 : clientCfg.getRequestTimeoutMs(), config.getRequestTimeoutMs());
        int maxRetries = resolveRetries(request.getMaxRetries(), clientCfg == null ? -1 : clientCfg.getMaxRetries(), config.getMaxRetries());
        boolean retryOnNetwork = resolveBool(request.getRetryOnNetworkError(), clientCfg == null ? null : clientCfg.getRetryOnNetworkError(), config.isRetryOnNetworkError());
        boolean failOnNon2xx = resolveBool(request.getFailOnNon2xx(), clientCfg == null ? null : clientCfg.getFailOnNon2xx(), config.isFailOnNon2xx());
        boolean followRedirects = resolveBool(null, clientCfg == null ? null : clientCfg.getFollowRedirects(), config.isFollowRedirects());
        long maxResponseBodyBytes = resolvePositive(request.getMaxResponseBodyBytes(), clientCfg == null ? -1 : clientCfg.getMaxResponseBodyBytes(), config.getMaxResponseBodyBytes());

        Set<Integer> retryStatuses = request.getRetryOnStatuses().isEmpty()
                ? (clientCfg == null || clientCfg.getRetryOnStatuses().isEmpty() ? config.getRetryOnStatuses() : clientCfg.getRetryOnStatuses())
                : request.getRetryOnStatuses();

        Set<String> retryMethods = request.getRetryMethods().isEmpty()
                ? (clientCfg == null || clientCfg.getRetryMethods().isEmpty() ? config.getRetryMethods() : clientCfg.getRetryMethods())
                : request.getRetryMethods();

        String targetUrl;
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

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, connectTimeoutMs)))
                .followRedirects(followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);

        SSLContext sslContext = resolveSslContext(clientCfg);
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        HttpClient finalClient = clientBuilder.build();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMillis(Math.max(1, requestTimeoutMs)));

        byte[] body = request.getBody();
        if (body.length == 0 && ("GET".equals(method) || "HEAD".equals(method))) {
            reqBuilder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
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

        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                reqBuilder.header(e.getKey(), e.getValue());
            }
        }

        return new ResolvedRequest(clientName, method, targetUrl, finalClient, reqBuilder.build(), maxRetries,
                new LinkedHashSet<>(retryStatuses), normalizeMethods(retryMethods), retryOnNetwork,
                failOnNon2xx, maxResponseBodyBytes,
                config.getRetryBackoffBaseMs(), config.getRetryBackoffMaxMs(), config.isRetryJitterEnabled());
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

    private void sleepBackoff(ResolvedRequest resolved, long retryIndex) {
        long wait = resolved.retryBackoffBaseMs << Math.min(20, retryIndex);
        wait = Math.min(wait, resolved.retryBackoffMaxMs);
        if (resolved.retryJitterEnabled) {
            long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, wait / 3));
            wait += jitter;
        }
        try {
            Thread.sleep(wait);
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
                    resolved.method, resolved.url, status, costMs, retryCount,
                    resolved.clientName == null ? "-" : resolved.clientName);
        } catch (Throwable ignore) {
            // avoid affecting business flow
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

    private static long resolvePositive(Long req, long client, long global) {
        if (req != null && req > 0) {
            return req;
        }
        if (client > 0) {
            return client;
        }
        return Math.max(1, global);
    }

    private static int resolveRetries(Integer req, int client, int global) {
        if (req != null) {
            return Math.max(0, req);
        }
        if (client >= 0) {
            return client;
        }
        return Math.max(0, global);
    }

    private static boolean resolveBool(Boolean req, Boolean client, boolean global) {
        if (req != null) {
            return req;
        }
        if (client != null) {
            return client;
        }
        return global;
    }

    private record ResolvedRequest(
            String clientName,
            String method,
            String url,
            HttpClient client,
            HttpRequest httpRequest,
            int maxRetries,
            Set<Integer> retryOnStatuses,
            Set<String> retryMethods,
            boolean retryOnNetworkError,
            boolean failOnNon2xx,
            long maxResponseBodyBytes,
            long retryBackoffBaseMs,
            long retryBackoffMaxMs,
            boolean retryJitterEnabled
    ) {
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
            if (code == VKHttpErrorCode.TIMEOUT) {
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
