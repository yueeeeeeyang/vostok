package yueyang.vostok.cache;

public record VKCachePoolMetrics(String cacheName,
                                 int total,
                                 int active,
                                 int idle,
                                 long borrowTimeouts,
                                 long leakedConnections,
                                 long evictedConnections,
                                 long rejectedByRateLimit) {
}
