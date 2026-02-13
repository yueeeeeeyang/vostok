package yueyang.vostok.web.route;

import yueyang.vostok.web.VKHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VKRouter {
    private final Map<String, Map<String, VKHandler>> routes = new ConcurrentHashMap<>();
    private final Map<String, List<DynamicRoute>> dynamicRoutes = new ConcurrentHashMap<>();

    public void add(String method, String path, VKHandler handler) {
        String m = method.toUpperCase();
        if (isDynamic(path)) {
            dynamicRoutes.computeIfAbsent(m, k -> new ArrayList<>())
                    .add(new DynamicRoute(path, handler));
            return;
        }
        routes.computeIfAbsent(m, k -> new ConcurrentHashMap<>())
                .put(path, handler);
    }

    public VKRouteMatch match(String method, String path) {
        String m = method.toUpperCase();
        Map<String, VKHandler> byMethod = routes.get(m);
        if (byMethod == null) {
            byMethod = new ConcurrentHashMap<>();
        }
        VKHandler exact = byMethod.get(path);
        if (exact != null) {
            return new VKRouteMatch(exact, Map.of());
        }
        List<DynamicRoute> dynList = dynamicRoutes.get(m);
        if (dynList == null) {
            return null;
        }
        for (DynamicRoute route : dynList) {
            Map<String, String> params = route.match(path);
            if (params != null) {
                return new VKRouteMatch(route.handler, params);
            }
        }
        return null;
    }

    private boolean isDynamic(String path) {
        return path != null && (path.contains("{") || path.contains(":"));
    }

    private static final class DynamicRoute {
        private final String[] segments;
        private final String[] paramNames;
        private final VKHandler handler;
        private int wildcardIndex = -1;

        DynamicRoute(String path, VKHandler handler) {
            this.handler = handler;
            String[] raw = split(path);
            segments = new String[raw.length];
            paramNames = new String[raw.length];
            for (int i = 0; i < raw.length; i++) {
                String seg = raw[i];
                if (seg.startsWith(":") && seg.length() > 1) {
                    paramNames[i] = seg.substring(1);
                    segments[i] = null;
                } else if (seg.startsWith("{") && seg.endsWith("}") && seg.length() > 2) {
                    paramNames[i] = seg.substring(1, seg.length() - 1);
                    segments[i] = null;
                } else if ("*".equals(seg) || "{*}".equals(seg) || seg.startsWith("{*")) {
                    String name = "*";
                    if (seg.startsWith("{*") && seg.endsWith("}") && seg.length() > 3) {
                        name = seg.substring(2, seg.length() - 1);
                    }
                    paramNames[i] = name;
                    segments[i] = null;
                    wildcardIndex = i;
                } else {
                    segments[i] = seg;
                }
            }
        }

        Map<String, String> match(String path) {
            String[] parts = split(path);
            Map<String, String> params = new HashMap<>();
            if (segments.length == 0) {
                return parts.length == 0 ? params : null;
            }
            int i = 0;
            for (; i < segments.length; i++) {
                String literal = segments[i];
                if (wildcardIndex == i) {
                    StringBuilder rest = new StringBuilder();
                    for (int j = i; j < parts.length; j++) {
                        if (j > i) {
                            rest.append('/');
                        }
                        rest.append(parts[j]);
                    }
                    params.put(paramNames[i] == null ? "*" : paramNames[i], rest.toString());
                    return params;
                }
                if (i >= parts.length) {
                    return null;
                }
                if (literal == null) {
                    params.put(paramNames[i], parts[i]);
                } else if (!literal.equals(parts[i])) {
                    return null;
                }
            }
            return i == parts.length ? params : null;
        }

        private static String[] split(String path) {
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return new String[0];
            }
            String p = path.startsWith("/") ? path.substring(1) : path;
            if (p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            if (p.isEmpty()) {
                return new String[0];
            }
            return p.split("/");
        }
    }
}
