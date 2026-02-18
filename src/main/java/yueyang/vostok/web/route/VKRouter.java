package yueyang.vostok.web.route;

import yueyang.vostok.web.VKHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VKRouter {
    private final ConcurrentHashMap<String, RouteTable> byMethod = new ConcurrentHashMap<>();

    public void add(String method, String path, VKHandler handler) {
        String m = method == null ? "GET" : method.toUpperCase();
        String p = normalize(path);
        RouteTable table = byMethod.computeIfAbsent(m, k -> new RouteTable());
        table.add(p, handler);
    }

    public VKRouteMatch match(String method, String path) {
        String m = method == null ? "GET" : method.toUpperCase();
        String p = normalize(path);
        RouteTable table = byMethod.get(m);
        if (table == null) {
            return null;
        }
        return table.match(p);
    }

    private String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static final class RouteTable {
        private final ConcurrentHashMap<String, RouteEntry> staticRoutes = new ConcurrentHashMap<>();
        private final TrieNode root = new TrieNode();

        void add(String path, VKHandler handler) {
            if (!isDynamic(path)) {
                staticRoutes.put(path, new RouteEntry(path, handler));
                return;
            }
            String[] segments = split(path);
            synchronized (root) {
                TrieNode n = root;
                for (int i = 0; i < segments.length; i++) {
                    String seg = segments[i];
                    if (isWildcard(seg)) {
                        String name = wildcardName(seg);
                        if (n.wildcardChild == null) {
                            n.wildcardChild = new TrieNode();
                        }
                        n.wildcardChild.paramName = name;
                        n = n.wildcardChild;
                        break;
                    }
                    if (isParam(seg)) {
                        if (n.paramChild == null) {
                            n.paramChild = new TrieNode();
                        }
                        n.paramChild.paramName = paramName(seg);
                        n = n.paramChild;
                    } else {
                        n = n.literalChildren.computeIfAbsent(seg, k -> new TrieNode());
                    }
                }
                n.entry = new RouteEntry(path, handler);
            }
        }

        VKRouteMatch match(String path) {
            RouteEntry exact = staticRoutes.get(path);
            if (exact != null) {
                return new VKRouteMatch(exact.handler, Map.of(), exact.pathPattern);
            }

            String[] segments = split(path);
            Map<String, String> params = new HashMap<>();
            RouteEntry dyn = matchTrie(root, segments, 0, params);
            if (dyn == null) {
                return null;
            }
            return new VKRouteMatch(dyn.handler, params, dyn.pathPattern);
        }

        private RouteEntry matchTrie(TrieNode node, String[] segments, int idx, Map<String, String> params) {
            if (node == null) {
                return null;
            }
            if (idx == segments.length) {
                if (node.entry != null) {
                    return node.entry;
                }
                if (node.wildcardChild != null && node.wildcardChild.entry != null) {
                    params.put(node.wildcardChild.paramName == null ? "*" : node.wildcardChild.paramName, "");
                    return node.wildcardChild.entry;
                }
                return null;
            }

            String seg = segments[idx];

            TrieNode literal = node.literalChildren.get(seg);
            if (literal != null) {
                RouteEntry h = matchTrie(literal, segments, idx + 1, params);
                if (h != null) {
                    return h;
                }
            }

            TrieNode param = node.paramChild;
            if (param != null) {
                String name = param.paramName == null ? "param" + idx : param.paramName;
                String old = params.put(name, seg);
                RouteEntry h = matchTrie(param, segments, idx + 1, params);
                if (h != null) {
                    return h;
                }
                if (old == null) {
                    params.remove(name);
                } else {
                    params.put(name, old);
                }
            }

            TrieNode wildcard = node.wildcardChild;
            if (wildcard != null && wildcard.entry != null) {
                StringBuilder rest = new StringBuilder();
                for (int i = idx; i < segments.length; i++) {
                    if (i > idx) {
                        rest.append('/');
                    }
                    rest.append(segments[i]);
                }
                params.put(wildcard.paramName == null ? "*" : wildcard.paramName, rest.toString());
                return wildcard.entry;
            }
            return null;
        }

        private boolean isDynamic(String path) {
            return path.indexOf('{') >= 0 || path.indexOf(':') >= 0;
        }

        private boolean isParam(String segment) {
            return segment.startsWith(":") || (segment.startsWith("{") && segment.endsWith("}") && !segment.startsWith("{*"));
        }

        private String paramName(String segment) {
            if (segment.startsWith(":")) {
                return segment.substring(1);
            }
            return segment.substring(1, segment.length() - 1);
        }

        private boolean isWildcard(String segment) {
            return "*".equals(segment) || "{*}".equals(segment) || segment.startsWith("{*");
        }

        private String wildcardName(String segment) {
            if (segment.startsWith("{*") && segment.endsWith("}") && segment.length() > 3) {
                return segment.substring(2, segment.length() - 1);
            }
            return "*";
        }

        private String[] split(String path) {
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return new String[0];
            }
            String p = path.charAt(0) == '/' ? path.substring(1) : path;
            if (p.isEmpty()) {
                return new String[0];
            }
            return p.split("/");
        }
    }

    private static final class TrieNode {
        final ConcurrentHashMap<String, TrieNode> literalChildren = new ConcurrentHashMap<>();
        volatile TrieNode paramChild;
        volatile TrieNode wildcardChild;
        volatile String paramName;
        volatile RouteEntry entry;
    }

    private record RouteEntry(String pathPattern, VKHandler handler) {
    }
}
