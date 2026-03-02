package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.VKHttpMethod;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.rate.VKRateLimitConfig;
import yueyang.vostok.web.rate.VKRateLimiter;
import yueyang.vostok.web.route.VKRouter;
import yueyang.vostok.web.route.VKRouteMatch;
import yueyang.vostok.web.util.VKBufferPool;
import yueyang.vostok.web.http.VKHttpParser;
import yueyang.vostok.web.websocket.VKWebSocketConfig;
import yueyang.vostok.web.websocket.VKWebSocketEndpoint;
import yueyang.vostok.web.websocket.VKWebSocketHandler;
import yueyang.vostok.web.websocket.VKWebSocketSession;
import yueyang.vostok.web.websocket.VKWsRegistry;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web 服务器核心协调类，管理 Reactor 线程组、Worker 线程池、路由、中间件等。
 *
 * 集成点：
 * - VKReactor：NIO 事件循环，处理网络 I/O
 * - VKWorkerPool：业务逻辑执行线程池
 * - VKMetrics：请求指标收集
 * - VKWsRegistry：WebSocket 会话注册与广播
 */
public final class VKWebServer {
    private final VKWebConfig config;
    private final VKRouter router = new VKRouter();
    private final List<VKMiddleware> middlewares = Collections.synchronizedList(new ArrayList<>());
    private volatile VKErrorHandler errorHandler;
    private volatile boolean started = false;
    private ServerSocketChannel serverChannel;
    private Thread acceptorThread;
    private VKReactor[] reactors;
    private VKWorkerPool workers;
    private VKBufferPool bufferPool;
    private VKAccessLogger accessLogger;
    private int boundPort;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private volatile boolean accepting = false;
    private int rr = 0;
    private volatile VKRateLimiter globalRateLimiter;
    private final Map<String, VKRateLimiter> routeRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, VKWebSocketEndpoint> webSockets = new ConcurrentHashMap<>();
    private final VKWsRegistry wsRegistry = new VKWsRegistry();
    /** 运行时指标，记录请求数、错误数、响应时间等。 */
    private final VKMetrics metrics = new VKMetrics();

    public VKWebServer(VKWebConfig config) {
        this.config = config;
        this.errorHandler = (err, req, res) -> res.status(500).text("Internal Server Error");
        // 注入活跃连接数回调，确保 metrics 每次查询到实时值
        metrics.setActiveConnectionsSupplier(activeConnections::get);
    }

    public void addRoute(String method, String path, VKHandler handler) {
        router.add(method, path, handler);
    }

    public void addMiddleware(VKMiddleware middleware) {
        middlewares.add(middleware);
    }

    public void addWebSocket(String path, VKWebSocketConfig wsConfig, VKWebSocketHandler handler) {
        if (path == null || handler == null) {
            return;
        }
        VKWebSocketConfig cfg = wsConfig == null ? defaultWebSocketConfig() : wsConfig;
        webSockets.put(normalizePath(path), new VKWebSocketEndpoint(normalizePath(path), cfg, handler));
    }

    public void setErrorHandler(VKErrorHandler handler) {
        if (handler != null) {
            this.errorHandler = handler;
        }
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            VKWebLogSupport.ensureLoggersReady();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(true);
            serverChannel.bind(new InetSocketAddress(config.getPort()), config.getBacklog());
            boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

            workers = new VKWorkerPool(config.getWorkerThreads(), config.getWorkerQueueSize());
            bufferPool = new VKBufferPool(config.getReadBufferSize(), 1024);
            if (config.isAccessLogEnabled()) {
                accessLogger = new VKAccessLogger(config.getAccessLogQueueSize());
                accessLogger.start();
            }

            // 若配置了 TLS，构建 SSLContext 后传给每个 Reactor
            SSLContext sslContext = null;
            if (config.getTlsConfig() != null) {
                try {
                    sslContext = config.getTlsConfig().buildSslContext();
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to build TLS SSLContext", e);
                }
            }

            int ioThreads = Math.max(1, config.getIoThreads());
            reactors = new VKReactor[ioThreads];
            for (int i = 0; i < ioThreads; i++) {
                Selector sel = Selector.open();
                reactors[i] = new VKReactor(this, sel, workers, router, middlewares, errorHandler,
                        new VKHttpParser(config.getMaxHeaderBytes(), config.getMaxBodyBytes()),
                        bufferPool, config, sslContext);
                Thread t = new Thread(reactors[i], "vostok-web-reactor-" + i);
                t.start();
            }

            accepting = true;
            acceptorThread = new Thread(this::acceptLoop, "vostok-web-acceptor");
            acceptorThread.start();
            started = true;
        } catch (IOException e) {
            cleanup();
            throw new IllegalStateException("Failed to start web server", e);
        }
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        accepting = false;
        if (acceptorThread != null) {
            acceptorThread.interrupt();
        }
        if (reactors != null) {
            for (VKReactor r : reactors) {
                if (r != null) {
                    r.stop();
                }
            }
        }
        cleanup();
    }

    public boolean isStarted() {
        return started;
    }

    public int port() {
        return boundPort;
    }

    /** 获取运行时指标对象，供 VostokWeb 注册 health/metrics 端点使用。 */
    public VKMetrics metrics() {
        return metrics;
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
        String key = routeLimitKey(method.name(), path);
        routeRateLimiters.put(key, new VKRateLimiter(config));
    }

    VKWebSocketEndpoint findWebSocket(String path) {
        return webSockets.get(normalizePath(path));
    }

    void registerWebSocketSession(String path, VKWebSocketSession session) {
        if (session == null) {
            return;
        }
        wsRegistry.register(normalizePath(path), session);
    }

    void unregisterWebSocketSession(String path, VKWebSocketSession session) {
        if (session == null) {
            return;
        }
        wsRegistry.unregister(normalizePath(path), session.id());
    }

    VKWsRegistry wsRegistry() {
        return wsRegistry;
    }

    // ---- WebSocket Text Broadcast ----

    public int broadcastWebSocket(String path, String text) {
        return wsRegistry.broadcastAllText(normalizePath(path), text);
    }

    public int broadcastWebSocketRoom(String path, String room, String text) {
        return wsRegistry.broadcastRoomText(normalizePath(path), room, text);
    }

    public int broadcastWebSocketGroup(String path, String group, String text) {
        return wsRegistry.broadcastGroupText(normalizePath(path), group, text);
    }

    public int broadcastWebSocketRoomAndGroup(String path, String room, String group, String text) {
        return wsRegistry.broadcastRoomAndGroupText(normalizePath(path), room, group, text);
    }

    // ---- WebSocket Binary Broadcast ----

    public int broadcastWebSocketBinary(String path, byte[] data) {
        return wsRegistry.broadcastAllBinary(normalizePath(path), data);
    }

    public int broadcastWebSocketRoomBinary(String path, String room, byte[] data) {
        return wsRegistry.broadcastRoomBinary(normalizePath(path), room, data);
    }

    public int broadcastWebSocketGroupBinary(String path, String group, byte[] data) {
        return wsRegistry.broadcastGroupBinary(normalizePath(path), group, data);
    }

    public int broadcastWebSocketRoomAndGroupBinary(String path, String room, String group, byte[] data) {
        return wsRegistry.broadcastRoomAndGroupBinary(normalizePath(path), room, group, data);
    }

    boolean tryRateLimit(VKRequest req, VKRouteMatch match, VKResponse res) {
        VKRateLimiter global = globalRateLimiter;
        if (global != null) {
            VKRateLimiter.Decision d = global.tryAcquireDecision(req);
            if (!d.allowed()) {
                global.applyRejected(res);
                if (config.isRateLimitLogEnabled()) {
                    VKWebLogSupport.logRateLimit(req, match, "global", d);
                }
                return false;
            }
        }
        if (match != null && match.routePattern() != null) {
            VKRateLimiter routeLimiter = routeRateLimiters.get(routeLimitKey(req.method(), match.routePattern()));
            if (routeLimiter == null) {
                return true;
            }
            VKRateLimiter.Decision d = routeLimiter.tryAcquireDecision(req);
            if (!d.allowed()) {
                routeLimiter.applyRejected(res);
                if (config.isRateLimitLogEnabled()) {
                    VKWebLogSupport.logRateLimit(req, match, "route", d);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 记录一次请求完成的指标。
     *
     * @param nanos 请求处理耗时（纳秒）
     * @param error 是否为错误响应（5xx）
     */
    void recordRequest(long nanos, boolean error) {
        metrics.totalRequests.incrementAndGet();
        metrics.totalResponseNs.addAndGet(nanos);
        if (error) {
            metrics.totalErrors.incrementAndGet();
        }
    }

    private void cleanup() {
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
        } catch (IOException ignore) {
        }
        if (workers != null) {
            workers.shutdown();
        }
        if (accessLogger != null) {
            accessLogger.stop();
            accessLogger = null;
        }
    }

    int maxConnections() {
        return config.getMaxConnections();
    }

    int readTimeoutMs() {
        return config.getReadTimeoutMs();
    }

    int keepAliveTimeoutMs() {
        return config.getKeepAliveTimeoutMs();
    }

    boolean accessLogEnabled() {
        return config.isAccessLogEnabled();
    }

    void logAccess(String line) {
        VKAccessLogger logger = accessLogger;
        if (logger != null) {
            logger.offer(line);
        }
    }

    int incConnections() {
        return activeConnections.incrementAndGet();
    }

    int decConnections() {
        return activeConnections.decrementAndGet();
    }

    private void acceptLoop() {
        while (accepting) {
            try {
                SocketChannel ch = serverChannel.accept();
                if (ch == null) {
                    continue;
                }
                if (activeConnections.get() >= config.getMaxConnections()) {
                    try {
                        ch.close();
                    } catch (IOException ignore) {
                    }
                    continue;
                }
                ch.configureBlocking(false);
                VKReactor r = reactors[rr++ % reactors.length];
                r.register(ch);
            } catch (IOException e) {
                if (!accepting) {
                    break;
                }
            }
        }
    }

    private String routeLimitKey(String method, String path) {
        String m = method == null ? "GET" : method.toUpperCase();
        String p = normalizePath(path);
        return m + " " + p;
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
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
