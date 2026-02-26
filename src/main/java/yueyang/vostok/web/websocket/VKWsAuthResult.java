package yueyang.vostok.web.websocket;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VKWsAuthResult {
    private final boolean allowed;
    private final int rejectStatus;
    private final String rejectReason;
    private final Map<String, Object> attributes;

    private VKWsAuthResult(boolean allowed, int rejectStatus, String rejectReason, Map<String, Object> attributes) {
        this.allowed = allowed;
        this.rejectStatus = rejectStatus;
        this.rejectReason = rejectReason;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static VKWsAuthResult allow() {
        return new VKWsAuthResult(true, 0, null, Map.of());
    }

    public static VKWsAuthResult allow(Map<String, Object> attributes) {
        return new VKWsAuthResult(true, 0, null, attributes);
    }

    public static VKWsAuthResult reject(int status, String reason) {
        int s = status <= 0 ? 401 : status;
        return new VKWsAuthResult(false, s, reason == null ? "Unauthorized" : reason, Map.of());
    }

    public boolean allowed() {
        return allowed;
    }

    public int rejectStatus() {
        return rejectStatus;
    }

    public String rejectReason() {
        return rejectReason;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}
