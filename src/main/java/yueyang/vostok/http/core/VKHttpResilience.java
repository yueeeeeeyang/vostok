package yueyang.vostok.http.core;

import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.util.ArrayDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class ResilienceRuntime {
    final TokenBucketLimiter rateLimiter;
    final Bulkhead bulkhead;
    final CircuitBreaker circuitBreaker;

    ResilienceRuntime(TokenBucketLimiter rateLimiter, Bulkhead bulkhead, CircuitBreaker circuitBreaker) {
        this.rateLimiter = rateLimiter;
        this.bulkhead = bulkhead;
        this.circuitBreaker = circuitBreaker;
    }
}

final class TokenBucketLimiter {
    private final int qps;
    private final int burst;
    // 优化5：使用 currentTimeMillis + long 运算替代 nanoTime + 浮点运算，对 QPS 限制（毫秒精度）更高效
    private double tokens;
    private long lastRefillMs;

    TokenBucketLimiter(int qps, int burst) {
        this.qps = Math.max(1, qps);
        this.burst = Math.max(1, burst);
        this.tokens = this.burst;
        this.lastRefillMs = System.currentTimeMillis();
    }

    synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillMs;
        if (elapsed > 0) {
            // 按照已过去的毫秒数补充令牌，qps/1000 即每毫秒令牌产生速率
            tokens = Math.min(burst, tokens + (double) elapsed * qps / 1000.0);
            lastRefillMs = now;
        }
        if (tokens >= 1.0d) {
            tokens -= 1.0d;
            return true;
        }
        return false;
    }
}

final class Bulkhead {
    private final Semaphore permits;
    private final Semaphore queuePermits;
    private final long acquireTimeoutMs;

    Bulkhead(int maxConcurrent, int queueSize, long acquireTimeoutMs) {
        this.permits = new Semaphore(Math.max(1, maxConcurrent));
        this.queuePermits = queueSize > 0 ? new Semaphore(queueSize) : null;
        this.acquireTimeoutMs = Math.max(0, acquireTimeoutMs);
    }

    void acquire() {
        try {
            if (queuePermits == null) {
                if (!permits.tryAcquire()) {
                    throw new VKHttpException(VKHttpErrorCode.BULKHEAD_REJECTED, "Http bulkhead full");
                }
                return;
            }
            if (!queuePermits.tryAcquire()) {
                throw new VKHttpException(VKHttpErrorCode.BULKHEAD_REJECTED, "Http bulkhead queue full");
            }
            try {
                boolean ok = acquireTimeoutMs <= 0
                        ? permits.tryAcquire()
                        : permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
                if (!ok) {
                    throw new VKHttpException(VKHttpErrorCode.BULKHEAD_REJECTED, "Http bulkhead acquire timeout");
                }
            } finally {
                queuePermits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VKHttpException(VKHttpErrorCode.BULKHEAD_REJECTED, "Http bulkhead interrupted", e);
        }
    }

    void release() {
        permits.release();
    }
}

final class CircuitBreaker {
    private final int windowSize;
    private final int minCalls;
    private final int failureRateThreshold;
    private final long openWaitMs;
    private final int halfOpenMaxCalls;
    private final Object lock = new Object();
    private final ArrayDeque<Boolean> outcomes;

    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private volatile long openUntilMs;
    private volatile int halfOpenAttempted;
    private volatile int halfOpenFailures;
    // Bug1修复：增加成功计数器，只要所有已发起探测全部成功即关闭（无需等配额耗尽）
    private volatile int halfOpenSuccesses;

    CircuitBreaker(int windowSize, int minCalls, int failureRateThreshold, long openWaitMs, int halfOpenMaxCalls) {
        this.windowSize = Math.max(1, windowSize);
        this.minCalls = Math.max(1, minCalls);
        this.failureRateThreshold = Math.min(100, Math.max(1, failureRateThreshold));
        this.openWaitMs = Math.max(100, openWaitMs);
        this.halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
        this.outcomes = new ArrayDeque<>(this.windowSize);
    }

    void beforeCall() {
        CircuitState s = state.get();
        long now = System.currentTimeMillis();
        if (s == CircuitState.OPEN) {
            if (now < openUntilMs) {
                throw new VKHttpException(VKHttpErrorCode.CIRCUIT_OPEN, "Http circuit is open");
            }
            synchronized (lock) {
                if (state.get() == CircuitState.OPEN && now >= openUntilMs) {
                    state.set(CircuitState.HALF_OPEN);
                    halfOpenAttempted = 0;
                    halfOpenFailures = 0;
                    halfOpenSuccesses = 0;  // Bug1：重置成功计数器
                }
            }
            s = state.get();
        }
        if (s == CircuitState.HALF_OPEN) {
            synchronized (lock) {
                if (halfOpenAttempted >= halfOpenMaxCalls) {
                    throw new VKHttpException(VKHttpErrorCode.CIRCUIT_OPEN, "Http circuit half-open quota exhausted");
                }
                halfOpenAttempted++;
            }
        }
    }

    void onSuccess() {
        CircuitState s = state.get();
        if (s == CircuitState.CLOSED) {
            record(false);
            return;
        }
        if (s == CircuitState.HALF_OPEN) {
            synchronized (lock) {
                if (state.get() != CircuitState.HALF_OPEN) {
                    return;
                }
                halfOpenSuccesses++;
                // Bug1修复：只要所有已发起的探测全部成功（且至少1次），立即关闭
                // 不需要等待配额 halfOpenMaxCalls 耗尽
                if (halfOpenSuccesses == halfOpenAttempted && halfOpenAttempted > 0 && halfOpenFailures == 0) {
                    state.set(CircuitState.CLOSED);
                    outcomes.clear();
                }
            }
        }
    }

    void onFailure() {
        CircuitState s = state.get();
        if (s == CircuitState.CLOSED) {
            record(true);
            return;
        }
        if (s == CircuitState.HALF_OPEN) {
            synchronized (lock) {
                if (state.get() != CircuitState.HALF_OPEN) {
                    return;
                }
                halfOpenFailures++;
                open();
            }
        }
    }

    private void record(boolean failure) {
        synchronized (lock) {
            outcomes.addLast(failure);
            while (outcomes.size() > windowSize) {
                outcomes.removeFirst();
            }
            if (outcomes.size() < minCalls) {
                return;
            }
            int fails = 0;
            for (Boolean it : outcomes) {
                if (Boolean.TRUE.equals(it)) {
                    fails++;
                }
            }
            int rate = (int) ((fails * 100L) / outcomes.size());
            if (rate >= failureRateThreshold) {
                open();
            }
        }
    }

    private void open() {
        state.set(CircuitState.OPEN);
        openUntilMs = System.currentTimeMillis() + openWaitMs;
    }
}

enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
