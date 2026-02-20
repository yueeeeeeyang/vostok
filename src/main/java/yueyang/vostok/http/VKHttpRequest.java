package yueyang.vostok.http;

import yueyang.vostok.http.auth.VKHttpAuth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VKHttpRequest {
    private final String clientName;
    private final String method;
    private final String urlOrPath;
    private final Map<String, String> pathParams;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> headers;
    private final byte[] body;
    private final String contentType;
    private final long timeoutMs;
    private final Integer maxRetries;
    private final Set<Integer> retryOnStatuses;
    private final Set<String> retryMethods;
    private final Boolean retryOnNetworkError;
    private final Boolean failOnNon2xx;
    private final Long maxResponseBodyBytes;
    private final VKHttpAuth auth;

    VKHttpRequest(String clientName,
                  String method,
                  String urlOrPath,
                  Map<String, String> pathParams,
                  Map<String, List<String>> queryParams,
                  Map<String, String> headers,
                  byte[] body,
                  String contentType,
                  long timeoutMs,
                  Integer maxRetries,
                  Set<Integer> retryOnStatuses,
                  Set<String> retryMethods,
                  Boolean retryOnNetworkError,
                  Boolean failOnNon2xx,
                  Long maxResponseBodyBytes,
                  VKHttpAuth auth) {
        this.clientName = clientName;
        this.method = method;
        this.urlOrPath = urlOrPath;
        this.pathParams = pathParams == null ? Map.of() : new LinkedHashMap<>(pathParams);
        this.queryParams = deepCopy(queryParams);
        this.headers = headers == null ? Map.of() : new LinkedHashMap<>(headers);
        this.body = body == null ? new byte[0] : body.clone();
        this.contentType = contentType;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.retryOnStatuses = retryOnStatuses == null ? Set.of() : new LinkedHashSet<>(retryOnStatuses);
        this.retryMethods = retryMethods == null ? Set.of() : new LinkedHashSet<>(retryMethods);
        this.retryOnNetworkError = retryOnNetworkError;
        this.failOnNon2xx = failOnNon2xx;
        this.maxResponseBodyBytes = maxResponseBodyBytes;
        this.auth = auth;
    }

    public String getClientName() {
        return clientName;
    }

    public String getMethod() {
        return method;
    }

    public String getUrlOrPath() {
        return urlOrPath;
    }

    public Map<String, String> getPathParams() {
        return Map.copyOf(pathParams);
    }

    public Map<String, List<String>> getQueryParams() {
        return deepCopy(queryParams);
    }

    public Map<String, String> getHeaders() {
        return Map.copyOf(headers);
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String getContentType() {
        return contentType;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public Set<Integer> getRetryOnStatuses() {
        return Set.copyOf(retryOnStatuses);
    }

    public Set<String> getRetryMethods() {
        return Set.copyOf(retryMethods);
    }

    public Boolean getRetryOnNetworkError() {
        return retryOnNetworkError;
    }

    public Boolean getFailOnNon2xx() {
        return failOnNon2xx;
    }

    public Long getMaxResponseBodyBytes() {
        return maxResponseBodyBytes;
    }

    public VKHttpAuth getAuth() {
        return auth;
    }

    private static Map<String, List<String>> deepCopy(Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : source.entrySet()) {
            List<String> values = e.getValue() == null ? List.of() : new ArrayList<>(e.getValue());
            out.put(e.getKey(), values);
        }
        return out;
    }
}
