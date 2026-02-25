package yueyang.vostok.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record VKHttpResponseMeta(
        int statusCode,
        Map<String, List<String>> headers
) {
    public VKHttpResponseMeta {
        headers = headers == null ? Map.of() : deepCopy(headers);
    }

    private static Map<String, List<String>> deepCopy(Map<String, List<String>> source) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : source.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? List.of() : List.copyOf(e.getValue()));
        }
        return Map.copyOf(out);
    }
}
