package yueyang.vostok.http;

import yueyang.vostok.common.json.VKJson;
import yueyang.vostok.http.auth.VKApiKeyAuth;
import yueyang.vostok.http.auth.VKBasicAuth;
import yueyang.vostok.http.auth.VKBearerAuth;
import yueyang.vostok.http.auth.VKHttpAuth;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class VKHttpRequestBuilder {
    private String clientName;
    private String method = VKHttpMethod.GET.name();
    private String urlOrPath;
    private final Map<String, String> pathParams = new LinkedHashMap<>();
    private final Map<String, List<String>> queryParams = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();

    private byte[] body;
    private String contentType;
    private long timeoutMs = -1;
    private Integer maxRetries;
    private final Set<Integer> retryOnStatuses = new LinkedHashSet<>();
    private final Set<String> retryMethods = new LinkedHashSet<>();
    private Boolean retryOnNetworkError;
    private Boolean failOnNon2xx;
    private Long maxResponseBodyBytes;
    private VKHttpAuth auth;

    private final Map<String, String> formFields = new LinkedHashMap<>();
    private final List<MultipartPart> multipartParts = new ArrayList<>();

    public VKHttpRequestBuilder client(String clientName) {
        this.clientName = clientName;
        return this;
    }

    public VKHttpRequestBuilder method(String method) {
        this.method = VKHttpMethod.normalize(method);
        return this;
    }

    public VKHttpRequestBuilder get(String urlOrPath) {
        this.method = VKHttpMethod.GET.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder post(String urlOrPath) {
        this.method = VKHttpMethod.POST.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder put(String urlOrPath) {
        this.method = VKHttpMethod.PUT.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder patch(String urlOrPath) {
        this.method = VKHttpMethod.PATCH.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder delete(String urlOrPath) {
        this.method = VKHttpMethod.DELETE.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder head(String urlOrPath) {
        this.method = VKHttpMethod.HEAD.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder options(String urlOrPath) {
        this.method = VKHttpMethod.OPTIONS.name();
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder url(String urlOrPath) {
        this.urlOrPath = urlOrPath;
        return this;
    }

    public VKHttpRequestBuilder path(String name, Object value) {
        if (name != null && !name.isBlank() && value != null) {
            this.pathParams.put(name.trim(), String.valueOf(value));
        }
        return this;
    }

    public VKHttpRequestBuilder query(String name, Object value) {
        if (name == null || name.isBlank() || value == null) {
            return this;
        }
        this.queryParams.computeIfAbsent(name.trim(), k -> new ArrayList<>()).add(String.valueOf(value));
        return this;
    }

    public VKHttpRequestBuilder query(String name, List<String> values) {
        if (name == null || name.isBlank() || values == null || values.isEmpty()) {
            return this;
        }
        this.queryParams.computeIfAbsent(name.trim(), k -> new ArrayList<>()).addAll(values);
        return this;
    }

    public VKHttpRequestBuilder header(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            this.headers.put(name.trim(), value);
        }
        return this;
    }

    public VKHttpRequestBuilder headers(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return this;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            header(e.getKey(), e.getValue());
        }
        return this;
    }

    public VKHttpRequestBuilder bearer(String token) {
        this.auth = new VKBearerAuth(token);
        return this;
    }

    public VKHttpRequestBuilder basic(String username, String password) {
        this.auth = new VKBasicAuth(username, password);
        return this;
    }

    public VKHttpRequestBuilder apiKeyHeader(String name, String value) {
        this.auth = VKApiKeyAuth.header(name, value);
        return this;
    }

    public VKHttpRequestBuilder apiKeyQuery(String name, String value) {
        this.auth = VKApiKeyAuth.query(name, value);
        return this;
    }

    public VKHttpRequestBuilder auth(VKHttpAuth auth) {
        this.auth = auth;
        return this;
    }

    public VKHttpRequestBuilder bodyText(String text) {
        this.body = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        this.contentType = "text/plain; charset=UTF-8";
        this.formFields.clear();
        this.multipartParts.clear();
        return this;
    }

    public VKHttpRequestBuilder bodyJson(Object value) {
        String json = VKJson.toJson(value);
        this.body = json.getBytes(StandardCharsets.UTF_8);
        this.contentType = "application/json; charset=UTF-8";
        this.formFields.clear();
        this.multipartParts.clear();
        return this;
    }

    public VKHttpRequestBuilder bodyBytes(byte[] bytes, String contentType) {
        this.body = bytes == null ? new byte[0] : bytes.clone();
        this.contentType = contentType;
        this.formFields.clear();
        this.multipartParts.clear();
        return this;
    }

    public VKHttpRequestBuilder contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public VKHttpRequestBuilder form(String name, Object value) {
        if (name != null && !name.isBlank() && value != null) {
            formFields.put(name.trim(), String.valueOf(value));
            this.body = null;
            this.multipartParts.clear();
        }
        return this;
    }

    public VKHttpRequestBuilder form(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        for (Map.Entry<String, ?> e : values.entrySet()) {
            form(e.getKey(), e.getValue());
        }
        return this;
    }

    public VKHttpRequestBuilder multipart(String name, String value) {
        if (name != null && !name.isBlank() && value != null) {
            multipartParts.add(MultipartPart.text(name.trim(), value));
            this.body = null;
            this.formFields.clear();
        }
        return this;
    }

    public VKHttpRequestBuilder multipart(String name, String filename, String partContentType, byte[] content) {
        if (name != null && !name.isBlank()) {
            multipartParts.add(MultipartPart.binary(name.trim(), filename, partContentType, content));
            this.body = null;
            this.formFields.clear();
        }
        return this;
    }

    public VKHttpRequestBuilder timeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public VKHttpRequestBuilder retry(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
        return this;
    }

    public VKHttpRequestBuilder retryOnStatuses(Integer... statuses) {
        this.retryOnStatuses.clear();
        if (statuses != null) {
            for (Integer status : statuses) {
                if (status != null) {
                    this.retryOnStatuses.add(status);
                }
            }
        }
        return this;
    }

    public VKHttpRequestBuilder retryMethods(String... methods) {
        this.retryMethods.clear();
        if (methods != null) {
            for (String method : methods) {
                if (method != null && !method.isBlank()) {
                    this.retryMethods.add(method.trim().toUpperCase());
                }
            }
        }
        return this;
    }

    public VKHttpRequestBuilder retryOnNetworkError(boolean retryOnNetworkError) {
        this.retryOnNetworkError = retryOnNetworkError;
        return this;
    }

    public VKHttpRequestBuilder failOnNon2xx(boolean failOnNon2xx) {
        this.failOnNon2xx = failOnNon2xx;
        return this;
    }

    public VKHttpRequestBuilder maxResponseBodyBytes(long maxResponseBodyBytes) {
        this.maxResponseBodyBytes = Math.max(1024, maxResponseBodyBytes);
        return this;
    }

    public VKHttpRequest build() {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            throw new VKHttpException(VKHttpErrorCode.INVALID_ARGUMENT, "url/path is blank");
        }

        byte[] outBody = body == null ? new byte[0] : body.clone();
        String outContentType = contentType;

        if (!multipartParts.isEmpty()) {
            String boundary = "----vostok-" + UUID.randomUUID();
            outBody = buildMultipart(boundary, multipartParts);
            outContentType = "multipart/form-data; boundary=" + boundary;
        } else if (!formFields.isEmpty()) {
            outBody = buildForm(formFields).getBytes(StandardCharsets.UTF_8);
            outContentType = "application/x-www-form-urlencoded; charset=UTF-8";
        }

        return new VKHttpRequest(
                clientName,
                method,
                urlOrPath,
                pathParams,
                queryParams,
                headers,
                outBody,
                outContentType,
                timeoutMs,
                maxRetries,
                retryOnStatuses,
                retryMethods,
                retryOnNetworkError,
                failOnNon2xx,
                maxResponseBodyBytes,
                auth
        );
    }

    public VKHttpResponse execute() {
        return VostokHttp.execute(build());
    }

    public <T> T executeJson(Class<T> type) {
        return VostokHttp.executeJson(build(), type);
    }

    public CompletableFuture<VKHttpResponse> executeAsync() {
        return VostokHttp.executeAsync(build());
    }

    public <T> CompletableFuture<T> executeJsonAsync(Class<T> type) {
        return VostokHttp.executeJsonAsync(build(), type);
    }

    private static String buildForm(Map<String, String> formValues) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : formValues.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(VostokHttp.urlEncode(e.getKey()));
            sb.append('=');
            sb.append(VostokHttp.urlEncode(e.getValue()));
        }
        return sb.toString();
    }

    private static byte[] buildMultipart(String boundary, List<MultipartPart> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        for (MultipartPart p : parts) {
            out.writeBytes(("--" + boundary).getBytes(StandardCharsets.UTF_8));
            out.writeBytes(crlf);
            String disposition = "Content-Disposition: form-data; name=\"" + p.name + "\"";
            if (p.filename != null && !p.filename.isBlank()) {
                disposition += "; filename=\"" + p.filename + "\"";
            }
            out.writeBytes(disposition.getBytes(StandardCharsets.UTF_8));
            out.writeBytes(crlf);
            if (p.contentType != null && !p.contentType.isBlank()) {
                out.writeBytes(("Content-Type: " + p.contentType).getBytes(StandardCharsets.UTF_8));
                out.writeBytes(crlf);
            }
            out.writeBytes(crlf);
            out.writeBytes(p.content);
            out.writeBytes(crlf);
        }
        out.writeBytes(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(crlf);
        return out.toByteArray();
    }

    private record MultipartPart(String name, String filename, String contentType, byte[] content) {
        static MultipartPart text(String name, String value) {
            return new MultipartPart(name, null, "text/plain; charset=UTF-8", value.getBytes(StandardCharsets.UTF_8));
        }

        static MultipartPart binary(String name, String filename, String contentType, byte[] content) {
            return new MultipartPart(name, filename, contentType, content == null ? new byte[0] : content.clone());
        }
    }
}
