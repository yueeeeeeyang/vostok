package yueyang.vostok.web.http;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class VKRequest {
    private final String method;
    private final String path;
    private final String query;
    private final String version;
    private final Map<String, String> headers;
    private final byte[] body;
    private final boolean keepAlive;
    private final InetSocketAddress remoteAddress;

    public VKRequest(String method, String path, String query, String version,
                     Map<String, String> headers, byte[] body,
                     boolean keepAlive, InetSocketAddress remoteAddress) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.version = version;
        this.headers = headers == null ? new HashMap<>() : headers;
        this.body = body == null ? new byte[0] : body;
        this.keepAlive = keepAlive;
        this.remoteAddress = remoteAddress;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String query() {
        return query;
    }

    public String version() {
        return version;
    }

    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        return headers.get(name.toLowerCase());
    }

    public byte[] body() {
        return body;
    }

    public String bodyText() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean keepAlive() {
        return keepAlive;
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }
}
