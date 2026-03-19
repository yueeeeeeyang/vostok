package yueyang.vostok.web.spi;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.VKHttpMethod;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.core.VKMetrics;
import yueyang.vostok.web.core.VKWebLogSupport;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.rate.VKRateLimiter;
import yueyang.vostok.web.route.VKRouter;
import yueyang.vostok.web.route.VKRouteMatch;
import yueyang.vostok.web.websocket.VKWebSocketConfig;
import yueyang.vostok.web.websocket.VKWebSocketEndpoint;
import yueyang.vostok.web.websocket.VKWebSocketHandler;
import yueyang.vostok.web.websocket.VKWebSocketSession;
import yueyang.vostok.web.websocket.VKWsRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 运行时公共语义容器。
 *
 * 这里沉淀了路由、中间件、限流、WebSocket 元数据、共享会话注册表与 metrics，
 * 使不同 Server Engine 只需处理网络层，不必重复实现框架语义。
 */
public final class VKWebRuntimeSupport {
    private final VKWebConfig config;
    private final VKRouter router = new VKRouter();
    private final List<VKMiddleware> middlewares = Collections.synchronizedList(new ArrayList<>());
    private volatile VKErrorHandler errorHandler = (err, req, res) -> res.status(500).text("Internal Server Error");
    private volatile VKRateLimiter globalRateLimiter;
    private final Map<String, VKRateLimiter> routeRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, VKWebSocketEndpoint> webSockets = new ConcurrentHashMap<>();
    private final VKWsRegistry wsRegistry = new VKWsRegistry();
    private final VKMetrics metrics = new VKMetrics();
    private final VKWebHttpDispatcher dispatcher = new VKWebHttpDispatcher(this);

    public VKWebRuntimeSupport(VKWebConfig config) {
        this.config = config == null ? new VKWebConfig() : config;
    }

    public VKWebConfig config() {
        return config;
    }

    public void addRoute(String method, String path, VKHandler handler) {
        router.add(method, path, handler);
    }

    public void addMiddleware(VKMiddleware middleware) {
        if (middleware != null) {
            middlewares.add(middleware);
        }
    }

    public void setErrorHandler(VKErrorHandler handler) {
        if (handler != null) {
            this.errorHandler = handler;
        }
    }

    public void setGlobalRateLimit(VKRateLimitConfig config) {
        if (config == null) {
            this.globalRateLimiter = null;
            return;
        }
        this.globalRateLimiter = new VKRateLimiter(config);
    }

    public void setRouteRateLimit(VKHttpMethod method, String path, VKRateLimitConfig config) {
        if (method == null || path == null || config == null) {
            return;
        }
        routeRateLimiters.put(routeLimitKey(method.name(), path), new VKRateLimiter(config));
    }

    public void addWebSocket(String path, VKWebSocketConfig wsConfig, VKWebSocketHandler handler) {
        if (path == null || handler == null) {
            return;
        }
        String normalized = normalizePath(path);
        VKWebSocketConfig actual = wsConfig == null ? defaultWebSocketConfig() : wsConfig;
        webSockets.put(normalized, new VKWebSocketEndpoint(normalized, actual, handler));
    }

    public VKRouter router() {
        return router;
    }

    /**
     * 返回共享中间件列表。
     *
     * 这里返回的是运行时持有的真实列表，目的是让内建引擎与自定义引擎看到同一套链路配置。
     */
    public List<VKMiddleware> middlewares() {
        return middlewares;
    }

    public VKErrorHandler errorHandler() {
        return errorHandler;
    }

    public VKWebSocketEndpoint findWebSocket(String path) {
        return webSockets.get(normalizePath(path));
    }

    public Map<String, VKWebSocketEndpoint> webSockets() {
        return Collections.unmodifiableMap(webSockets);
    }

    public void registerWebSocketSession(String path, VKWebSocketSession session) {
        if (session != null) {
            wsRegistry.register(normalizePath(path), session);
        }
    }

    public void unregisterWebSocketSession(String path, VKWebSocketSession session) {
        if (session != null) {
            wsRegistry.unregister(normalizePath(path), session.id());
        }
    }

    public VKWsRegistry wsRegistry() {
        return wsRegistry;
    }

    public VKMetrics metrics() {
        return metrics;
    }

    public VKWebHttpDispatcher dispatcher() {
        return dispatcher;
    }

    public VKWebDispatchResult dispatchHttp(VKRequest req) {
        return dispatcher.dispatch(req);
    }

    public VKWebDispatchResult dispatchHttp(VKRequest req, VKResponse res) {
        return dispatcher.dispatch(req, res);
    }

    public boolean tryRateLimit(VKRequest req, VKRouteMatch match, VKResponse res) {
        VKRateLimiter global = globalRateLimiter;
        if (global != null) {
            VKRateLimiter.Decision decision = global.tryAcquireDecision(req);
            if (!decision.allowed()) {
                global.applyRejected(res);
                if (config.isRateLimitLogEnabled()) {
                    VKWebLogSupport.logRateLimit(req, match, "global", decision);
                }
                return false;
            }
        }
        if (match != null && match.routePattern() != null) {
            VKRateLimiter routeLimiter = routeRateLimiters.get(routeLimitKey(req.method(), match.routePattern()));
            if (routeLimiter != null) {
                VKRateLimiter.Decision decision = routeLimiter.tryAcquireDecision(req);
                if (!decision.allowed()) {
                    routeLimiter.applyRejected(res);
                    if (config.isRateLimitLogEnabled()) {
                        VKWebLogSupport.logRateLimit(req, match, "route", decision);
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public void recordRequest(long nanos, boolean error) {
        metrics.recordRequest(nanos, error);
    }

    private String routeLimitKey(String method, String path) {
        String actualMethod = method == null ? "GET" : method.toUpperCase();
        return actualMethod + " " + normalizePath(path);
    }

    private VKWebSocketConfig defaultWebSocketConfig() {
        return new VKWebSocketConfig()
                .maxFramePayloadBytes(config.getWebsocketMaxFramePayloadBytes())
                .maxMessageBytes(config.getWebsocketMaxMessageBytes())
                .maxPendingFrames(config.getWebsocketMaxPendingFrames())
                .maxPendingBytes(config.getWebsocketMaxPendingBytes())
                .pingIntervalMs(config.getWebsocketPingIntervalMs())
                .pongTimeoutMs(config.getWebsocketPongTimeoutMs())
                .idleTimeoutMs(config.getWebsocketIdleTimeoutMs());
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String actual = path.startsWith("/") ? path : "/" + path;
        if (actual.length() > 1 && actual.endsWith("/")) {
            actual = actual.substring(0, actual.length() - 1);
        }
        return actual;
    }
}
