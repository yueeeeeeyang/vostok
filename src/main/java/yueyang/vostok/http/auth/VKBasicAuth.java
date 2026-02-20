package yueyang.vostok.http.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class VKBasicAuth implements VKHttpAuth {
    private final String username;
    private final String password;

    public VKBasicAuth(String username, String password) {
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, List<String>> queryParams) {
        String raw = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + encoded);
    }
}
