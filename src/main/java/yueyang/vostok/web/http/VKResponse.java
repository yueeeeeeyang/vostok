package yueyang.vostok.web.http;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class VKResponse {
    private int status = 200;
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body = new byte[0];

    public int status() {
        return status;
    }

    public VKResponse status(int status) {
        this.status = status;
        return this;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public VKResponse header(String name, String value) {
        if (name != null && value != null) {
            headers.put(name, value);
        }
        return this;
    }

    public byte[] body() {
        return body;
    }

    public VKResponse body(byte[] body) {
        this.body = body == null ? new byte[0] : body;
        return this;
    }

    public VKResponse text(String text) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        header("Content-Type", "text/plain; charset=utf-8");
        body(bytes);
        return this;
    }

    public VKResponse json(String json) {
        byte[] bytes = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        header("Content-Type", "application/json; charset=utf-8");
        body(bytes);
        return this;
    }
}
