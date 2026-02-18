package yueyang.vostok.web.rate;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class VKRateLimiter {
    private final VKRateLimitConfig config;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong cleanupTick = new AtomicLong();

    public VKRateLimiter(VKRateLimitConfig config) {
        this.config = config == null ? new VKRateLimitConfig() : config;
    }

    public boolean tryAcquire(VKRequest req) {
        return tryAcquireDecision(req).allowed();
    }

    public Decision tryAcquireDecision(VKRequest req) {
        String key = resolveKey(req);
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(config.getCapacity(), now));
        boolean allowed;
        synchronized (bucket) {
            refill(bucket, now);
            if (bucket.tokens > 0) {
                bucket.tokens--;
                allowed = true;
            } else {
                allowed = false;
            }
            bucket.lastSeen = now;
        }
        cleanupIfNeeded(now);
        return new Decision(allowed, key, config.getKeyStrategy(), config.getRejectStatus());
    }

    public void applyRejected(VKResponse res) {
        res.status(config.getRejectStatus()).text(config.getRejectBody());
    }

    private void refill(Bucket bucket, long now) {
        long elapsed = now - bucket.lastRefillAt;
        if (elapsed < config.getRefillPeriodMs()) {
            return;
        }
        long intervals = elapsed / config.getRefillPeriodMs();
        long add = intervals * (long) config.getRefillTokens();
        if (add <= 0) {
            return;
        }
        bucket.tokens = Math.min(config.getCapacity(), bucket.tokens + add);
        bucket.lastRefillAt += intervals * config.getRefillPeriodMs();
    }

    private String resolveKey(VKRequest req) {
        if (req == null) {
            return "-";
        }
        VKRateLimitKeyStrategy strategy = config.getKeyStrategy();
        if (strategy == VKRateLimitKeyStrategy.TRACE_ID) {
            String tid = req.traceId();
            return tid == null || tid.isEmpty() ? "-" : tid;
        }
        if (strategy == VKRateLimitKeyStrategy.HEADER) {
            String v = req.header(config.getHeaderName());
            return v == null || v.isEmpty() ? "-" : v;
        }
        if (strategy == VKRateLimitKeyStrategy.CUSTOM && config.getCustomKeyResolver() != null) {
            String v = config.getCustomKeyResolver().apply(req);
            return v == null || v.isEmpty() ? "-" : v;
        }
        if (req.remoteAddress() != null && req.remoteAddress().getAddress() != null) {
            return req.remoteAddress().getAddress().getHostAddress();
        }
        return "-";
    }

    private void cleanupIfNeeded(long now) {
        long tick = cleanupTick.incrementAndGet();
        if ((tick & 1023) != 0) {
            return;
        }
        long staleMs = Math.max(config.getRefillPeriodMs() * 20L, 60_000L);
        for (Map.Entry<String, Bucket> e : buckets.entrySet()) {
            Bucket bucket = e.getValue();
            if (now - bucket.lastSeen > staleMs) {
                buckets.remove(e.getKey(), bucket);
            }
        }
    }

    private static final class Bucket {
        long tokens;
        long lastRefillAt;
        volatile long lastSeen;

        Bucket(long tokens, long now) {
            this.tokens = tokens;
            this.lastRefillAt = now;
            this.lastSeen = now;
        }
    }

    public record Decision(boolean allowed, String key, VKRateLimitKeyStrategy strategy, int rejectStatus) {
    }
}
