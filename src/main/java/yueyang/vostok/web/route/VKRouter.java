package yueyang.vostok.web.route;

import yueyang.vostok.web.VKHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VKRouter {
    private final Map<String, Map<String, VKHandler>> routes = new ConcurrentHashMap<>();

    public void add(String method, String path, VKHandler handler) {
        routes.computeIfAbsent(method.toUpperCase(), k -> new ConcurrentHashMap<>())
                .put(path, handler);
    }

    public VKHandler match(String method, String path) {
        Map<String, VKHandler> byMethod = routes.get(method.toUpperCase());
        if (byMethod == null) {
            return null;
        }
        return byMethod.get(path);
    }
}
