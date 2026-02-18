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

    public VKWebServer(VKWebConfig config) {
        this.config = config;
        this.errorHandler = (err, req, res) -> res.status(500).text("Internal Server Error");
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
            int ioThreads = Math.max(1, config.getIoThreads());
            reactors = new VKReactor[ioThreads];
            for (int i = 0; i < ioThreads; i++) {
                Selector sel = Selector.open();
                reactors[i] = new VKReactor(this, sel, workers, router, middlewares, errorHandler,
                        new VKHttpParser(config.getMaxHeaderBytes(), config.getMaxBodyBytes()), bufferPool, config);
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

    boolean tryRateLimit(VKRequest req, VKRouteMatch match, VKResponse res) {
        VKRateLimiter global = globalRateLimiter;
        if (global != null && !global.tryAcquire(req)) {
            global.applyRejected(res);
            return false;
        }
        if (match != null && match.routePattern() != null) {
            VKRateLimiter routeLimiter = routeRateLimiters.get(routeLimitKey(req.method(), match.routePattern()));
            if (routeLimiter != null && !routeLimiter.tryAcquire(req)) {
                routeLimiter.applyRejected(res);
                return false;
            }
        }
        return true;
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
