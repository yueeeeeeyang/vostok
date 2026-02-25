package yueyang.vostok.ai.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;
import yueyang.vostok.http.VKHttpClientConfig;
import yueyang.vostok.http.VKHttpRequest;
import yueyang.vostok.http.VKHttpResponse;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VKAiTransportOps {
    private VKAiTransportOps() {
    }

    static HttpResult executeHttpJson(ConcurrentHashMap<String, String> providerHttpClients,
                                      String clientName,
                                      String url,
                                      Map<String, String> headers,
                                      String body,
                                      long connectTimeoutMs,
                                      long readTimeoutMs,
                                      boolean streamMode,
                                      boolean failOnNon2xx) {
        try {
            VKHttpRequest request = buildHttpRequest(providerHttpClients,
                    clientName,
                    url,
                    headers,
                    body,
                    connectTimeoutMs,
                    readTimeoutMs,
                    streamMode,
                    failOnNon2xx);
            VKHttpResponse response = Vostok.Http.execute(request);
            return new HttpResult(response.statusCode(), response.bodyText(StandardCharsets.UTF_8));
        } catch (VKHttpException e) {
            throw mapHttpException("AI request failed", e, false);
        }
    }

    static VKHttpRequest buildHttpRequest(ConcurrentHashMap<String, String> providerHttpClients,
                                          String clientName,
                                          String url,
                                          Map<String, String> headers,
                                          String body,
                                          long connectTimeoutMs,
                                          long readTimeoutMs,
                                          boolean streamMode,
                                          boolean failOnNon2xx) {
        String httpClient = ensureProviderHttpClient(providerHttpClients, connectTimeoutMs, readTimeoutMs, streamMode);
        yueyang.vostok.http.VKHttpRequestBuilder builder = Vostok.Http.post(url)
                .client(httpClient)
                .bodyBytes(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8")
                .totalTimeoutMs(Math.max(1L, readTimeoutMs))
                .readTimeoutMs(Math.max(1L, readTimeoutMs))
                .retry(0)
                .retryOnTimeout(false)
                .retryOnNetworkError(false)
                .failOnNon2xx(failOnNon2xx);
        if (headers != null && !headers.isEmpty()) {
            builder.headers(headers);
        }
        if (clientName != null && !clientName.isBlank()) {
            builder.header("X-Vostok-AI-Client", clientName);
        }
        return builder.build();
    }

    static String ensureProviderHttpClient(ConcurrentHashMap<String, String> providerHttpClients,
                                           long connectTimeoutMs,
                                           long readTimeoutMs,
                                           boolean streamMode) {
        long connect = Math.max(1L, connectTimeoutMs);
        long read = Math.max(1L, readTimeoutMs);
        String key = connect + "|" + read + "|" + streamMode;
        return providerHttpClients.computeIfAbsent(key, it -> {
            String client = "vk-ai-http-" + VKAiRuntimeSupportOps.shortHash(it);
            VKHttpClientConfig cfg = new VKHttpClientConfig()
                    .connectTimeoutMs(connect)
                    .readTimeoutMs(read)
                    .maxRetries(0)
                    .failOnNon2xx(false)
                    .streamEnabled(true)
                    .streamIdleTimeoutMs(read)
                    .streamTotalTimeoutMs(streamMode ? read : -1);
            Vostok.Http.registerClient(client, cfg);
            return client;
        });
    }

    static VKAiException mapHttpException(String action, VKHttpException e, boolean preserveStatusBody) {
        VKHttpErrorCode code = e.getCode();
        String prefix = action == null || action.isBlank() ? "AI request failed" : action;
        if (code == VKHttpErrorCode.HTTP_STATUS) {
            Integer status = e.getStatusCode();
            String message = prefix + " with status=" + status;
            if (preserveStatusBody && e.getMessage() != null && !e.getMessage().isBlank()) {
                message = e.getMessage();
            }
            if (status == null) {
                return new VKAiException(VKAiErrorCode.HTTP_STATUS, message);
            }
            return new VKAiException(VKAiErrorCode.HTTP_STATUS, message, status);
        }
        if (code == VKHttpErrorCode.CONNECT_TIMEOUT
                || code == VKHttpErrorCode.READ_TIMEOUT
                || code == VKHttpErrorCode.TOTAL_TIMEOUT
                || code == VKHttpErrorCode.TIMEOUT
                || code == VKHttpErrorCode.STREAM_IDLE_TIMEOUT) {
            return new VKAiException(VKAiErrorCode.TIMEOUT, prefix + ": timeout", e);
        }
        if (code == VKHttpErrorCode.NETWORK_ERROR) {
            return new VKAiException(VKAiErrorCode.NETWORK_ERROR, prefix + ": network error", e);
        }
        if (code == VKHttpErrorCode.SSE_PARSE_ERROR || code == VKHttpErrorCode.SERIALIZATION_ERROR) {
            return new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, prefix + ": stream parse error", e);
        }
        if (code == VKHttpErrorCode.INVALID_ARGUMENT || code == VKHttpErrorCode.CONFIG_ERROR) {
            return new VKAiException(VKAiErrorCode.CONFIG_ERROR, prefix + ": " + e.getMessage(), e);
        }
        return new VKAiException(VKAiErrorCode.STATE_ERROR, prefix + ": " + e.getMessage(), e);
    }

    static VKAiException mapStreamThrowable(Throwable t) {
        if (t instanceof VKAiException e) {
            return e;
        }
        if (t instanceof VKHttpException e) {
            return mapHttpException("AI stream failed", e, true);
        }
        return new VKAiException(VKAiErrorCode.STATE_ERROR, "AI stream failed", t);
    }

    record HttpResult(int statusCode, String body) {
    }
}
