package yueyang.vostok.web;

import yueyang.vostok.web.auto.VKAutoCrud;
import yueyang.vostok.web.auto.VKCrudStyle;
import yueyang.vostok.web.asset.VKStaticHandler;
import yueyang.vostok.web.core.VKBuiltinWebServerEngine;
import yueyang.vostok.web.middleware.VKCorsConfig;
import yueyang.vostok.web.middleware.VKCorsMiddleware;
import yueyang.vostok.web.middleware.VKGzipConfig;
import yueyang.vostok.web.middleware.VKGzipMiddleware;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.mvc.VKMvcConfig;
import yueyang.vostok.web.mvc.VKMvcControllerRegistry;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.spi.VKWebRuntimeSupport;
import yueyang.vostok.web.spi.VKWebServerEngine;
import yueyang.vostok.web.spi.VKWebServerFactory;
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
    private static VKWebRuntimeSupport runtime;
    private static VKWebServerEngine engine;
    private static volatile VKMvcConfig mvcConfig = VKMvcConfig.defaults();

    protected VostokWeb() {
    }

    public static VostokWeb init(int port) {
        return init(new VKWebConfig().port(port));
    }

    public static VostokWeb init(VKWebConfig config) {
        synchronized (LOCK) {
            VKWebConfig actual = config == null ? new VKWebConfig() : config;
            runtime = new VKWebRuntimeSupport(actual);
            VKWebServerFactory factory = actual.getServerFactory();
            engine = factory == null ? new VKBuiltinWebServerEngine(actual, runtime) : factory.create(actual, runtime);
            if (engine == null) {
                throw new IllegalStateException("VKWebServerFactory returned null engine");
            }
            mvcConfig = VKMvcConfig.defaults();
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
        ensureInitializedStatic();
        return runtime.wsRegistry().broadcastAllBinary(path, data);
    }

    public static int websocketBroadcastRoomBinary(String path, String room, byte[] data) {
        ensureInitializedStatic();
        return runtime.wsRegistry().broadcastRoomBinary(path, room, data);
    }

    public static int websocketBroadcastGroupBinary(String path, String group, byte[] data) {
        ensureInitializedStatic();
        return runtime.wsRegistry().broadcastGroupBinary(path, group, data);
    }

    public static int websocketBroadcastRoomAndGroupBinary(String path, String room, String group, byte[] data) {
        ensureInitializedStatic();
        return runtime.wsRegistry().broadcastRoomAndGroupBinary(path, room, group, data);
    }

    // ---- Instance API ----

    public VostokWeb get(String path, VKHandler handler) {
        ensureInitialized();
        runtime.addRoute("GET", path, handler);
        return this;
    }

    public VostokWeb post(String path, VKHandler handler) {
        ensureInitialized();
        runtime.addRoute("POST", path, handler);
        return this;
    }

    public VostokWeb route(String method, String path, VKHandler handler) {
        ensureInitialized();
        runtime.addRoute(method, path, handler);
        return this;
    }

    /** 注册单个注解控制器实例。 */
    public VostokWeb controller(Object controller) {
        ensureInitialized();
        new VKMvcControllerRegistry(runtime, mvcConfig).registerController(controller);
        return this;
    }

    /** 按包扫描并注册注解控制器。 */
    public VostokWeb controllers(String... basePackages) {
        ensureInitialized();
        new VKMvcControllerRegistry(runtime, mvcConfig).registerControllers(basePackages);
        return this;
    }

    /** 设置 MVC 注解路由配置。 */
    public VostokWeb mvcConfig(VKMvcConfig config) {
        mvcConfig = config == null ? VKMvcConfig.defaults() : config.copy();
        return this;
    }

    public VostokWeb autoCrudApi(String... basePackages) {
        ensureInitialized();
        for (var route : VKAutoCrud.build(VKCrudStyle.RESTFUL, basePackages)) {
            runtime.addRoute(route.method(), route.path(), route.handler());
        }
        return this;
    }

    public VostokWeb autoCrudApi() {
        return autoCrudApi(new String[0]);
    }

    public VostokWeb autoCrudApi(VKCrudStyle style, String... basePackages) {
        ensureInitialized();
        VKCrudStyle actual = style == null ? VKCrudStyle.RESTFUL : style;
        for (var route : VKAutoCrud.build(actual, basePackages)) {
            runtime.addRoute(route.method(), route.path(), route.handler());
        }
        return this;
    }

    public VostokWeb use(VKMiddleware middleware) {
        ensureInitialized();
        runtime.addMiddleware(middleware);
        return this;
    }

    public VostokWeb staticDir(String urlPrefix, String directory) {
        ensureInitialized();
        String prefix = urlPrefix == null ? "/" : urlPrefix;
        VKStaticHandler handler = new VKStaticHandler(prefix, java.nio.file.Path.of(directory));
        String p = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        runtime.addRoute("GET", p + "/{*path}", handler);
        runtime.addRoute("GET", p, handler);
        return this;
    }

    public VostokWeb error(VKErrorHandler handler) {
        ensureInitialized();
        runtime.setErrorHandler(handler);
        return this;
    }

    public VostokWeb rateLimit(VKRateLimitConfig config) {
        ensureInitialized();
        runtime.setGlobalRateLimit(config);
        return this;
    }

    public VostokWeb rateLimit(VKHttpMethod method, String path, VKRateLimitConfig config) {
        ensureInitialized();
        runtime.setRouteRateLimit(method, path, config);
        return this;
    }

    public VostokWeb websocket(String path, VKWebSocketHandler handler) {
        return websocket(path, null, handler);
    }

    public VostokWeb websocket(String path, VKWebSocketConfig config, VKWebSocketHandler handler) {
        ensureInitialized();
        runtime.addWebSocket(path, config, handler);
        return this;
    }

    /**
     * 注册 SSE 端点。handler 在 worker 线程中被调用，emitter 可保存到外部集合用于后续推送。
     */
    public VostokWeb sse(String path, VKSseHandler handler) {
        ensureInitialized();
        runtime.addRoute("GET", path, (req, res) -> res.sseResponse(emitter -> handler.handle(req, emitter)));
        return this;
    }

    /** 注册 Gzip 压缩中间件（使用默认配置：minBytes=256，压缩 text/ 和 application/json）。 */
    public VostokWeb gzip() {
        return use(new VKGzipMiddleware());
    }

    /** 注册 Gzip 压缩中间件（使用自定义配置）。 */
    public VostokWeb gzip(VKGzipConfig config) {
        return use(new VKGzipMiddleware(config));
    }

    /** 注册 CORS 中间件（允许所有来源的默认配置）。 */
    public VostokWeb cors() {
        return use(new VKCorsMiddleware());
    }

    /** 注册 CORS 中间件（使用自定义配置）。 */
    public VostokWeb cors(VKCorsConfig config) {
        return use(new VKCorsMiddleware(config));
    }

    /** 注册内置健康检查端点 GET /actuator/health。 */
    public VostokWeb health() {
        return health("/actuator/health");
    }

    /** 注册自定义路径的健康检查端点。 */
    public VostokWeb health(String path) {
        ensureInitialized();
        runtime.addRoute("GET", path, (req, res) -> res.status(200).json(runtime.metrics().toHealthJson()));
        return this;
    }

    /** 注册内置 Metrics 端点 GET /actuator/metrics。 */
    public VostokWeb metrics() {
        return metrics("/actuator/metrics");
    }

    /** 注册自定义路径的 Metrics 端点。 */
    public VostokWeb metrics(String path) {
        ensureInitialized();
        runtime.addRoute("GET", path, (req, res) -> res.status(200).json(runtime.metrics().toMetricsJson()));
        return this;
    }

    private void startInternal() {
        ensureInitialized();
        engine.start();
    }

    private void stopInternal() {
        if (engine != null) {
            engine.stop();
        }
    }

    private boolean startedInternal() {
        return engine != null && engine.isStarted();
    }

    private int portInternal() {
        ensureInitialized();
        return engine.port();
    }

    private int websocketBroadcastInternal(String path, String text) {
        ensureInitialized();
        return runtime.wsRegistry().broadcastAllText(path, text);
    }

    private int websocketBroadcastRoomInternal(String path, String room, String text) {
        ensureInitialized();
        return runtime.wsRegistry().broadcastRoomText(path, room, text);
    }

    private int websocketBroadcastGroupInternal(String path, String group, String text) {
        ensureInitialized();
        return runtime.wsRegistry().broadcastGroupText(path, group, text);
    }

    private int websocketBroadcastRoomAndGroupInternal(String path, String room, String group, String text) {
        ensureInitialized();
        return runtime.wsRegistry().broadcastRoomAndGroupText(path, room, group, text);
    }

    private void ensureInitialized() {
        if (runtime == null || engine == null) {
            throw new IllegalStateException("VostokWeb is not initialized. Call init() first.");
        }
    }

    private static void ensureInitializedStatic() {
        if (runtime == null || engine == null) {
            throw new IllegalStateException("VostokWeb is not initialized. Call init() first.");
        }
    }
}
