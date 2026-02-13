package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.route.VKRouter;
import yueyang.vostok.web.util.VKBufferPool;
import yueyang.vostok.web.http.VKHttpParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private int boundPort;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private volatile boolean accepting = false;
    private int rr = 0;

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
}
