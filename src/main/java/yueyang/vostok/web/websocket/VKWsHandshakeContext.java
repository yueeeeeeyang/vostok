package yueyang.vostok.web.websocket;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VKWsHandshakeContext {
    private final String path;
    private final String traceId;
    private final String subProtocol;
    private final InetSocketAddress remoteAddress;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;

    public VKWsHandshakeContext(String path,
                                String traceId,
                                String subProtocol,
                                InetSocketAddress remoteAddress,
                                Map<String, String> headers,
                                Map<String, String> queryParams) {
        this.path = path;
        this.traceId = traceId;
        this.subProtocol = subProtocol;
        this.remoteAddress = remoteAddress;
        this.headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        this.queryParams = queryParams == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(queryParams));
    }

    public String path() {
        return path;
    }

    public String traceId() {
        return traceId;
    }

    public String subProtocol() {
        return subProtocol;
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    public String remoteIp() {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String header(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return headers.get(name.toLowerCase());
    }

    public Map<String, String> queryParams() {
        return queryParams;
    }

    public String queryParam(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return queryParams.get(name);
    }
}
