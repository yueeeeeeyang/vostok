package yueyang.vostok.http.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VKApiKeyAuth implements VKHttpAuth {
    public enum Location {
        HEADER,
        QUERY
    }

    private final String name;
    private final String value;
    private final Location location;

    public VKApiKeyAuth(String name, String value) {
        this(name, value, Location.HEADER);
    }

    public VKApiKeyAuth(String name, String value, Location location) {
        this.name = name;
        this.value = value;
        this.location = location == null ? Location.HEADER : location;
    }

    public static VKApiKeyAuth header(String name, String value) {
        return new VKApiKeyAuth(name, value, Location.HEADER);
    }

    public static VKApiKeyAuth query(String name, String value) {
        return new VKApiKeyAuth(name, value, Location.QUERY);
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, List<String>> queryParams) {
        if (name == null || name.isBlank() || value == null) {
            return;
        }
        if (location == Location.QUERY) {
            queryParams.computeIfAbsent(name.trim(), k -> new ArrayList<>()).add(value);
            return;
        }
        headers.put(name.trim(), value);
    }
}
