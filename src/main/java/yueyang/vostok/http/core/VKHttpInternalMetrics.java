package yueyang.vostok.http.core;

import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.exception.VKHttpErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class HttpMetrics {
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong successCalls = new AtomicLong();
    private final AtomicLong failedCalls = new AtomicLong();
    private final AtomicLong retriedCalls = new AtomicLong();
    private final AtomicLong timeoutCalls = new AtomicLong();
    private final AtomicLong networkErrorCalls = new AtomicLong();
    private final AtomicLong streamOpens = new AtomicLong();
    private final AtomicLong streamCloses = new AtomicLong();
    private final AtomicLong streamErrors = new AtomicLong();
    private final AtomicLong sseEvents = new AtomicLong();
    private final AtomicLong totalCostMs = new AtomicLong();
    private final ConcurrentHashMap<Integer, AtomicLong> statusCounts = new ConcurrentHashMap<>();

    void recordStreamOpen() {
        streamOpens.incrementAndGet();
    }

    void recordStreamClose() {
        streamCloses.incrementAndGet();
    }

    void recordStreamError() {
        streamErrors.incrementAndGet();
    }

    void recordSseEvent() {
        sseEvents.incrementAndGet();
    }

    void recordRetry() {
        retriedCalls.incrementAndGet();
    }

    void recordResponse(int status, long costMs) {
        totalCalls.incrementAndGet();
        totalCostMs.addAndGet(Math.max(0, costMs));
        if (status >= 200 && status < 300) {
            successCalls.incrementAndGet();
        } else {
            failedCalls.incrementAndGet();
        }
        statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();
    }

    void recordFailure(VKHttpErrorCode code, long costMs) {
        totalCalls.incrementAndGet();
        failedCalls.incrementAndGet();
        totalCostMs.addAndGet(Math.max(0, costMs));
        if (code == VKHttpErrorCode.CONNECT_TIMEOUT
                || code == VKHttpErrorCode.READ_TIMEOUT
                || code == VKHttpErrorCode.TOTAL_TIMEOUT
                || code == VKHttpErrorCode.TIMEOUT) {
            timeoutCalls.incrementAndGet();
        }
        if (code == VKHttpErrorCode.NETWORK_ERROR) {
            networkErrorCalls.incrementAndGet();
        }
    }

    VKHttpMetrics snapshot() {
        Map<Integer, Long> statuses = new LinkedHashMap<>();
        List<Integer> keys = new ArrayList<>(statusCounts.keySet());
        keys.sort(Integer::compareTo);
        for (Integer status : keys) {
            statuses.put(status, statusCounts.get(status).get());
        }
        return new VKHttpMetrics(
                totalCalls.get(),
                successCalls.get(),
                failedCalls.get(),
                retriedCalls.get(),
                timeoutCalls.get(),
                networkErrorCalls.get(),
                streamOpens.get(),
                streamCloses.get(),
                streamErrors.get(),
                sseEvents.get(),
                totalCostMs.get(),
                statuses
        );
    }

    void reset() {
        totalCalls.set(0);
        successCalls.set(0);
        failedCalls.set(0);
        retriedCalls.set(0);
        timeoutCalls.set(0);
        networkErrorCalls.set(0);
        streamOpens.set(0);
        streamCloses.set(0);
        streamErrors.set(0);
        sseEvents.set(0);
        totalCostMs.set(0);
        statusCounts.clear();
    }
}
