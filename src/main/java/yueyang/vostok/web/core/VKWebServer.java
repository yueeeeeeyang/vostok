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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VKWebServer {
    private final VKWebConfig config;
    private final VKRouter router = new VKRouter();
    private final List<VKMiddleware> middlewares = Collections.synchronizedList(new ArrayList<>());
    private volatile VKErrorHandler errorHandler;
    private volatile boolean started = false;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Thread reactorThread;
    private VKReactor reactor;
    private VKWorkerPool workers;
    private VKBufferPool bufferPool;
    private int boundPort;

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
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(config.getPort()), config.getBacklog());
            boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            serverChannel.register(selector, java.nio.channels.SelectionKey.OP_ACCEPT);

            workers = new VKWorkerPool(config.getWorkerThreads());
            bufferPool = new VKBufferPool(config.getReadBufferSize(), 1024);
            reactor = new VKReactor(this, selector, serverChannel, workers, router, middlewares, errorHandler,
                    new VKHttpParser(config.getMaxHeaderBytes(), config.getMaxBodyBytes()), bufferPool);
            reactorThread = new Thread(reactor, "vostok-web-reactor");
            reactorThread.start();
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
        if (reactor != null) {
            reactor.stop();
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
        try {
            if (selector != null) {
                selector.close();
            }
        } catch (IOException ignore) {
        }
        if (workers != null) {
            workers.shutdown();
        }
    }
}
