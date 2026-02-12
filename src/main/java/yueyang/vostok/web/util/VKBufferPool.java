package yueyang.vostok.web.util;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public final class VKBufferPool {
    private final int bufferSize;
    private final int maxPoolSize;
    private final Deque<ByteBuffer> pool = new ArrayDeque<>();

    public VKBufferPool(int bufferSize, int maxPoolSize) {
        this.bufferSize = bufferSize;
        this.maxPoolSize = maxPoolSize;
    }

    public synchronized ByteBuffer acquire() {
        ByteBuffer buf = pool.pollFirst();
        if (buf == null) {
            return ByteBuffer.allocate(bufferSize);
        }
        buf.clear();
        return buf;
    }

    public synchronized void release(ByteBuffer buf) {
        if (buf == null) {
            return;
        }
        if (pool.size() >= maxPoolSize) {
            return;
        }
        buf.clear();
        pool.addLast(buf);
    }
}
