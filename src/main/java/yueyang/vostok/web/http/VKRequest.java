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
    private Map<String, String> params;
    private Map<String, String> queryParams;
    private String traceId;

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
        this.params = new HashMap<>();
        this.queryParams = null;
        this.traceId = null;
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

    public String traceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String param(String name) {
        if (name == null || params == null) {
            return null;
        }
        return params.get(name);
    }

    public String queryParam(String name) {
        if (name == null) {
            return null;
        }
        Map<String, String> map = ensureQueryParams();
        return map.get(name);
    }

    public Map<String, String> queryParams() {
        return ensureQueryParams();
    }

    public void setParams(Map<String, String> params) {
        this.params = params == null ? new HashMap<>() : params;
    }

    private Map<String, String> ensureQueryParams() {
        if (queryParams != null) {
            return queryParams;
        }
        Map<String, String> map = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] parts = query.split("&");
            for (String p : parts) {
                if (p == null || p.isEmpty()) {
                    continue;
                }
                int idx = p.indexOf('=');
                if (idx <= 0) {
                    map.put(decode(p), "");
                } else {
                    String k = decode(p.substring(0, idx));
                    String v = decode(p.substring(idx + 1));
                    map.put(k, v);
                }
            }
        }
        queryParams = map;
        return map;
    }

    private String decode(String s) {
        if (s == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
