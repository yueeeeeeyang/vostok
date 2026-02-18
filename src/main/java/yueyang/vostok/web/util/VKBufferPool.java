package yueyang.vostok.web.util;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public final class VKBufferPool {
    private static final int LOCAL_POOL_MAX = 16;

    private final int bufferSize;
    private final int maxPoolSize;
    private final Deque<ByteBuffer> globalPool = new ArrayDeque<>();
    private final ThreadLocal<Deque<ByteBuffer>> localPool = ThreadLocal.withInitial(ArrayDeque::new);

    public VKBufferPool(int bufferSize, int maxPoolSize) {
        this.bufferSize = bufferSize;
        this.maxPoolSize = maxPoolSize;
    }

    public ByteBuffer acquire() {
        Deque<ByteBuffer> local = localPool.get();
        ByteBuffer buf = local.pollFirst();
        if (buf != null) {
            buf.clear();
            return buf;
        }
        synchronized (globalPool) {
            buf = globalPool.pollFirst();
        }
        if (buf == null) {
            return ByteBuffer.allocate(bufferSize);
        }
        buf.clear();
        return buf;
    }

    public void release(ByteBuffer buf) {
        if (buf == null) {
            return;
        }
        buf.clear();

        Deque<ByteBuffer> local = localPool.get();
        if (local.size() < LOCAL_POOL_MAX) {
            local.addFirst(buf);
            return;
        }

        synchronized (globalPool) {
            if (globalPool.size() < maxPoolSize) {
                globalPool.addLast(buf);
            }
        }
    }
}
