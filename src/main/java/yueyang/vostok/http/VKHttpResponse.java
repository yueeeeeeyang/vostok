package yueyang.vostok.http;

import yueyang.vostok.Vostok;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VKHttpResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public VKHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = headers == null ? Map.of() : new LinkedHashMap<>(headers);
        this.body = body == null ? new byte[0] : body.clone();
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean is2xx() {
        return statusCode >= 200 && statusCode < 300;
    }

    public Map<String, List<String>> headers() {
        return Map.copyOf(headers);
    }

    public byte[] bodyBytes() {
        return body.clone();
    }

    public String bodyText() {
        return bodyText(StandardCharsets.UTF_8);
    }

    public String bodyText(Charset charset) {
        Charset cs = charset == null ? StandardCharsets.UTF_8 : charset;
        return new String(body, cs);
    }

    public <T> T bodyJson(Class<T> type) {
        try {
            return Vostok.Util.fromJson(bodyText(), type);
        } catch (Exception e) {
            throw new VKHttpException(VKHttpErrorCode.SERIALIZATION_ERROR, "Failed to parse response body to JSON", e);
        }
    }
}
