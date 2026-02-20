package yueyang.vostok.http.auth;

import java.util.List;
import java.util.Map;

public interface VKHttpAuth {
    void apply(Map<String, String> headers, Map<String, List<String>> queryParams);
}
