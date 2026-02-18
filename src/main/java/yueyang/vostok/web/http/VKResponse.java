package yueyang.vostok.web.http;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VKResponse {
    private int status = 200;
    private final Map<String, String> headers = new HashMap<>();
    private final List<String> setCookies = new ArrayList<>();
    private byte[] body = new byte[0];
    private Path filePath;
    private long fileOffset;
    private long fileLength = -1;

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

    public List<String> setCookies() {
        return setCookies;
    }

    public VKResponse body(byte[] body) {
        this.body = body == null ? new byte[0] : body;
        this.filePath = null;
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

    public VKResponse cookie(String name, String value) {
        return cookie(new VKCookie(name, value));
    }

    public VKResponse cookie(VKCookie cookie) {
        if (cookie != null) {
            setCookies.add(cookie.encodeSetCookie());
        }
        return this;
    }

    public VKResponse deleteCookie(String name) {
        if (name == null || name.isEmpty()) {
            return this;
        }
        return cookie(new VKCookie(name, "").maxAge(0).path("/"));
    }

    public VKResponse file(Path path, long length) {
        this.filePath = path;
        this.fileOffset = 0;
        this.fileLength = length;
        this.body = new byte[0];
        return this;
    }

    public boolean isFile() {
        return filePath != null;
    }

    public Path filePath() {
        return filePath;
    }

    public long fileOffset() {
        return fileOffset;
    }

    public long fileLength() {
        return fileLength;
    }
}
