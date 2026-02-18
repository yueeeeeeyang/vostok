package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKHttpParseException;
import yueyang.vostok.web.http.VKHttpParser;
import yueyang.vostok.web.http.VKMultipartParseException;
import yueyang.vostok.web.http.VKMultipartParser;
import yueyang.vostok.web.http.VKHttpWriter;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.middleware.VKChain;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.route.VKRouter;
import yueyang.vostok.web.util.VKBufferPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class VKReactor implements Runnable {
    private static final AtomicLong TRACE_SEQ = new AtomicLong();

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
    private final VKHashedWheelTimer<VKConn> timer;
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
        this.timer = new VKHashedWheelTimer<>(1000, 1024, System.currentTimeMillis());
    }

    @Override
    public void run() {
        while (running) {
            try {
                selector.select(200);
                drainPending();
                var iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    VKConn conn = (VKConn) key.attachment();
                    if (conn == null || conn.isClosed()) {
                        continue;
                    }
                    try {
                        if (key.isReadable()) {
                            conn.onRead();
                        }
                        if (key.isValid() && key.isWritable()) {
                            conn.onWrite();
                        }
                    } catch (java.nio.channels.CancelledKeyException ignore) {
                    }
                }
                pollTimeouts();
            } catch (java.nio.channels.ClosedSelectorException e) {
                break;
            } catch (IOException ignore) {
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
                VKConn conn = new VKConn(server, channel, key, parser, bufferPool, workers, router,
                        middlewares, errorHandler, this, config);
                key.attach(conn);
                server.incConnections();
                conn.rescheduleTimeout(System.currentTimeMillis());
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

    void requestReschedule(VKConn conn) {
        pending.add(() -> conn.rescheduleTimeout(System.currentTimeMillis()));
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

    private void pollTimeouts() {
        long now = System.currentTimeMillis();
        timer.pollExpired(now, entry -> {
            VKConn conn = entry.target;
            if (conn == null || conn.isClosed()) {
                return;
            }
            if (conn.timeoutToken() != entry.token) {
                return;
            }
            conn.onTimeout(now);
        });
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
        private final int readTimeoutMs;
        private final VKWebConfig webConfig;

        private byte[] dataBuf = new byte[8192];
        private int dataLen;

        private final Queue<VKOutbound> writeQueue = new ConcurrentLinkedQueue<>();
        private VKOutbound currentOutbound;
        private ByteBuffer currentHead;
        private ByteBuffer currentBody;
        private long currentFilePos;

        private volatile boolean closeAfterWrite;
        private volatile boolean waitingBody;
        private volatile boolean closed;
        private volatile boolean sentContinue;

        private volatile long lastActive;
        private volatile long lastRead;
        private volatile long timeoutToken = -1;

        private final AtomicInteger inFlight = new AtomicInteger();

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
            this.webConfig = config;
            this.readTimeoutMs = config.getReadTimeoutMs();
            this.readBuffer = bufferPool.acquire();
            this.lastActive = System.currentTimeMillis();
            this.lastRead = lastActive;
        }

        SelectionKey key() {
            return key;
        }

        long timeoutToken() {
            return timeoutToken;
        }

        boolean isClosed() {
            return closed;
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

            long now = System.currentTimeMillis();
            lastRead = now;
            lastActive = now;

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
                        enqueueResponse(VKOutbound.fromHeadBytes(VKHttpWriter.writeContinue()), false);
                        sentContinue = true;
                    }
                    rescheduleTimeout(now);
                    return;
                }

                waitingBody = false;
                sentContinue = false;

                VKRequest req = parsed.request();
                int consumed = parsed.consumed();
                shift(consumed);
                dispatch(req);

                if (dataLen == 0) {
                    rescheduleTimeout(System.currentTimeMillis());
                    return;
                }
            }
        }

        void onWrite() {
            try {
                while (true) {
                    if (currentOutbound == null) {
                        currentOutbound = writeQueue.poll();
                        currentHead = null;
                        currentBody = null;
                        currentFilePos = 0;
                        if (currentOutbound == null) {
                            if (key.isValid()) {
                                key.interestOps(SelectionKey.OP_READ);
                            }
                            if (closeAfterWrite) {
                                close();
                            } else {
                                lastActive = System.currentTimeMillis();
                                rescheduleTimeout(lastActive);
                            }
                            return;
                        }
                    }

                    if (currentHead == null && currentOutbound.head != null) {
                        currentHead = ByteBuffer.wrap(currentOutbound.head);
                    }
                    if (currentHead != null) {
                        channel.write(currentHead);
                        if (currentHead.hasRemaining()) {
                            return;
                        }
                        currentHead = null;
                    }

                    if (currentBody == null && currentOutbound.body != null) {
                        currentBody = ByteBuffer.wrap(currentOutbound.body);
                    }
                    if (currentBody != null) {
                        channel.write(currentBody);
                        if (currentBody.hasRemaining()) {
                            return;
                        }
                        currentBody = null;
                    }

                    if (currentOutbound.file != null) {
                        long transferred = currentOutbound.file.transferTo(currentFilePos,
                                currentOutbound.fileLength - currentFilePos, channel);
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
                }
            } catch (IOException e) {
                close();
            }
        }

        void onTimeout(long now) {
            if (closed) {
                return;
            }
            if (inFlight.get() > 0) {
                rescheduleTimeout(now);
                return;
            }
            if (waitingBody) {
                if (now - lastRead >= readTimeoutMs) {
                    respondTimeout();
                    return;
                }
            } else {
                if (now - lastActive >= server.keepAliveTimeoutMs()) {
                    close();
                    return;
                }
            }
            rescheduleTimeout(now);
        }

        void rescheduleTimeout(long now) {
            if (closed) {
                return;
            }
            long timeoutMs = waitingBody ? readTimeoutMs : server.keepAliveTimeoutMs();
            if (inFlight.get() > 0) {
                timeoutMs = Math.max(timeoutMs, server.keepAliveTimeoutMs());
            }
            long deadline = now + timeoutMs;
            timeoutToken = reactor.timer.schedule(this, deadline);
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
            timeoutToken = -1;

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

            closeOutbound(currentOutbound);
            VKOutbound pending;
            while ((pending = writeQueue.poll()) != null) {
                closeOutbound(pending);
            }
        }

        private void closeOutbound(VKOutbound out) {
            if (out != null && out.file != null) {
                try {
                    out.file.close();
                } catch (IOException ignore) {
                }
            }
        }

        private void dispatch(VKRequest req) {
            inFlight.incrementAndGet();
            boolean accepted = workers.submit(() -> {
                long start = System.nanoTime();
                VKResponse res = new VKResponse();
                try {
                    ensureTraceId(req, res);
                    var match = router.match(req.method(), req.path());
                    if (match == null || match.handler() == null) {
                        res.status(404).text("Not Found");
                    } else {
                        req.setParams(match.params());
                        if (webConfig.isMultipartEnabled() && req.isMultipart()) {
                            req.applyMultipart(VKMultipartParser.parse(req, webConfig));
                        }
                        if (!server.tryRateLimit(req, match, res)) {
                            return;
                        }
                        if (middlewares.isEmpty()) {
                            match.handler().handle(req, res);
                        } else {
                            VKChain chain = new VKChain(middlewares, match.handler());
                            chain.next(req, res);
                        }
                    }
                } catch (VKMultipartParseException e) {
                    res.status(e.status()).text(e.getMessage());
                } catch (Throwable t) {
                    try {
                        errorHandler.handle(t, req, res);
                    } catch (Throwable ignore) {
                        res.status(500).text("Internal Server Error");
                    }
                } finally {
                    if (res.headers().get("X-Trace-Id") == null && req.traceId() != null) {
                        res.header("X-Trace-Id", req.traceId());
                    }

                    boolean keepAlive = req.keepAlive();
                    VKOutbound out = VKOutbound.from(res, keepAlive);
                    enqueueResponse(out, !keepAlive);

                    long costMs = (System.nanoTime() - start) / 1_000_000;
                    logAccess(req, res.status(), out.totalBytes(), costMs);

                    req.cleanupUploads();
                    inFlight.decrementAndGet();
                    reactor.requestReschedule(this);
                }
            });

            if (!accepted) {
                inFlight.decrementAndGet();
                VKResponse res = new VKResponse().status(503).text("Service Unavailable");
                enqueueResponse(VKOutbound.from(res, false), true);
                rescheduleTimeout(System.currentTimeMillis());
            }
        }

        private void respondError(int status, String msg, boolean close) {
            VKResponse res = new VKResponse().status(status).text(msg == null ? "" : msg);
            enqueueResponse(VKOutbound.from(res, false), close);
            lastActive = System.currentTimeMillis();
            rescheduleTimeout(lastActive);
        }

        void respondTimeout() {
            VKResponse res = new VKResponse().status(408).text("Request Timeout");
            enqueueResponse(VKOutbound.from(res, false), true);
            lastActive = System.currentTimeMillis();
            rescheduleTimeout(lastActive);
        }

        private void ensureCapacity(int size) {
            if (size <= dataBuf.length) {
                return;
            }
            int newSize = dataBuf.length;
            while (newSize < size) {
                newSize <<= 1;
            }
            byte[] next = new byte[newSize];
            System.arraycopy(dataBuf, 0, next, 0, dataLen);
            dataBuf = next;
        }

        private void shift(int consumed) {
            int remain = dataLen - consumed;
            if (remain > 0) {
                System.arraycopy(dataBuf, consumed, dataBuf, 0, remain);
            }
            dataLen = remain;
        }

        private InetSocketAddress remoteAddress() {
            try {
                return (InetSocketAddress) channel.getRemoteAddress();
            } catch (IOException e) {
                return null;
            }
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

        private void logAccess(VKRequest req, int status, long bytes, long costMs) {
            if (!server.accessLogEnabled()) {
                return;
            }
            String ip = req.remoteAddress() == null ? "-" : req.remoteAddress().getAddress().getHostAddress();
            String traceId = req.traceId() == null ? "-" : req.traceId();
            String msg = ip + " \"" + req.method() + " " + req.path() + "\" " + status + " " + bytes + " "
                    + costMs + "ms trace=" + traceId;
            server.logAccess(msg);
        }
    }

    static final class VKOutbound {
        private final byte[] head;
        private final byte[] body;
        private final java.nio.channels.FileChannel file;
        private final long fileLength;

        private VKOutbound(byte[] head, byte[] body, java.nio.channels.FileChannel file, long fileLength) {
            this.head = head;
            this.body = body;
            this.file = file;
            this.fileLength = fileLength;
        }

        static VKOutbound from(VKResponse res, boolean keepAlive) {
            if (res.isFile()) {
                byte[] head = VKHttpWriter.writeHead(res, keepAlive);
                try {
                    java.nio.channels.FileChannel fc = java.nio.channels.FileChannel.open(
                            res.filePath(), java.nio.file.StandardOpenOption.READ);
                    return new VKOutbound(head, null, fc, res.fileLength());
                } catch (IOException e) {
                    VKResponse fail = new VKResponse().status(404).text("Not Found");
                    byte[] h = VKHttpWriter.writeHead(fail, false);
                    return new VKOutbound(h, fail.body(), null, 0);
                }
            }
            byte[] head = VKHttpWriter.writeHead(res, keepAlive);
            return new VKOutbound(head, res.body(), null, 0);
        }

        static VKOutbound fromHeadBytes(byte[] bytes) {
            return new VKOutbound(bytes, null, null, 0);
        }

        long totalBytes() {
            long n = 0;
            if (head != null) {
                n += head.length;
            }
            if (body != null) {
                n += body.length;
            }
            n += fileLength;
            return n;
        }
    }
}
