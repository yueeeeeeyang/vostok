package yueyang.vostok.web;

import yueyang.vostok.web.core.VKWebServer;
import yueyang.vostok.web.middleware.VKCorsConfig;
import yueyang.vostok.web.middleware.VKCorsMiddleware;
import yueyang.vostok.web.middleware.VKGzipConfig;
import yueyang.vostok.web.middleware.VKGzipMiddleware;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.auto.VKAutoCrud;
import yueyang.vostok.web.auto.VKCrudStyle;
import yueyang.vostok.web.asset.VKStaticHandler;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.sse.VKSseHandler;
import yueyang.vostok.web.websocket.VKWebSocketConfig;
import yueyang.vostok.web.websocket.VKWebSocketHandler;

/**
 * Vostok Web 公共 API 入口类。
 *
 * 使用 Singleton + Facade 模式，所有操作均通过静态方法或链式实例方法完成。
 * 支持功能：HTTP 路由、中间件、WebSocket、SSE、Gzip 压缩、CORS、
 *           Health/Metrics 端点、HTTPS/TLS、静态文件、限流、自动 CRUD。
 */
public class VostokWeb {
    private static final Object LOCK = new Object();
    private static final VostokWeb INSTANCE = new VostokWeb();
    private static VKWebServer server;

    protected VostokWeb() {
    }

    public static VostokWeb init(int port) {
        return init(new VKWebConfig().port(port));
    }

    public static VostokWeb init(VKWebConfig config) {
        synchronized (LOCK) {
            server = new VKWebServer(config);
        }
        return INSTANCE;
    }

    public static void start() {
        INSTANCE.startInternal();
    }

    public static void stop() {
        INSTANCE.stopInternal();
    }

    public static boolean started() {
        return INSTANCE.startedInternal();
    }

    public static int port() {
        return INSTANCE.portInternal();
    }

    // ---- WebSocket Text Broadcast ----

    public static int websocketBroadcast(String path, String text) {
        return INSTANCE.websocketBroadcastInternal(path, text);
    }

    public static int websocketBroadcastRoom(String path, String room, String text) {
        return INSTANCE.websocketBroadcastRoomInternal(path, room, text);
    }

    public static int websocketBroadcastGroup(String path, String group, String text) {
        return INSTANCE.websocketBroadcastGroupInternal(path, group, text);
    }

    public static int websocketBroadcastRoomAndGroup(String path, String room, String group, String text) {
        return INSTANCE.websocketBroadcastRoomAndGroupInternal(path, room, group, text);
    }

    // ---- WebSocket Binary Broadcast ----

    public static int websocketBroadcastBinary(String path, byte[] data) {
        ensureServerStatic();
        return server.broadcastWebSocketBinary(path, data);
    }

    public static int websocketBroadcastRoomBinary(String path, String room, byte[] data) {
        ensureServerStatic();
        return server.broadcastWebSocketRoomBinary(path, room, data);
    }

    public static int websocketBroadcastGroupBinary(String path, String group, byte[] data) {
        ensureServerStatic();
        return server.broadcastWebSocketGroupBinary(path, group, data);
    }

    public static int websocketBroadcastRoomAndGroupBinary(String path, String room, String group, byte[] data) {
        ensureServerStatic();
        return server.broadcastWebSocketRoomAndGroupBinary(path, room, group, data);
    }

    // ---- Instance API ----

    public VostokWeb get(String path, VKHandler handler) {
        ensureServer();
        server.addRoute("GET", path, handler);
        return this;
    }

    public VostokWeb post(String path, VKHandler handler) {
        ensureServer();
        server.addRoute("POST", path, handler);
        return this;
    }

    public VostokWeb route(String method, String path, VKHandler handler) {
        ensureServer();
        server.addRoute(method, path, handler);
        return this;
    }

    public VostokWeb autoCrudApi(String... basePackages) {
        ensureServer();
        for (var route : VKAutoCrud.build(VKCrudStyle.RESTFUL, basePackages)) {
            server.addRoute(route.method(), route.path(), route.handler());
        }
        return this;
    }

    public VostokWeb autoCrudApi() {
        return autoCrudApi(new String[0]);
    }

    public VostokWeb autoCrudApi(VKCrudStyle style, String... basePackages) {
        ensureServer();
        VKCrudStyle s = style == null ? VKCrudStyle.RESTFUL : style;
        for (var route : VKAutoCrud.build(s, basePackages)) {
            server.addRoute(route.method(), route.path(), route.handler());
        }
        return this;
    }

    public VostokWeb use(VKMiddleware middleware) {
        ensureServer();
        server.addMiddleware(middleware);
        return this;
    }

    public VostokWeb staticDir(String urlPrefix, String directory) {
        ensureServer();
        String prefix = urlPrefix == null ? "/" : urlPrefix;
        VKStaticHandler handler = new VKStaticHandler(prefix, java.nio.file.Path.of(directory));
        String p = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        server.addRoute("GET", p + "/{*path}", handler);
        server.addRoute("GET", p, handler);
        return this;
    }

    public VostokWeb error(VKErrorHandler handler) {
        ensureServer();
        server.setErrorHandler(handler);
        return this;
    }

    public VostokWeb rateLimit(VKRateLimitConfig config) {
        ensureServer();
        server.setGlobalRateLimit(config);
        return this;
    }

    public VostokWeb rateLimit(VKHttpMethod method, String path, VKRateLimitConfig config) {
        ensureServer();
        server.setRouteRateLimit(method, path, config);
        return this;
    }

    public VostokWeb websocket(String path, VKWebSocketHandler handler) {
        return websocket(path, null, handler);
    }

    public VostokWeb websocket(String path, VKWebSocketConfig config, VKWebSocketHandler handler) {
        ensureServer();
        server.addWebSocket(path, config, handler);
        return this;
    }

    /**
     * 注册 SSE 端点。handler 在 worker 线程中被调用，emitter 可保存到外部集合用于后续推送。
     *
     * @param path    SSE 端点路径
     * @param handler SSE 连接建立回调
     */
    public VostokWeb sse(String path, VKSseHandler handler) {
        ensureServer();
        server.addRoute("GET", path, (req, res) -> {
            res.sseResponse(emitter -> handler.handle(req, emitter));
        });
        return this;
    }

    /**
     * 注册 Gzip 压缩中间件（使用默认配置：minBytes=256，压缩 text/ 和 application/json）。
     */
    public VostokWeb gzip() {
        return use(new VKGzipMiddleware());
    }

    /**
     * 注册 Gzip 压缩中间件（使用自定义配置）。
     */
    public VostokWeb gzip(VKGzipConfig config) {
        return use(new VKGzipMiddleware(config));
    }

    /**
     * 注册 CORS 中间件（允许所有来源的默认配置）。
     */
    public VostokWeb cors() {
        return use(new VKCorsMiddleware());
    }

    /**
     * 注册 CORS 中间件（使用自定义配置）。
     */
    public VostokWeb cors(VKCorsConfig config) {
        return use(new VKCorsMiddleware(config));
    }

    /**
     * 注册内置健康检查端点 GET /actuator/health。
     * 返回：{"status":"UP","connections":42}
     */
    public VostokWeb health() {
        return health("/actuator/health");
    }

    /**
     * 注册自定义路径的健康检查端点。
     * 返回：{"status":"UP","connections":42}
     */
    public VostokWeb health(String path) {
        ensureServer();
        server.addRoute("GET", path, (req, res) -> {
            res.status(200).json(server.metrics().toHealthJson());
        });
        return this;
    }

    /**
     * 注册内置 Metrics 端点 GET /actuator/metrics。
     * 返回：{"requests":1000,"errors":2,"activeConnections":42,"avgResponseMs":5.2}
     */
    public VostokWeb metrics() {
        return metrics("/actuator/metrics");
    }

    /**
     * 注册自定义路径的 Metrics 端点。
     */
    public VostokWeb metrics(String path) {
        ensureServer();
        server.addRoute("GET", path, (req, res) -> {
            res.status(200).json(server.metrics().toMetricsJson());
        });
        return this;
    }

    // ---- Internal Methods ----

    private void startInternal() {
        ensureServer();
        server.start();
    }

    private void stopInternal() {
        if (server != null) {
            server.stop();
        }
    }

    private boolean startedInternal() {
        return server != null && server.isStarted();
    }

    private int portInternal() {
        ensureServer();
        return server.port();
    }

    private int websocketBroadcastInternal(String path, String text) {
        ensureServer();
        return server.broadcastWebSocket(path, text);
    }

    private int websocketBroadcastRoomInternal(String path, String room, String text) {
        ensureServer();
        return server.broadcastWebSocketRoom(path, room, text);
    }

    private int websocketBroadcastGroupInternal(String path, String group, String text) {
        ensureServer();
        return server.broadcastWebSocketGroup(path, group, text);
    }

    private int websocketBroadcastRoomAndGroupInternal(String path, String room, String group, String text) {
        ensureServer();
        return server.broadcastWebSocketRoomAndGroup(path, room, group, text);
    }

    private void ensureServer() {
        if (server == null) {
            throw new IllegalStateException("VostokWeb is not initialized. Call init() first.");
        }
    }

    private static void ensureServerStatic() {
        if (server == null) {
            throw new IllegalStateException("VostokWeb is not initialized. Call init() first.");
        }
    }
}
