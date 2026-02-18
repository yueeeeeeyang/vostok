package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKWebConfig;
import yueyang.vostok.web.http.VKHttpParseException;
import yueyang.vostok.web.http.VKHttpParser;
import yueyang.vostok.web.http.VKMultipartParseException;
import yueyang.vostok.web.http.VKMultipartData;
import yueyang.vostok.web.http.VKMultipartStreamDecoder;
import yueyang.vostok.web.http.VKHttpWriter;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.middleware.VKChain;
import yueyang.vostok.web.middleware.VKMiddleware;
import yueyang.vostok.web.route.VKRouter;
import yueyang.vostok.web.util.VKBufferPool;
import yueyang.vostok.web.websocket.VKWebSocketEndpoint;
import yueyang.vostok.web.websocket.VKWebSocketSession;
import yueyang.vostok.web.websocket.VKWsFrame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
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
        private enum Protocol {
            HTTP,
            WS
        }

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
        private volatile Protocol protocol = Protocol.HTTP;
        private volatile VKWebSocketSession wsSession;
        private volatile VKWebSocketEndpoint wsEndpoint;
        private volatile boolean wsAwaitingPong;
        private volatile long wsLastPingAt;
        private volatile long wsLastPongAt;
        private volatile int wsPendingFrames;
        private volatile int wsPendingBytes;
        private int wsFragmentOpcode = -1;
        private ByteArrayOutputStream wsFragmentBuffer;

        private final AtomicInteger inFlight = new AtomicInteger();
        private MultipartCtx multipartCtx;

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
            this.wsLastPongAt = lastActive;
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

            if (protocol == Protocol.WS) {
                parseWebSocketFrames();
                rescheduleTimeout(now);
                return;
            }

            while (true) {
                if (multipartCtx != null) {
                    if (!consumeMultipartBody()) {
                        waitingBody = true;
                        rescheduleTimeout(now);
                        return;
                    }
                    waitingBody = false;
                    continue;
                }
                VKHttpParser.ParsedHeaders headers;
                try {
                    headers = parser.parseHeadersOnly(dataBuf, dataLen);
                } catch (VKHttpParseException e) {
                    respondError(e.status(), e.getMessage(), true);
                    return;
                } catch (RuntimeException e) {
                    respondError(400, "Bad Request", true);
                    return;
                }
                if (headers != null && isStreamingMultipart(headers)) {
                    if (headers.chunked()) {
                        respondError(400, "Chunked multipart is not supported", true);
                        return;
                    }
                    int cl = headers.contentLength();
                    if (cl < 0 || cl > webConfig.getMaxBodyBytes() || cl > webConfig.getMultipartMaxTotalBytes()) {
                        respondError(413, "Body too large", true);
                        return;
                    }
                    String contentType = headers.headers().get("content-type");
                    String boundary = extractBoundary(contentType);
                    if (boundary == null || boundary.isEmpty()) {
                        respondError(400, "Invalid multipart boundary", true);
                        return;
                    }
                    initMultipart(headers, boundary);
                    int consumed = headers.headerEnd() + 4;
                    shift(consumed);
                    continue;
                }
                break;
            }

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

                if (protocol == Protocol.WS) {
                    parseWebSocketFrames();
                    rescheduleTimeout(System.currentTimeMillis());
                    return;
                }
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

                    if (currentOutbound.wsFrame) {
                        wsPendingFrames = Math.max(0, wsPendingFrames - 1);
                        wsPendingBytes = Math.max(0, wsPendingBytes - (int) currentOutbound.totalBytes());
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
            if (protocol == Protocol.WS) {
                handleWebSocketTimeout(now);
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
            if (protocol == Protocol.WS) {
                timeoutToken = reactor.timer.schedule(this, now + 1000);
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
            boolean wsWasOpen = protocol == Protocol.WS;
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
            if (multipartCtx != null) {
                multipartCtx.abort();
                multipartCtx = null;
            }
            if (wsWasOpen && wsEndpoint != null && wsSession != null) {
                try {
                    wsEndpoint.handler().onClose(wsSession, 1006, "Abnormal Closure");
                } catch (Throwable ignore) {
                }
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
                    if (tryUpgradeWebSocket(req)) {
                        return;
                    }
                    var match = router.match(req.method(), req.path());
                    if (match == null || match.handler() == null) {
                        res.status(404).text("Not Found");
                    } else {
                        req.setParams(match.params());
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
                    if (protocol == Protocol.WS && wsEndpoint != null) {
                        wsEndpoint.handler().onError(wsSession, t);
                        return;
                    }
                    try {
                        errorHandler.handle(t, req, res);
                    } catch (Throwable ignore) {
                        res.status(500).text("Internal Server Error");
                    }
                } finally {
                    if (protocol == Protocol.WS) {
                        inFlight.decrementAndGet();
                        reactor.requestReschedule(this);
                        return;
                    }
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

        private boolean tryUpgradeWebSocket(VKRequest req) {
            if (!webConfig.isWebsocketEnabled()) {
                return false;
            }
            String upgrade = req.header("upgrade");
            if (upgrade == null || !"websocket".equalsIgnoreCase(upgrade)) {
                return false;
            }
            VKWebSocketEndpoint endpoint = server.findWebSocket(req.path());
            if (endpoint == null) {
                respondError(404, "Not Found", true);
                return true;
            }
            String conn = req.header("connection");
            String version = req.header("sec-websocket-version");
            String key = req.header("sec-websocket-key");
            if (conn == null || !conn.toLowerCase().contains("upgrade")
                    || key == null || key.isEmpty()
                    || version == null || !"13".equals(version)) {
                respondError(400, "Bad WebSocket Request", true);
                return true;
            }
            String accept = websocketAccept(key);
            byte[] head = ("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "X-Trace-Id: " + (req.traceId() == null ? "" : req.traceId()) + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
            enqueueResponse(VKOutbound.fromHeadBytes(head), false);
            enableWebSocket(req, endpoint);
            return true;
        }

        private void enableWebSocket(VKRequest req, VKWebSocketEndpoint endpoint) {
            protocol = Protocol.WS;
            wsEndpoint = endpoint;
            wsLastPongAt = System.currentTimeMillis();
            wsLastPingAt = wsLastPongAt;
            wsAwaitingPong = false;
            wsPendingFrames = 0;
            wsPendingBytes = 0;
            wsSession = new VKWebSocketSession(
                    Long.toHexString(System.nanoTime()) + "-" + TRACE_SEQ.incrementAndGet(),
                    endpoint.path(),
                    req.traceId(),
                    req.remoteAddress(),
                    () -> !closed && protocol == Protocol.WS,
                    this::sendWsFrame,
                    this::close
            );
            try {
                endpoint.handler().onOpen(wsSession);
            } catch (Throwable t) {
                endpoint.handler().onError(wsSession, t);
                close();
            }
        }

        private void parseWebSocketFrames() {
            while (!closed) {
                if (dataLen < 2) {
                    return;
                }
                int b0 = dataBuf[0] & 0xFF;
                int b1 = dataBuf[1] & 0xFF;
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                int pos = 2;
                if (len == 126) {
                    if (dataLen < 4) {
                        return;
                    }
                    len = ((dataBuf[2] & 0xFF) << 8) | (dataBuf[3] & 0xFF);
                    pos = 4;
                } else if (len == 127) {
                    if (dataLen < 10) {
                        return;
                    }
                    len = 0;
                    for (int i = 2; i < 10; i++) {
                        len = (len << 8) | (dataBuf[i] & 0xFF);
                    }
                    pos = 10;
                }
                if (len > Integer.MAX_VALUE) {
                    closeWebSocket(1009, "Message Too Big");
                    return;
                }
                int payloadLen = (int) len;
                if (!masked) {
                    closeWebSocket(1002, "Client frame not masked");
                    return;
                }
                if (payloadLen > wsEndpoint.config().getMaxFramePayloadBytes()) {
                    closeWebSocket(1009, "Frame Too Large");
                    return;
                }
                int frameTotal = pos + 4 + payloadLen;
                if (dataLen < frameTotal) {
                    return;
                }
                byte m0 = dataBuf[pos];
                byte m1 = dataBuf[pos + 1];
                byte m2 = dataBuf[pos + 2];
                byte m3 = dataBuf[pos + 3];
                pos += 4;
                byte[] payload = new byte[payloadLen];
                for (int i = 0; i < payloadLen; i++) {
                    byte mask = switch (i & 3) {
                        case 0 -> m0;
                        case 1 -> m1;
                        case 2 -> m2;
                        default -> m3;
                    };
                    payload[i] = (byte) (dataBuf[pos + i] ^ mask);
                }
                shift(frameTotal);
                lastActive = System.currentTimeMillis();

                if (!handleWebSocketFrame(opcode, fin, payload)) {
                    return;
                }
            }
        }

        private boolean handleWebSocketFrame(int opcode, boolean fin, byte[] payload) {
            if (opcode == VKWsFrame.OPCODE_PING) {
                sendWsFrame(VKWsFrame.pong(payload));
                return true;
            }
            if (opcode == VKWsFrame.OPCODE_PONG) {
                wsAwaitingPong = false;
                wsLastPongAt = System.currentTimeMillis();
                return true;
            }
            if (opcode == VKWsFrame.OPCODE_CLOSE) {
                closeWebSocket(1000, "");
                return false;
            }
            if (opcode == VKWsFrame.OPCODE_TEXT || opcode == VKWsFrame.OPCODE_BINARY) {
                if (!fin) {
                    wsFragmentOpcode = opcode;
                    wsFragmentBuffer = new ByteArrayOutputStream();
                    wsFragmentBuffer.writeBytes(payload);
                    if (wsFragmentBuffer.size() > wsEndpoint.config().getMaxMessageBytes()) {
                        closeWebSocket(1009, "Message Too Big");
                        return false;
                    }
                    return true;
                }
                dispatchWebSocketMessage(opcode, payload);
                return true;
            }
            if (opcode == VKWsFrame.OPCODE_CONTINUATION) {
                if (wsFragmentBuffer == null || wsFragmentOpcode < 0) {
                    closeWebSocket(1002, "Unexpected continuation");
                    return false;
                }
                wsFragmentBuffer.writeBytes(payload);
                if (wsFragmentBuffer.size() > wsEndpoint.config().getMaxMessageBytes()) {
                    closeWebSocket(1009, "Message Too Big");
                    return false;
                }
                if (fin) {
                    byte[] data = wsFragmentBuffer.toByteArray();
                    int op = wsFragmentOpcode;
                    wsFragmentBuffer = null;
                    wsFragmentOpcode = -1;
                    dispatchWebSocketMessage(op, data);
                }
                return true;
            }
            closeWebSocket(1002, "Bad opcode");
            return false;
        }

        private void dispatchWebSocketMessage(int opcode, byte[] payload) {
            inFlight.incrementAndGet();
            boolean accepted = workers.submit(() -> {
                try {
                    if (wsEndpoint == null || wsSession == null) {
                        return;
                    }
                    if (opcode == VKWsFrame.OPCODE_TEXT) {
                        wsEndpoint.handler().onText(wsSession, new String(payload, StandardCharsets.UTF_8));
                    } else if (opcode == VKWsFrame.OPCODE_BINARY) {
                        wsEndpoint.handler().onBinary(wsSession, payload);
                    }
                } catch (Throwable t) {
                    if (wsEndpoint != null && wsSession != null) {
                        wsEndpoint.handler().onError(wsSession, t);
                    }
                } finally {
                    inFlight.decrementAndGet();
                    reactor.requestReschedule(this);
                }
            });
            if (!accepted) {
                inFlight.decrementAndGet();
                closeWebSocket(1013, "Try Again Later");
            }
        }

        private void sendWsFrame(VKWsFrame frame) {
            if (frame == null || protocol != Protocol.WS || closed || wsEndpoint == null) {
                return;
            }
            int nextFrames = wsPendingFrames + 1;
            int nextBytes = wsPendingBytes + frame.payload().length;
            if (nextFrames > wsEndpoint.config().getMaxPendingFrames()
                    || nextBytes > wsEndpoint.config().getMaxPendingBytes()) {
                closeWebSocket(1008, "Backpressure");
                return;
            }
            wsPendingFrames = nextFrames;
            wsPendingBytes = nextBytes;
            enqueueResponse(VKOutbound.fromWsFrame(frame.encode()), false);
        }

        private void closeWebSocket(int code, String reason) {
            if (protocol != Protocol.WS || closed) {
                close();
                return;
            }
            byte[] reasonBytes = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + reasonBytes.length];
            payload[0] = (byte) ((code >> 8) & 0xFF);
            payload[1] = (byte) (code & 0xFF);
            System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
            sendWsFrame(VKWsFrame.close(payload));
            if (wsEndpoint != null && wsSession != null) {
                wsEndpoint.handler().onClose(wsSession, code, reason == null ? "" : reason);
            }
            wsEndpoint = null;
            wsSession = null;
            closeAfterWrite = true;
        }

        private void handleWebSocketTimeout(long now) {
            if (wsEndpoint == null) {
                close();
                return;
            }
            int idleTimeoutMs = wsEndpoint.config().getIdleTimeoutMs();
            if (now - lastActive >= idleTimeoutMs) {
                closeWebSocket(1001, "Idle Timeout");
                return;
            }
            int pingIntervalMs = wsEndpoint.config().getPingIntervalMs();
            int pongTimeoutMs = wsEndpoint.config().getPongTimeoutMs();
            if (!wsAwaitingPong && now - wsLastPingAt >= pingIntervalMs) {
                wsAwaitingPong = true;
                wsLastPingAt = now;
                sendWsFrame(VKWsFrame.ping(new byte[0]));
            } else if (wsAwaitingPong && now - wsLastPingAt >= pongTimeoutMs) {
                closeWebSocket(1001, "Pong Timeout");
                return;
            }
            rescheduleTimeout(now);
        }

        private String websocketAccept(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] digest = md.digest((key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                        .getBytes(StandardCharsets.US_ASCII));
                return Base64.getEncoder().encodeToString(digest);
            } catch (Exception e) {
                throw new IllegalStateException("WebSocket handshake failed", e);
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

        private boolean isStreamingMultipart(VKHttpParser.ParsedHeaders headers) {
            if (!webConfig.isMultipartEnabled()) {
                return false;
            }
            if (headers == null || headers.contentLength() <= 0) {
                return false;
            }
            String ct = headers.headers().get("content-type");
            return ct != null && ct.toLowerCase().startsWith("multipart/form-data");
        }

        private String extractBoundary(String contentType) {
            if (contentType == null) {
                return null;
            }
            String[] parts = contentType.split(";");
            for (String p : parts) {
                String s = p.trim();
                if (s.toLowerCase().startsWith("boundary=")) {
                    String v = s.substring("boundary=".length()).trim();
                    if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return v;
                }
            }
            return null;
        }

        private void initMultipart(VKHttpParser.ParsedHeaders headers, String boundary) {
            VKRequest req = new VKRequest(headers.method(), headers.path(), headers.query(), headers.version(),
                    headers.headers(), new byte[0], headers.keepAlive(), remoteAddress());
            multipartCtx = new MultipartCtx(req, new VKMultipartStreamDecoder(boundary, webConfig), headers.contentLength());
        }

        private boolean consumeMultipartBody() {
            if (multipartCtx == null) {
                return true;
            }
            if (dataLen > 0 && multipartCtx.remaining > 0) {
                int n = Math.min(dataLen, multipartCtx.remaining);
                try {
                    multipartCtx.decoder.feed(dataBuf, 0, n);
                } catch (VKMultipartParseException e) {
                    multipartCtx.abort();
                    multipartCtx = null;
                    respondError(e.status(), e.getMessage(), true);
                    return false;
                }
                shift(n);
                multipartCtx.remaining -= n;
            }
            if (multipartCtx.remaining > 0) {
                return false;
            }
            try {
                multipartCtx.decoder.finish();
                VKMultipartData data = multipartCtx.decoder.result();
                multipartCtx.request.applyMultipart(data);
            } catch (VKMultipartParseException e) {
                multipartCtx.abort();
                multipartCtx = null;
                respondError(e.status(), e.getMessage(), true);
                return false;
            }
            VKRequest req = multipartCtx.request;
            multipartCtx = null;
            dispatch(req);
            return true;
        }

        private static final class MultipartCtx {
            final VKRequest request;
            final VKMultipartStreamDecoder decoder;
            int remaining;

            MultipartCtx(VKRequest request, VKMultipartStreamDecoder decoder, int remaining) {
                this.request = request;
                this.decoder = decoder;
                this.remaining = remaining;
            }

            void abort() {
                decoder.abort();
                request.cleanupUploads();
            }
        }
    }

    static final class VKOutbound {
        private final byte[] head;
        private final byte[] body;
        private final java.nio.channels.FileChannel file;
        private final long fileLength;
        private final boolean wsFrame;

        private VKOutbound(byte[] head, byte[] body, java.nio.channels.FileChannel file, long fileLength, boolean wsFrame) {
            this.head = head;
            this.body = body;
            this.file = file;
            this.fileLength = fileLength;
            this.wsFrame = wsFrame;
        }

        static VKOutbound from(VKResponse res, boolean keepAlive) {
            if (res.isFile()) {
                byte[] head = VKHttpWriter.writeHead(res, keepAlive);
                try {
                    java.nio.channels.FileChannel fc = java.nio.channels.FileChannel.open(
                            res.filePath(), java.nio.file.StandardOpenOption.READ);
                    return new VKOutbound(head, null, fc, res.fileLength(), false);
                } catch (IOException e) {
                    VKResponse fail = new VKResponse().status(404).text("Not Found");
                    byte[] h = VKHttpWriter.writeHead(fail, false);
                    return new VKOutbound(h, fail.body(), null, 0, false);
                }
            }
            byte[] head = VKHttpWriter.writeHead(res, keepAlive);
            return new VKOutbound(head, res.body(), null, 0, false);
        }

        static VKOutbound fromHeadBytes(byte[] bytes) {
            return new VKOutbound(bytes, null, null, 0, false);
        }

        static VKOutbound fromWsFrame(byte[] bytes) {
            return new VKOutbound(bytes, null, null, 0, true);
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
