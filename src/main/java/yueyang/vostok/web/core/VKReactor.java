package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKHandler;
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
import yueyang.vostok.web.sse.VKSseEmitter;
import yueyang.vostok.web.util.VKBufferPool;
import yueyang.vostok.web.websocket.VKWebSocketEndpoint;
import yueyang.vostok.web.websocket.VKWebSocketSession;
import yueyang.vostok.web.websocket.VKWsAuthResult;
import yueyang.vostok.web.websocket.VKWsFrame;
import yueyang.vostok.web.websocket.VKWsHandshakeContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * NIO Reactor 事件循环，每个 Reactor 绑定一个 Selector 和一个线程。
 *
 * 职责：
 * - 管理 SocketChannel 的读写事件分发
 * - HTTP 解析与请求派发到 VKWorkerPool
 * - WebSocket 协议升级和帧解析
 * - SSE 连接保持（Protocol.SSE）
 * - TLS 握手与加解密（若配置了 SSLContext）
 * - 通过 pending 队列安全地在 reactor 线程内执行跨线程操作
 *
 * 线程模型：
 * - reactor 线程：独占执行 selector.select() 及所有 I/O 事件回调
 * - worker 线程：执行业务逻辑（handler/middleware），通过 pending 队列回调 reactor
 */
final class VKReactor implements Runnable {
    private static final AtomicLong TRACE_SEQ = new AtomicLong();

    /**
     * Perf4：ThreadLocal 缓存 SHA-1 MessageDigest，避免 WebSocket 握手时重复创建。
     * SHA-1 实例非线程安全，ThreadLocal 保证每个线程独占一个。
     */
    private static final ThreadLocal<MessageDigest> SHA1_CACHE = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 not available", e);
        }
    });

    private final VKWebServer server;
    private final Selector selector;
    private final VKWorkerPool workers;
    private final VKRouter router;
    private final List<VKMiddleware> middlewares;
    private final VKErrorHandler errorHandler;
    private final VKHttpParser parser;
    private final VKBufferPool bufferPool;
    private final VKWebConfig config;
    /** pending 队列：跨线程安全地向 reactor 线程投递任务（close、send 等）。 */
    final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private final VKHashedWheelTimer<VKConn> timer;
    private volatile boolean running = true;
    /** TLS 上下文，null 表示明文 HTTP。 */
    private final SSLContext sslContext;

    VKReactor(VKWebServer server,
              Selector selector,
              VKWorkerPool workers,
              VKRouter router,
              List<VKMiddleware> middlewares,
              VKErrorHandler errorHandler,
              VKHttpParser parser,
              VKBufferPool bufferPool,
              VKWebConfig config,
              SSLContext sslContext) {
        this.server = server;
        this.selector = selector;
        this.workers = workers;
        this.router = router;
        this.middlewares = middlewares;
        this.errorHandler = errorHandler;
        this.parser = parser;
        this.bufferPool = bufferPool;
        this.config = config;
        this.sslContext = sslContext;
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
                // 若配置了 TLS，为每个连接创建独立的 SSLEngine
                SSLEngine sslEngine = null;
                if (sslContext != null) {
                    sslEngine = sslContext.createSSLEngine();
                    sslEngine.setUseClientMode(false); // 服务端模式
                    if (config.getTlsConfig() != null && config.getTlsConfig().isClientAuth()) {
                        sslEngine.setNeedClientAuth(true);
                    }
                    if (config.getTlsConfig() != null && config.getTlsConfig().getEnabledProtocols() != null) {
                        sslEngine.setEnabledProtocols(config.getTlsConfig().getEnabledProtocols());
                    }
                    if (config.getTlsConfig() != null && config.getTlsConfig().getEnabledCipherSuites() != null) {
                        sslEngine.setEnabledCipherSuites(config.getTlsConfig().getEnabledCipherSuites());
                    }
                    sslEngine.beginHandshake(); // 触发握手状态机初始化
                }
                VKConn conn = new VKConn(server, channel, key, parser, bufferPool, workers, router,
                        middlewares, errorHandler, this, config, sslEngine);
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

    void requestContinueRead(VKConn conn) {
        pending.add(() -> {
            if (!conn.isClosed() && conn.inFlight.get() == 0 && conn.dataLen > 0) {
                conn.processDataBuf(System.currentTimeMillis());
            }
        });
        selector.wakeup();
    }

    /**
     * Bug1修复：将 WebSocket 帧发送投递到 reactor 的 pending 队列。
     * sendWsFrame 现在只在 reactor 线程调用，wsPendingFrames/wsPendingBytes 无竞态。
     *
     * @param conn  目标连接
     * @param frame 待发送的 WebSocket 帧
     */
    void scheduleSend(VKConn conn, VKWsFrame frame) {
        pending.add(() -> conn.sendWsFrame(frame));
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

    // ==========================================================================
    // VKConn - 单个 TCP 连接的状态机
    // ==========================================================================
    static final class VKConn {
        /**
         * 连接协议状态枚举：
         * - HTTP：标准 HTTP/1.1 请求响应
         * - WS：WebSocket 全双工
         * - SSE：Server-Sent Events，服务端单向推流
         */
        private enum Protocol {
            HTTP,
            WS,
            SSE
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
        /** 明文模式下的读缓冲（从连接池借出，关闭时归还）。 */
        private final ByteBuffer readBuffer;
        private final int readTimeoutMs;
        private final VKWebConfig webConfig;

        /**
         * 已读取待解析的数据缓冲。
         * Perf5：大请求处理完毕后（dataLen==0 且 dataBuf 过大）会触发缩容。
         */
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

        // WebSocket 状态
        private volatile VKWebSocketSession wsSession;
        private volatile VKWebSocketEndpoint wsEndpoint;
        private volatile boolean wsAwaitingPong;
        private volatile long wsLastPingAt;
        private volatile long wsLastPongAt;
        /**
         * wsPendingFrames/wsPendingBytes：因 Bug1 修复后只在 reactor 线程访问，
         * 不再需要 volatile（reactor 线程单线程读写，内存可见性由 pending 队列保证）。
         */
        private int wsPendingFrames;
        private int wsPendingBytes;
        private int wsFragmentOpcode = -1;
        private ByteArrayOutputStream wsFragmentBuffer;

        // SSE 状态
        /** 当前 SSE 发射器，close() 时标记为 closed。 */
        private volatile VKSseEmitter sseEmitter;

        // TLS 状态
        /** SSLEngine 实例，null 表示明文连接。 */
        private final SSLEngine sslEngine;
        /** TLS 加密输入缓冲（网络侧接收的加密字节）。 */
        private ByteBuffer sslNetIn;
        /** TLS 待发送的加密包队列。 */
        private final Deque<ByteBuffer> sslOutPackets;
        /** TLS 握手是否已完成。 */
        private boolean sslHandshakeDone = false;

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
               VKWebConfig config,
               SSLEngine sslEngine) {
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
            this.sslEngine = sslEngine;
            if (sslEngine != null) {
                // 分配 TLS 网络输入缓冲，大小为 TLS 最大记录包大小
                this.sslNetIn = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize() * 2);
                this.sslOutPackets = new ArrayDeque<>();
            } else {
                this.sslOutPackets = null;
            }
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

        // -----------------------------------------------------------------------
        // onRead: 读事件处理（TLS/明文双路径）
        // -----------------------------------------------------------------------

        void onRead() {
            if (sslEngine != null) {
                // TLS 路径：从网络读取加密数据，解密后放入 dataBuf
                onReadTls();
                return;
            }
            // 明文路径
            int read;
            try {
                read = channel.read(readBuffer);
            } catch (IOException e) {
                close();
                return;
            }

            if (read <= 0) {
                if (read < 0) {
                    // SSE 连接：客户端关闭时标记 emitter 为 closed
                    if (protocol == Protocol.SSE) {
                        VKSseEmitter e = sseEmitter;
                        if (e != null) e.markClosed();
                    }
                    close();
                }
                return;
            }

            long now = System.currentTimeMillis();
            lastRead = now;
            lastActive = now;

            readBuffer.flip();
            // Perf1：批量拷贝替代逐字节拷贝，减少循环开销
            int n = readBuffer.remaining();
            ensureCapacity(dataLen + n);
            readBuffer.get(dataBuf, dataLen, n);
            dataLen += n;
            readBuffer.clear();

            processDataBuf(now);
        }

        /**
         * TLS 读路径：从 channel 读取加密字节到 sslNetIn，解密后填充 dataBuf。
         */
        /**
         * TLS 读路径：从 channel 读取加密字节到 sslNetIn，解密后填充 dataBuf。
         *
         * 状态约束：调用间 sslNetIn 始终处于写模式（position 指向下一个待写位置，limit = capacity）。
         * doTlsHandshake() 和 decryptSslInput() 在所有退出路径上均负责将 sslNetIn compact 回写模式。
         *
         * Bug 修复：原始代码在方法开头调用 compact()，若 sslNetIn 已处于写模式（position=0, limit=capacity），
         * compact() 会将 position 设为 capacity，导致 channel.read() 返回 0（无空间），
         * 后续 flip() 产生 limit=capacity 的"垃圾"读缓冲，触发 SSLException 并关闭握手。
         */
        private void onReadTls() {
            // sslNetIn 处于写模式（调用间不变式）：直接读取新数据到 position 处
            int n;
            try {
                n = channel.read(sslNetIn);
            } catch (IOException e) {
                close();
                return;
            }
            if (n < 0) {
                if (protocol == Protocol.SSE) {
                    VKSseEmitter e = sseEmitter;
                    if (e != null) e.markClosed();
                }
                close();
                return;
            }
            if (n == 0) {
                return; // 无新数据，无需处理
            }

            // flip() 切换为读模式，limit = 已写入的总字节数（包括之前残留数据）
            sslNetIn.flip();

            if (!sslHandshakeDone) {
                // doTlsHandshake() 所有退出路径均 compact sslNetIn 回写模式
                doTlsHandshake();
                if (!sslHandshakeDone) {
                    // 握手未完成，sslNetIn 已被 doTlsHandshake compact 回写模式，等待下次读事件
                    return;
                }
                // 握手刚完成：sslNetIn 处于写模式，其中可能有握手报文后紧跟的应用数据
                sslNetIn.flip();
                if (!sslNetIn.hasRemaining()) {
                    // 无残余应用数据，重置为写模式等待下次读事件
                    sslNetIn.clear();
                    return;
                }
                // 有应用数据，继续解密（此时 sslNetIn 处于读模式）
            }

            // sslNetIn 处于读模式，解密到 dataBuf
            // decryptSslInput() 所有退出路径均 compact/clear sslNetIn 回写模式
            if (!decryptSslInput()) {
                return;
            }

            long now = System.currentTimeMillis();
            lastRead = now;
            lastActive = now;

            processDataBuf(now);
        }

        /**
         * 将 sslNetIn 中已到达的加密数据解密到 dataBuf。
         *
         * @return true 表示成功（可能解密了 0 字节）；false 表示连接应关闭
         */
        private boolean decryptSslInput() {
            while (sslNetIn.hasRemaining()) {
                int appBufSize = sslEngine.getSession().getApplicationBufferSize() + 32;
                ByteBuffer appBuf = ByteBuffer.allocate(appBufSize);
                SSLEngineResult res;
                try {
                    res = sslEngine.unwrap(sslNetIn, appBuf);
                } catch (SSLException e) {
                    close();
                    return false;
                }
                switch (res.getStatus()) {
                    case BUFFER_UNDERFLOW:
                        // 需要更多网络数据，等待下次 read 事件
                        sslNetIn.compact(); // 转回写模式，保留未消费内容
                        return true;
                    case BUFFER_OVERFLOW:
                        // 不应发生（appBuf 已足够大），跳过
                        break;
                    case CLOSED:
                        close();
                        return false;
                    case OK:
                        appBuf.flip();
                        int nDecrypted = appBuf.remaining();
                        if (nDecrypted > 0) {
                            // 注意：appBuf.get() 会修改 remaining()，需先保存
                            ensureCapacity(dataLen + nDecrypted);
                            appBuf.get(dataBuf, dataLen, nDecrypted);
                            dataLen += nDecrypted;
                        }
                        // 可能还有更多 TLS 记录，继续循环
                        continue;
                }
                break;
            }
            // sslNetIn 已读完，compact 转回写模式
            if (sslNetIn.hasRemaining()) {
                sslNetIn.compact();
            } else {
                sslNetIn.clear();
            }
            return true;
        }

        /**
         * TLS 握手状态机，在 reactor 线程中驱动。
         * 仅由 onReadTls() 和 onWrite()（TLS 路径）调用。
         *
         * 调用约定（状态不变式）：
         * - 调用前：调用方必须将 sslNetIn flip 到读模式
         * - 返回后：sslNetIn 始终处于写模式（所有退出路径均 compact）
         *
         * 握手各状态处理：
         * - NEED_WRAP: 生成握手数据并发出，若写不完则 compact + 注册 OP_WRITE 等待
         * - NEED_UNWRAP: 从 sslNetIn 解析握手数据，若不够则 compact 等待更多读事件
         * - NEED_TASK: 同步执行委托任务（短暂阻塞可接受）
         * - FINISHED/NOT_HANDSHAKING: 握手完成，compact sslNetIn 回写模式
         */
        private void doTlsHandshake() {
            try {
                outer:
                while (true) {
                    SSLEngineResult.HandshakeStatus hs = sslEngine.getHandshakeStatus();
                    switch (hs) {
                        case FINISHED:
                        case NOT_HANDSHAKING:
                            sslHandshakeDone = true;
                            // compact 回写模式，握手后可能有紧跟的应用数据留在 sslNetIn 前部
                            sslNetIn.compact();
                            break outer;

                        case NEED_WRAP: {
                            // 生成握手数据（ServerHello、证书等）
                            ByteBuffer netOut = ByteBuffer.allocate(
                                    sslEngine.getSession().getPacketBufferSize());
                            SSLEngineResult r = sslEngine.wrap(ByteBuffer.allocate(0), netOut);
                            netOut.flip();
                            if (netOut.hasRemaining()) {
                                sslOutPackets.addLast(netOut);
                            }
                            // 尝试将生成的握手数据写出
                            boolean flushed = flushSslOutPackets();
                            if (!flushed) {
                                // 缓冲区已满，注册 OP_WRITE 等待可写事件继续
                                if (key.isValid()) {
                                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                                }
                                // compact 回写模式后返回，下次由 onWrite 继续握手
                                sslNetIn.compact();
                                return;
                            }
                            // 执行可能产生的委托任务
                            if (r.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                                runDelegatedTasks();
                            }
                            break;
                        }

                        case NEED_UNWRAP: {
                            // 从 sslNetIn 中解析握手消息（ClientHello、证书等）
                            if (!sslNetIn.hasRemaining()) {
                                // 没有更多数据，compact 回写模式等待下次读事件
                                sslNetIn.compact();
                                return;
                            }
                            ByteBuffer appBuf = ByteBuffer.allocate(
                                    sslEngine.getSession().getApplicationBufferSize() + 32);
                            SSLEngineResult r = sslEngine.unwrap(sslNetIn, appBuf);
                            if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                                // 握手数据未到齐，compact 保留现有数据等待更多读事件
                                sslNetIn.compact();
                                return;
                            }
                            if (r.getStatus() == SSLEngineResult.Status.CLOSED) {
                                close();
                                return;
                            }
                            if (r.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                                runDelegatedTasks();
                            }
                            break;
                        }

                        case NEED_TASK:
                            runDelegatedTasks();
                            break;

                        default:
                            sslNetIn.compact();
                            break outer;
                    }
                }
            } catch (IOException e) {
                close();
            }
        }

        /** 同步执行 SSLEngine 委托的计算任务（密钥派生等），在 reactor 线程中内联执行。 */
        private void runDelegatedTasks() {
            Runnable task;
            while ((task = sslEngine.getDelegatedTask()) != null) {
                task.run();
            }
        }

        /**
         * 处理 dataBuf 中已积累的数据（WebSocket 帧解析或 HTTP 请求解析）。
         * 供明文路径和 TLS 解密后共同调用。
         */
        private void processDataBuf(long now) {
            // SSE 连接：客户端数据直接忽略（SSE 是单向推送，不期望客户端发数据）
            if (protocol == Protocol.SSE) {
                dataLen = 0; // 丢弃客户端发来的任何数据
                return;
            }

            if (protocol == Protocol.WS) {
                parseWebSocketFrames();
                rescheduleTimeout(now);
                return;
            }

            // HTTP 解析循环
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
                if (protocol == Protocol.SSE) {
                    return;
                }
                if (dataLen == 0) {
                    maybeShrinkDataBuf();
                    rescheduleTimeout(System.currentTimeMillis());
                    return;
                }
                // HTTP/1.1: remaining pipelined requests will be dispatched after this
                // response is enqueued, via requestContinueRead(), to preserve order.
                rescheduleTimeout(System.currentTimeMillis());
                return;
            }
        }

        // -----------------------------------------------------------------------
        // onWrite: 写事件处理（TLS/明文双路径）
        // -----------------------------------------------------------------------

        void onWrite() {
            // TLS 握手阶段：先推进握手，再处理业务写
            if (sslEngine != null && !sslHandshakeDone) {
                try {
                    if (!flushSslOutPackets()) {
                        return; // 握手数据还在写，等待下次可写事件
                    }
                } catch (IOException e) {
                    close();
                    return;
                }
                // sslNetIn 处于写模式（调用间不变式）；flip 到读模式供 doTlsHandshake 使用
                sslNetIn.flip();
                doTlsHandshake(); // 返回时 sslNetIn 处于写模式
                if (!sslHandshakeDone) {
                    // 握手未完成，正等待客户端数据（NEED_UNWRAP）
                    // sslOutPackets 已空：清除 OP_WRITE 避免空转；等待 OP_READ 触发后续握手
                    if (sslOutPackets.isEmpty() && key.isValid()) {
                        key.interestOps(SelectionKey.OP_READ);
                    }
                    return;
                }
            }

            try {
                // 明文写路径
                if (sslEngine == null) {
                    writeLoop();
                } else {
                    // TLS 写路径：先刷出已加密的包，再加密新数据写出
                    if (!flushSslOutPackets()) {
                        return;
                    }
                    writeLoopTls();
                }
            } catch (IOException e) {
                close();
            }
        }

        /**
         * 明文写循环：将 writeQueue 中的 outbound 按顺序写出到 channel。
         */
        private void writeLoop() throws IOException {
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
        }

        /**
         * TLS 写循环：将每个 outbound 的数据经过 sslEngine.wrap() 加密后写出。
         * 文件响应采用读块→wrap→写的方式（放弃 zero-copy 以保证正确性）。
         */
        private void writeLoopTls() throws IOException {
            while (true) {
                // 先刷出队列中已加密的数据包
                if (!flushSslOutPackets()) {
                    return;
                }

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

                // 加密 head 部分
                if (currentHead == null && currentOutbound.head != null) {
                    currentHead = ByteBuffer.wrap(currentOutbound.head);
                }
                if (currentHead != null) {
                    wrapAndEnqueue(currentHead);
                    currentHead = null;
                    if (!flushSslOutPackets()) {
                        return;
                    }
                }

                // 加密 body 部分
                if (currentBody == null && currentOutbound.body != null) {
                    currentBody = ByteBuffer.wrap(currentOutbound.body);
                }
                if (currentBody != null) {
                    wrapAndEnqueue(currentBody);
                    currentBody = null;
                    if (!flushSslOutPackets()) {
                        return;
                    }
                }

                // 文件响应：分块读取并加密（TLS 不支持 zero-copy sendfile）
                if (currentOutbound.file != null) {
                    ByteBuffer fileBuf = ByteBuffer.allocate(16 * 1024);
                    while (currentFilePos < currentOutbound.fileLength) {
                        fileBuf.clear();
                        long toRead = Math.min(fileBuf.capacity(), currentOutbound.fileLength - currentFilePos);
                        fileBuf.limit((int) toRead);
                        int nRead = currentOutbound.file.read(fileBuf, currentFilePos);
                        if (nRead <= 0) break;
                        currentFilePos += nRead;
                        fileBuf.flip();
                        wrapAndEnqueue(fileBuf);
                        if (!flushSslOutPackets()) {
                            return;
                        }
                    }
                    if (currentFilePos >= currentOutbound.fileLength) {
                        try {
                            currentOutbound.file.close();
                        } catch (IOException ignore) {
                        }
                    }
                }

                if (currentOutbound.wsFrame) {
                    wsPendingFrames = Math.max(0, wsPendingFrames - 1);
                    wsPendingBytes = Math.max(0, wsPendingBytes - (int) currentOutbound.totalBytes());
                }
                currentOutbound = null;
            }
        }

        /**
         * 将明文 ByteBuffer 经 SSLEngine 加密，结果放入 sslOutPackets 队列。
         */
        private void wrapAndEnqueue(ByteBuffer plaintext) throws IOException {
            while (plaintext.hasRemaining()) {
                ByteBuffer netOut = ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                try {
                    sslEngine.wrap(plaintext, netOut);
                } catch (SSLException e) {
                    throw new IOException("TLS wrap failed", e);
                }
                netOut.flip();
                if (netOut.hasRemaining()) {
                    sslOutPackets.addLast(netOut);
                }
            }
        }

        /**
         * 将 sslOutPackets 中已加密的数据包写入 channel。
         *
         * @return true 表示全部写完；false 表示缓冲区满，需等待下次 OP_WRITE
         */
        private boolean flushSslOutPackets() throws IOException {
            while (!sslOutPackets.isEmpty()) {
                ByteBuffer packet = sslOutPackets.peekFirst();
                channel.write(packet);
                if (packet.hasRemaining()) {
                    // channel 写缓冲已满，注册 OP_WRITE 等待
                    if (key.isValid()) {
                        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    }
                    return false;
                }
                sslOutPackets.pollFirst();
            }
            return true;
        }

        // -----------------------------------------------------------------------
        // onTimeout / rescheduleTimeout
        // -----------------------------------------------------------------------

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
            if (protocol == Protocol.SSE) {
                // SSE：emitter 已关闭则关闭连接；否则继续保持长驻
                VKSseEmitter e = sseEmitter;
                if (e == null || !e.isOpen()) {
                    close();
                } else {
                    rescheduleTimeout(now);
                }
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
            if (protocol == Protocol.SSE) {
                // SSE 连接每 30s 检查一次 emitter 状态
                timeoutToken = reactor.timer.schedule(this, now + 30_000);
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

        // -----------------------------------------------------------------------
        // close: 连接关闭清理
        // -----------------------------------------------------------------------

        void close() {
            if (closed) {
                return;
            }
            boolean wsWasOpen = protocol == Protocol.WS;
            boolean sseWasOpen = protocol == Protocol.SSE;
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
            VKOutbound pendingOut;
            while ((pendingOut = writeQueue.poll()) != null) {
                closeOutbound(pendingOut);
            }
            if (multipartCtx != null) {
                multipartCtx.abort();
                multipartCtx = null;
            }
            if (wsWasOpen && wsEndpoint != null && wsSession != null) {
                server.unregisterWebSocketSession(wsEndpoint.path(), wsSession);
                try {
                    wsEndpoint.handler().onClose(wsSession, 1006, "Abnormal Closure");
                } catch (Throwable ignore) {
                }
            }
            // SSE 关闭时：标记 emitter，防止业务代码继续向已关闭连接发数据
            if (sseWasOpen) {
                VKSseEmitter e = sseEmitter;
                if (e != null) {
                    e.markClosed();
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

        // -----------------------------------------------------------------------
        // dispatch: HTTP 请求派发到 worker 线程
        // -----------------------------------------------------------------------

        private void dispatch(VKRequest req) {
            inFlight.incrementAndGet();
            boolean accepted = workers.submit(() -> {
                long startNs = System.nanoTime();
                boolean isError = false;
                VKResponse res = new VKResponse();
                try {
                    ensureTraceId(req, res);
                    if (tryUpgradeWebSocket(req)) {
                        return;
                    }
                    var match = router.match(req.method(), req.path());
                    // Bug fix: 中间件必须在所有请求（包括 404）上执行，使 CORS 等中间件能拦截 OPTIONS preflight
                    VKHandler finalHandler;
                    if (match == null || match.handler() == null) {
                        finalHandler = (r, s) -> s.status(404).text("Not Found");
                    } else {
                        req.setParams(match.params());
                        if (!server.tryRateLimit(req, match, res)) {
                            return;
                        }
                        finalHandler = match.handler();
                    }
                    if (middlewares.isEmpty()) {
                        finalHandler.handle(req, res);
                    } else {
                        VKChain chain = new VKChain(middlewares, finalHandler);
                        chain.next(req, res);
                    }
                } catch (VKMultipartParseException e) {
                    res.status(e.status()).text(e.getMessage());
                    isError = true;
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
                    isError = true;
                } finally {
                    if (protocol == Protocol.WS) {
                        inFlight.decrementAndGet();
                        reactor.requestReschedule(this);
                        return;
                    }

                    // SSE 响应检测：handler 设置了 sseResponse() 则切换到 SSE 协议
                    if (res.isSse()) {
                        byte[] head = VKHttpWriter.writeSseHead(res, req.keepAlive());
                        enqueueResponse(VKOutbound.fromHeadBytes(head), false);
                        Consumer<VKSseEmitter> consumer = res.sseConsumer();
                        // 切换协议必须在 reactor 线程中执行，通过 pending 队列投递
                        reactor.pending.add(() -> enableSse(consumer));
                        reactor.selector.wakeup();
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

                    long costNs = System.nanoTime() - startNs;
                    long costMs = costNs / 1_000_000;
                    // 更新 metrics：请求数和响应时间
                    server.recordRequest(costNs, isError || res.status() >= 500);

                    logAccess(req, res.status(), out.totalBytes(), costMs);

                    req.cleanupUploads();
                    inFlight.decrementAndGet();
                    reactor.requestContinueRead(this);
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

        // -----------------------------------------------------------------------
        // SSE 支持
        // -----------------------------------------------------------------------

        /**
         * 切换连接到 SSE 协议并创建 VKSseEmitter。
         * 必须在 reactor 线程中调用（通过 pending 队列投递）。
         *
         * @param consumer 业务代码的 SSE handler，接收 emitter 后可保存用于后续推送
         */
        private void enableSse(Consumer<VKSseEmitter> consumer) {
            if (closed) {
                return;
            }
            protocol = Protocol.SSE;
            VKSseEmitter emitter = new VKSseEmitter(
                    // sender：将 SSE 事件字节投递到 reactor 写队列（跨线程安全）
                    bytes -> {
                        if (!closed) {
                            enqueueResponse(VKOutbound.fromHeadBytes(bytes), false);
                        }
                    },
                    // closer：通过 requestClose 在 reactor 线程安全地关闭连接
                    () -> reactor.requestClose(this)
            );
            this.sseEmitter = emitter;
            rescheduleTimeout(System.currentTimeMillis());
            // 在 reactor 线程中调用 consumer（可能立即发送初始事件）
            try {
                consumer.accept(emitter);
            } catch (Throwable t) {
                emitter.markClosed();
                close();
            }
        }

        // -----------------------------------------------------------------------
        // WebSocket 支持
        // -----------------------------------------------------------------------

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
            String wsKey = req.header("sec-websocket-key");
            if (conn == null || !conn.toLowerCase().contains("upgrade")
                    || wsKey == null || wsKey.isEmpty()
                    || version == null || !"13".equals(version)) {
                respondError(400, "Bad WebSocket Request", true);
                return true;
            }
            VKWsHandshakeContext hsContext = new VKWsHandshakeContext(
                    req.path(),
                    req.traceId(),
                    req.header("sec-websocket-protocol"),
                    req.remoteAddress(),
                    req.headers(),
                    req.queryParams()
            );
            VKWsAuthResult authResult;
            try {
                endpoint.config().getHandshakeHook().beforeUpgrade(hsContext);
                authResult = endpoint.config().getHandshakeAuthenticator().authenticate(hsContext);
            } catch (Throwable e) {
                authResult = VKWsAuthResult.reject(401, "WebSocket Unauthorized");
            }
            if (authResult == null) {
                authResult = VKWsAuthResult.allow();
            }
            if (!authResult.allowed()) {
                try {
                    endpoint.config().getHandshakeHook().onReject(hsContext, authResult);
                } catch (Throwable ignore) {
                }
                int status = authResult.rejectStatus() <= 0 ? 401 : authResult.rejectStatus();
                respondError(status, authResult.rejectReason(), true);
                return true;
            }
            String accept = websocketAccept(wsKey);
            byte[] head = ("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "X-Trace-Id: " + (req.traceId() == null ? "" : req.traceId()) + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII);
            enqueueResponse(VKOutbound.fromHeadBytes(head), false);
            enableWebSocket(req, endpoint, hsContext, authResult);
            return true;
        }

        private void enableWebSocket(VKRequest req, VKWebSocketEndpoint endpoint,
                                     VKWsHandshakeContext hsContext, VKWsAuthResult authResult) {
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
                    // Bug1修复：通过 scheduleSend 在 reactor 线程内单线程发送，消除竞态
                    frame -> reactor.scheduleSend(this, frame),
                    // Bug2修复：通过 requestClose 在 reactor 线程内关闭，消除与 onRead 的竞态
                    () -> reactor.requestClose(this),
                    server.wsRegistry(),
                    authResult.attributes()
            );
            server.registerWebSocketSession(endpoint.path(), wsSession);
            try {
                endpoint.config().getHandshakeHook().afterAuth(wsSession, hsContext);
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

        /**
         * 发送 WebSocket 帧（仅在 reactor 线程调用）。
         * Bug1修复：通过 scheduleSend 保证此方法只在 reactor 线程执行，wsPendingFrames/wsPendingBytes 无竞态。
         */
        void sendWsFrame(VKWsFrame frame) {
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
            // Bug3修复：拆分守卫条件，避免 HTTP 连接误调用 close()
            if (closed) {
                return;
            }
            if (protocol != Protocol.WS) {
                return;
            }
            byte[] reasonBytes = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + reasonBytes.length];
            payload[0] = (byte) ((code >> 8) & 0xFF);
            payload[1] = (byte) (code & 0xFF);
            System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
            sendWsFrame(VKWsFrame.close(payload));
            if (wsEndpoint != null && wsSession != null) {
                server.unregisterWebSocketSession(wsEndpoint.path(), wsSession);
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

        /**
         * Perf4：使用 ThreadLocal 缓存的 MessageDigest 计算 WebSocket Accept 值，
         * 避免每次握手都调用 getInstance（有锁竞争）。
         */
        private String websocketAccept(String key) {
            MessageDigest md = SHA1_CACHE.get();
            md.reset(); // ThreadLocal 实例复用前必须 reset
            byte[] digest = md.digest(
                    (key.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                            .getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        }

        // -----------------------------------------------------------------------
        // 工具方法
        // -----------------------------------------------------------------------

        private void respondError(int status, String msg, boolean doClose) {
            VKResponse res = new VKResponse().status(status).text(msg == null ? "" : msg);
            enqueueResponse(VKOutbound.from(res, false), doClose);
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

        /**
         * Perf5：dataBuf 在大请求处理完后缩容，释放多余内存。
         * 触发条件：dataLen==0 且 dataBuf 超过初始大小（readBufferSize * 2）。
         */
        private void maybeShrinkDataBuf() {
            int initial = webConfig.getReadBufferSize() * 2;
            if (dataLen == 0 && dataBuf.length > initial) {
                dataBuf = new byte[initial];
            }
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

    // ==========================================================================
    // VKOutbound - 待发送的响应数据
    // ==========================================================================
    static final class VKOutbound {
        final byte[] head;
        final byte[] body;
        final java.nio.channels.FileChannel file;
        final long fileLength;
        final boolean wsFrame;

        private VKOutbound(byte[] head, byte[] body, java.nio.channels.FileChannel file,
                           long fileLength, boolean wsFrame) {
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
            if (head != null) n += head.length;
            if (body != null) n += body.length;
            n += fileLength;
            return n;
        }
    }
}
