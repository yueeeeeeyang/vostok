package yueyang.vostok.web.core;

import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKHttpParser;
import yueyang.vostok.web.spi.VKWebRuntimeSupport;
import yueyang.vostok.web.spi.VKWebServerEngine;
import yueyang.vostok.web.util.VKBufferPool;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内建 Web 引擎实现。
 *
 * 该类只负责 NIO 传输层生命周期与连接管理；路由、中间件、限流、WebSocket 元数据与 metrics
 * 均下沉到 VKWebRuntimeSupport 中，方便后续业务项目替换成自定义引擎实现。
 */
public final class VKBuiltinWebServerEngine implements VKWebServerEngine {
    private final VKWebConfig config;
    private final VKWebRuntimeSupport runtime;
    private final AtomicInteger activeConnections = new AtomicInteger();

    private volatile boolean started;
    private volatile boolean accepting;
    private ServerSocketChannel serverChannel;
    private Thread acceptorThread;
    private VKReactor[] reactors;
    private VKWorkerPool workers;
    private VKBufferPool bufferPool;
    private VKAccessLogger accessLogger;
    private int boundPort;
    private int rr;

    public VKBuiltinWebServerEngine(VKWebConfig config, VKWebRuntimeSupport runtime) {
        this.config = config == null ? new VKWebConfig() : config;
        this.runtime = runtime == null ? new VKWebRuntimeSupport(this.config) : runtime;
        this.runtime.metrics().setActiveConnectionsSupplier(activeConnections::get);
    }

    public VKWebRuntimeSupport runtime() {
        return runtime;
    }

    @Override
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

            SSLContext sslContext = buildSslContext();
            int ioThreads = Math.max(1, config.getIoThreads());
            reactors = new VKReactor[ioThreads];
            for (int i = 0; i < ioThreads; i++) {
                Selector selector = Selector.open();
                reactors[i] = new VKReactor(this, runtime, selector, workers,
                        new VKHttpParser(config.getMaxHeaderBytes(), config.getMaxBodyBytes()),
                        bufferPool, config, sslContext);
                Thread thread = new Thread(reactors[i], "vostok-web-reactor-" + i);
                thread.start();
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

    @Override
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
            for (VKReactor reactor : reactors) {
                if (reactor != null) {
                    reactor.stop();
                }
            }
        }
        cleanup();
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public int port() {
        return boundPort;
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

    private SSLContext buildSslContext() {
        if (config.getTlsConfig() == null) {
            return null;
        }
        try {
            return config.getTlsConfig().buildSslContext();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build TLS SSLContext", e);
        }
    }

    private void acceptLoop() {
        while (accepting) {
            try {
                SocketChannel channel = serverChannel.accept();
                if (channel == null) {
                    continue;
                }
                if (activeConnections.get() >= config.getMaxConnections()) {
                    try {
                        channel.close();
                    } catch (IOException ignore) {
                    }
                    continue;
                }
                channel.configureBlocking(false);
                VKReactor reactor = reactors[rr++ % reactors.length];
                reactor.register(channel);
            } catch (IOException e) {
                if (!accepting) {
                    break;
                }
            }
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
            workers = null;
        }
        if (accessLogger != null) {
            accessLogger.stop();
            accessLogger = null;
        }
    }
}
