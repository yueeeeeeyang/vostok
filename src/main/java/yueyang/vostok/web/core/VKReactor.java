package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.http.VKHttpParseException;
import yueyang.vostok.web.http.VKHttpParser;
import yueyang.vostok.web.http.VKHttpWriter;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.middleware.VKChain;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.route.VKRouter;
import yueyang.vostok.web.util.VKBufferPool;
import yueyang.vostok.web.VKWebConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class VKReactor implements Runnable {
    private final VKWebServer server;
    private final Selector selector;
    private final VKWorkerPool workers;
    private final VKRouter router;
    private final List<VKMiddleware> middlewares;
    private final VKErrorHandler errorHandler;
    private final VKHttpParser parser;
    private final VKBufferPool bufferPool;
    private final VKWebConfig config;
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private final List<VKConn> connections = new ArrayList<>();
    private volatile boolean running = true;

    VKReactor(VKWebServer server,
              Selector selector,
              VKWorkerPool workers,
              VKRouter router,
              List<VKMiddleware> middlewares,
              VKErrorHandler errorHandler,
              VKHttpParser parser,
              VKBufferPool bufferPool,
              VKWebConfig config) {
        this.server = server;
        this.selector = selector;
        this.workers = workers;
        this.router = router;
        this.middlewares = middlewares;
        this.errorHandler = errorHandler;
        this.parser = parser;
        this.bufferPool = bufferPool;
        this.config = config;
    }

    @Override
    public void run() {
        while (running) {
            try {
                selector.select(1000);
                drainPending();
                var keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    try {
                        VKConn conn = (VKConn) key.attachment();
                        if (key.isReadable()) {
                            conn.onRead();
                        }
                        if (key.isWritable()) {
                            conn.onWrite();
                        }
                    } catch (java.nio.channels.CancelledKeyException e) {
                        // key cancelled during processing, ignore
                    }
                }
                sweepIdle();
            } catch (java.nio.channels.ClosedSelectorException e) {
                break;
            } catch (IOException e) {
                // ignore and continue
            }
        }
        try {
            selector.close();
        } catch (IOException ignore) {
        }
    }

    void stop() {
        running = false;
        selector.wakeup();
    }

    void register(SocketChannel channel) {
        pending.add(() -> {
            try {
                SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
                VKConn conn = new VKConn(server, channel, key, parser, bufferPool, workers, router, middlewares, errorHandler, this, config);
                key.attach(conn);
                connections.add(conn);
                server.incConnections();
            } catch (Exception e) {
                try {
                    channel.close();
                } catch (IOException ignore) {
                }
            }
        });
        selector.wakeup();
    }

    void requestWrite(VKConn conn) {
        pending.add(() -> {
            SelectionKey key = conn.key();
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        });
        selector.wakeup();
    }

    void requestClose(VKConn conn) {
        pending.add(conn::close);
        selector.wakeup();
    }

    private void drainPending() {
        Runnable task;
        while ((task = pending.poll()) != null) {
            try {
                task.run();
            } catch (Exception ignore) {
            }
        }
    }

    private void sweepIdle() {
        if (connections.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long keepAliveMs = config.getKeepAliveTimeoutMs();
        long readTimeoutMs = config.getReadTimeoutMs();
        for (int i = connections.size() - 1; i >= 0; i--) {
            VKConn c = connections.get(i);
            if (c.isClosed()) {
                connections.remove(i);
                continue;
            }
            long idle = now - c.lastActive();
            if (idle > keepAliveMs) {
                requestClose(c);
                connections.remove(i);
                continue;
            }
            if (c.waitingForBody() && (now - c.lastReadTime()) > readTimeoutMs) {
                c.respondTimeout();
                connections.remove(i);
            }
        }
    }

    static final class VKConn {
        private final VKWebServer server;
        private final SocketChannel channel;
        private final SelectionKey key;
        private final VKHttpParser parser;
        private final VKBufferPool bufferPool;
        private final VKWorkerPool workers;
        private final VKRouter router;
        private final List<VKMiddleware> middlewares;
        private final VKErrorHandler errorHandler;
        private final VKReactor reactor;
        private final ByteBuffer readBuffer;
        private byte[] dataBuf = new byte[8192];
        private int dataLen = 0;
        private final Queue<byte[]> writeQueue = new ConcurrentLinkedQueue<>();
        private ByteBuffer currentWrite;
        private volatile boolean closeAfterWrite = false;
        private final int readTimeoutMs;
        private volatile long lastActive;
        private volatile long lastRead;
        private volatile boolean waitingBody;
        private volatile boolean closed;
        private volatile boolean sentContinue;

        VKConn(VKWebServer server,
               SocketChannel channel,
               SelectionKey key,
               VKHttpParser parser,
               VKBufferPool bufferPool,
               VKWorkerPool workers,
               VKRouter router,
               List<VKMiddleware> middlewares,
               VKErrorHandler errorHandler,
               VKReactor reactor,
               VKWebConfig config) {
            this.server = server;
            this.channel = channel;
            this.key = key;
            this.parser = parser;
            this.bufferPool = bufferPool;
            this.workers = workers;
            this.router = router;
            this.middlewares = middlewares;
            this.errorHandler = errorHandler;
            this.reactor = reactor;
            this.readTimeoutMs = config.getReadTimeoutMs();
            this.readBuffer = bufferPool.acquire();
            this.lastActive = System.currentTimeMillis();
            this.lastRead = lastActive;
        }

        SelectionKey key() {
            return key;
        }

        void onRead() {
            int read;
            try {
                read = channel.read(readBuffer);
            } catch (IOException e) {
                close();
                return;
            }
            if (read <= 0) {
                if (read < 0) {
                    close();
                }
                return;
            }
            lastRead = System.currentTimeMillis();
            lastActive = lastRead;
            readBuffer.flip();
            ensureCapacity(dataLen + readBuffer.remaining());
            while (readBuffer.hasRemaining()) {
                dataBuf[dataLen++] = readBuffer.get();
            }
            readBuffer.clear();

            while (true) {
                VKHttpParser.ParsedRequest parsed;
                try {
                    parsed = parser.parse(dataBuf, dataLen, remoteAddress(), sentContinue);
                } catch (VKHttpParseException e) {
                    respondError(e.status(), e.getMessage(), true);
                    return;
                } catch (RuntimeException e) {
                    respondError(400, "Bad Request", true);
                    return;
                }

                if (parsed == null) {
                    waitingBody = true;
                    if (!sentContinue && parser.shouldSendContinue(dataBuf, dataLen)) {
                        enqueueResponse(VKHttpWriter.writeContinue(), false);
                        sentContinue = true;
                    }
                    return;
                }
                waitingBody = false;
                sentContinue = false;

                VKRequest req = parsed.request();
                int consumed = parsed.consumed();
                shift(consumed);
                dispatch(req);
            }
        }

        void onWrite() {
            try {
                if (currentWrite == null) {
                    byte[] next = writeQueue.poll();
                    if (next == null) {
                        key.interestOps(SelectionKey.OP_READ);
                        if (closeAfterWrite) {
                            close();
                        }
                        return;
                    }
                    currentWrite = ByteBuffer.wrap(next);
                }

                channel.write(currentWrite);
                if (!currentWrite.hasRemaining()) {
                    currentWrite = null;
                }
            } catch (IOException e) {
                close();
            }
        }

        void enqueueResponse(byte[] bytes, boolean close) {
            writeQueue.add(bytes);
            if (close) {
                closeAfterWrite = true;
            }
            reactor.requestWrite(this);
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                key.cancel();
            } catch (Exception ignore) {
            }
            try {
                channel.close();
            } catch (IOException ignore) {
            }
            bufferPool.release(readBuffer);
            server.decConnections();
        }

        private void dispatch(VKRequest req) {
            boolean accepted = workers.submit(() -> {
                VKResponse res = new VKResponse();
                try {
                    var match = router.match(req.method(), req.path());
                    if (match == null || match.handler() == null) {
                        res.status(404).text("Not Found");
                    } else if (middlewares.isEmpty()) {
                        req.setParams(match.params());
                        match.handler().handle(req, res);
                    } else {
                        req.setParams(match.params());
                        VKChain chain = new VKChain(middlewares, match.handler());
                        chain.next(req, res);
                    }
                } catch (Throwable t) {
                    try {
                        errorHandler.handle(t, req, res);
                    } catch (Throwable ignore) {
                        res.status(500).text("Internal Server Error");
                    }
                }

                boolean keepAlive = req.keepAlive();
                byte[] out = VKHttpWriter.write(res, keepAlive);
                enqueueResponse(out, !keepAlive);
            });
            if (!accepted) {
                VKResponse res = new VKResponse().status(503).text("Service Unavailable");
                enqueueResponse(VKHttpWriter.write(res, false), true);
            }
        }

        private void respondError(int status, String msg, boolean close) {
            VKResponse res = new VKResponse().status(status).text(msg == null ? "" : msg);
            byte[] out = VKHttpWriter.write(res, false);
            enqueueResponse(out, close);
        }

        void respondTimeout() {
            VKResponse res = new VKResponse().status(408).text("Request Timeout");
            enqueueResponse(VKHttpWriter.write(res, false), true);
        }

        private void ensureCapacity(int size) {
            if (size <= dataBuf.length) {
                return;
            }
            int newSize = dataBuf.length;
            while (newSize < size) {
                newSize *= 2;
            }
            byte[] newBuf = new byte[newSize];
            System.arraycopy(dataBuf, 0, newBuf, 0, dataLen);
            dataBuf = newBuf;
        }

        private void shift(int consumed) {
            int remaining = dataLen - consumed;
            if (remaining > 0) {
                System.arraycopy(dataBuf, consumed, dataBuf, 0, remaining);
            }
            dataLen = remaining;
        }

        private InetSocketAddress remoteAddress() {
            try {
                return (InetSocketAddress) channel.getRemoteAddress();
            } catch (IOException e) {
                return null;
            }
        }

        boolean waitingForBody() {
            return waitingBody;
        }

        long lastActive() {
            return lastActive;
        }

        long lastReadTime() {
            return lastRead;
        }

        boolean isClosed() {
            return closed;
        }
    }
}
