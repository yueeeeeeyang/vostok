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
import java.util.concurrent.atomic.AtomicLong;

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
    private static final AtomicLong TRACE_SEQ = new AtomicLong();

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
        private final Queue<VKOutbound> writeQueue = new ConcurrentLinkedQueue<>();
        private ByteBuffer currentWrite;
        private VKOutbound currentOutbound;
        private long currentFilePos;
        private volatile boolean closeAfterWrite = false;
        private final int readTimeoutMs;
        private volatile long lastActive;
        private volatile long lastRead;
        private volatile boolean waitingBody;
        private volatile boolean closed;
        private volatile boolean sentContinue;
        private long lastCostMs;
        private int lastStatus;
        private long lastBytes;

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
                        enqueueResponse(VKOutbound.fromBytes(VKHttpWriter.writeContinue()), false);
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
                if (currentOutbound == null) {
                    currentOutbound = writeQueue.poll();
                    currentWrite = null;
                    currentFilePos = 0;
                    if (currentOutbound == null) {
                        key.interestOps(SelectionKey.OP_READ);
                        if (closeAfterWrite) {
                            close();
                        }
                        return;
                    }
                }

                if (currentWrite == null && currentOutbound.data != null) {
                    currentWrite = ByteBuffer.wrap(currentOutbound.data);
                }

                if (currentWrite != null) {
                    channel.write(currentWrite);
                    if (currentWrite.hasRemaining()) {
                        return;
                    }
                    currentWrite = null;
                }

                if (currentOutbound.file != null) {
                    long transferred = currentOutbound.file.transferTo(currentFilePos, currentOutbound.fileLength - currentFilePos, channel);
                    if (transferred > 0) {
                        currentFilePos += transferred;
                    }
                    if (currentFilePos < currentOutbound.fileLength) {
                        return;
                    }
                    try {
                        currentOutbound.file.close();
                    } catch (IOException ignore) {
                    }
                }
                currentOutbound = null;
            } catch (IOException e) {
                close();
            }
        }

        void enqueueResponse(VKOutbound outbound, boolean close) {
            writeQueue.add(outbound);
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
            if (currentOutbound != null && currentOutbound.file != null) {
                try {
                    currentOutbound.file.close();
                } catch (IOException ignore) {
                }
            }
        }

        private void dispatch(VKRequest req) {
            boolean accepted = workers.submit(() -> {
                long start = System.nanoTime();
                VKResponse res = new VKResponse();
                try {
                    ensureTraceId(req, res);
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
                VKOutbound out = VKOutbound.from(res, keepAlive);
                enqueueResponse(out, !keepAlive);
                long cost = (System.nanoTime() - start) / 1_000_000;
                this.lastCostMs = cost;
                this.lastStatus = res.status();
                this.lastBytes = out.totalBytes();
                logAccess(req);
            });
            if (!accepted) {
                VKResponse res = new VKResponse().status(503).text("Service Unavailable");
                enqueueResponse(VKOutbound.from(res, false), true);
            }
        }

        private void respondError(int status, String msg, boolean close) {
            VKResponse res = new VKResponse().status(status).text(msg == null ? "" : msg);
            enqueueResponse(VKOutbound.from(res, false), close);
        }

        void respondTimeout() {
            VKResponse res = new VKResponse().status(408).text("Request Timeout");
            enqueueResponse(VKOutbound.from(res, false), true);
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

        private void ensureTraceId(VKRequest req, VKResponse res) {
            String tid = req.header("x-trace-id");
            if (tid == null || tid.isEmpty()) {
                tid = Long.toHexString(System.nanoTime()) + "-" + TRACE_SEQ.incrementAndGet();
            }
            req.setTraceId(tid);
            if (res.headers().get("X-Trace-Id") == null) {
                res.header("X-Trace-Id", tid);
            }
        }

        private void logAccess(VKRequest req) {
            if (!server.accessLogEnabled()) {
                return;
            }
            String ip = req.remoteAddress() == null ? "-" : req.remoteAddress().getAddress().getHostAddress();
            String traceId = req.traceId() == null ? "-" : req.traceId();
            String msg = ip + " \"" + req.method() + " " + req.path() + "\" " + lastStatus + " " + lastBytes + " " + lastCostMs + "ms trace=" + traceId;
            yueyang.vostok.util.VKLog.info(msg);
        }
    }

    static final class VKOutbound {
        private final byte[] data;
        private final java.nio.channels.FileChannel file;
        private final long fileLength;

        private VKOutbound(byte[] data, java.nio.channels.FileChannel file, long fileLength) {
            this.data = data;
            this.file = file;
            this.fileLength = fileLength;
        }

        static VKOutbound from(VKResponse res, boolean keepAlive) {
            if (res.isFile()) {
                byte[] head = VKHttpWriter.writeHead(res, keepAlive);
                try {
                    java.nio.channels.FileChannel fc = java.nio.channels.FileChannel.open(res.filePath(), java.nio.file.StandardOpenOption.READ);
                    return new VKOutbound(head, fc, res.fileLength());
                } catch (IOException e) {
                    byte[] out = VKHttpWriter.write(new VKResponse().status(404).text("Not Found"), false);
                    return new VKOutbound(out, null, 0);
                }
            }
            byte[] out = VKHttpWriter.write(res, keepAlive);
            return new VKOutbound(out, null, out.length);
        }

        static VKOutbound fromBytes(byte[] bytes) {
            return new VKOutbound(bytes, null, bytes == null ? 0 : bytes.length);
        }

        long totalBytes() {
            if (file != null) {
                return (data == null ? 0 : data.length) + fileLength;
            }
            return data == null ? 0 : data.length;
        }
    }
}
