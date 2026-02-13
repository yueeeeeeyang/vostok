package yueyang.vostok.web.core;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.http.VKHttpParseException;
import yueyang.vostok.web.http.VKHttpParser;
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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class VKReactor implements Runnable {
    private final VKWebServer server;
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final VKWorkerPool workers;
    private final VKRouter router;
    private final List<VKMiddleware> middlewares;
    private final VKErrorHandler errorHandler;
    private final VKHttpParser parser;
    private final VKBufferPool bufferPool;
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    VKReactor(VKWebServer server,
              Selector selector,
              ServerSocketChannel serverChannel,
              VKWorkerPool workers,
              VKRouter router,
              List<VKMiddleware> middlewares,
              VKErrorHandler errorHandler,
              VKHttpParser parser,
              VKBufferPool bufferPool) {
        this.server = server;
        this.selector = selector;
        this.serverChannel = serverChannel;
        this.workers = workers;
        this.router = router;
        this.middlewares = middlewares;
        this.errorHandler = errorHandler;
        this.parser = parser;
        this.bufferPool = bufferPool;
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
                        if (key.isAcceptable()) {
                            onAccept();
                        } else {
                            VKConn conn = (VKConn) key.attachment();
                            if (key.isReadable()) {
                                conn.onRead();
                            }
                            if (key.isWritable()) {
                                conn.onWrite();
                            }
                        }
                    } catch (java.nio.channels.CancelledKeyException e) {
                        // key cancelled during processing, ignore
                    }
                }
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

    private void onAccept() {
        try {
            SocketChannel channel = serverChannel.accept();
            if (channel == null) {
                return;
            }
            channel.configureBlocking(false);
            SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            VKConn conn = new VKConn(server, channel, key, parser, bufferPool, workers, router, middlewares, errorHandler, this);
            key.attach(conn);
        } catch (IOException ignore) {
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

        VKConn(VKWebServer server,
               SocketChannel channel,
               SelectionKey key,
               VKHttpParser parser,
               VKBufferPool bufferPool,
               VKWorkerPool workers,
               VKRouter router,
               List<VKMiddleware> middlewares,
               VKErrorHandler errorHandler,
               VKReactor reactor) {
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
            this.readBuffer = bufferPool.acquire();
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
            readBuffer.flip();
            ensureCapacity(dataLen + readBuffer.remaining());
            while (readBuffer.hasRemaining()) {
                dataBuf[dataLen++] = readBuffer.get();
            }
            readBuffer.clear();

            while (true) {
                VKHttpParser.ParsedRequest parsed;
                try {
                    parsed = parser.parse(dataBuf, dataLen, remoteAddress());
                } catch (VKHttpParseException e) {
                    respondError(e.status(), e.getMessage(), true);
                    return;
                } catch (RuntimeException e) {
                    respondError(400, "Bad Request", true);
                    return;
                }

                if (parsed == null) {
                    return;
                }

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
            try {
                key.cancel();
            } catch (Exception ignore) {
            }
            try {
                channel.close();
            } catch (IOException ignore) {
            }
            bufferPool.release(readBuffer);
        }

        private void dispatch(VKRequest req) {
            workers.submit(() -> {
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
        }

        private void respondError(int status, String msg, boolean close) {
            VKResponse res = new VKResponse().status(status).text(msg == null ? "" : msg);
            byte[] out = VKHttpWriter.write(res, false);
            enqueueResponse(out, close);
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
    }
}
