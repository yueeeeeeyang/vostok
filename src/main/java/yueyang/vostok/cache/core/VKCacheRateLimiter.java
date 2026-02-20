package yueyang.vostok.cache.core;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class VKCacheRateLimiter {
    private final int qps;
    private final AtomicLong second = new AtomicLong(0);
    private final AtomicInteger used = new AtomicInteger(0);

    VKCacheRateLimiter(int qps) {
        this.qps = Math.max(0, qps);
    }

    boolean allow() {
        if (qps <= 0) {
            return true;
        }
        long nowSec = System.currentTimeMillis() / 1000;
        long prev = second.get();
        if (prev != nowSec && second.compareAndSet(prev, nowSec)) {
            used.set(0);
        }
        return used.incrementAndGet() <= qps;
    }
}
