package yueyang.vostok.http.auth;

import java.util.List;
import java.util.Map;

public final class VKBearerAuth implements VKHttpAuth {
    private final String token;

    public VKBearerAuth(String token) {
        this.token = token;
    }

    @Override
    public void apply(Map<String, String> headers, Map<String, List<String>> queryParams) {
        if (token == null || token.isBlank()) {
            return;
        }
        headers.put("Authorization", "Bearer " + token.trim());
    }
}
