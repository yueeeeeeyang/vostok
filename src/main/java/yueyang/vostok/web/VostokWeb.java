package yueyang.vostok.web;

import yueyang.vostok.web.core.VKWebServer;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.auto.VKAutoCrud;
import yueyang.vostok.web.auto.VKCrudStyle;
import yueyang.vostok.web.asset.VKStaticHandler;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.websocket.VKWebSocketConfig;
import yueyang.vostok.web.websocket.VKWebSocketHandler;

/**
 * Vostok Web entry.
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

    private void ensureServer() {
        if (server == null) {
            throw new IllegalStateException("VostokWeb is not initialized. Call init() first.");
        }
    }
}
