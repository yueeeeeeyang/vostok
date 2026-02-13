package yueyang.vostok.web.http;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class VKResponse {
    private int status = 200;
    private final Map<String, String> headers = new HashMap<>();
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
