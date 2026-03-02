package yueyang.vostok.http.core;

import yueyang.vostok.http.VKHttpMetrics;
import yueyang.vostok.http.exception.VKHttpErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单客户端维度的轻量 Metrics 计数器（被 VKHttpInternalMetrics 的 perClient 使用）。
 * 方法签名不含 clientName，因为每个实例已对应一个具体客户端。
 */
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

    void recordStreamOpen() { streamOpens.incrementAndGet(); }
    void recordStreamClose() { streamCloses.incrementAndGet(); }
    void recordStreamError() { streamErrors.incrementAndGet(); }
    void recordSseEvent() { sseEvents.incrementAndGet(); }
    void recordRetry() { retriedCalls.incrementAndGet(); }

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
                totalCalls.get(), successCalls.get(), failedCalls.get(),
                retriedCalls.get(), timeoutCalls.get(), networkErrorCalls.get(),
                streamOpens.get(), streamCloses.get(), streamErrors.get(),
                sseEvents.get(), totalCostMs.get(), statuses
        );
    }
}

/**
 * HTTP 模块内部 Metrics 计数器。
 * <p>
 * 扩展5：增加 perClient 映射，同时维护全局计数和各命名客户端的独立计数。
 * 所有 record* 方法均接受 clientName 参数（null 表示只记录全局）。
 */
final class VKHttpInternalMetrics {

    // -----------------------------------------------------------------------
    // 全局计数器
    // -----------------------------------------------------------------------
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

    // -----------------------------------------------------------------------
    // 扩展5：每客户端独立计数
    // -----------------------------------------------------------------------
    private final ConcurrentHashMap<String, HttpMetrics> perClient = new ConcurrentHashMap<>();

    /** 获取（或懒创建）指定客户端的 HttpMetrics 实例。null/空名称直接返回 null。 */
    private HttpMetrics forClient(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            return null;
        }
        return perClient.computeIfAbsent(clientName, k -> new HttpMetrics());
    }

    // -----------------------------------------------------------------------
    // 流相关记录
    // -----------------------------------------------------------------------

    void recordStreamOpen(String clientName) {
        streamOpens.incrementAndGet();
        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordStreamOpen();
    }

    void recordStreamClose(String clientName) {
        streamCloses.incrementAndGet();
        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordStreamClose();
    }

    void recordStreamError(String clientName) {
        streamErrors.incrementAndGet();
        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordStreamError();
    }

    void recordSseEvent(String clientName) {
        sseEvents.incrementAndGet();
        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordSseEvent();
    }

    // -----------------------------------------------------------------------
    // 重试、响应、失败记录
    // -----------------------------------------------------------------------

    void recordRetry(String clientName) {
        retriedCalls.incrementAndGet();
        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordRetry();
    }

    void recordResponse(String clientName, int status, long costMs) {
        totalCalls.incrementAndGet();
        totalCostMs.addAndGet(Math.max(0, costMs));
        if (status >= 200 && status < 300) {
            successCalls.incrementAndGet();
        } else {
            failedCalls.incrementAndGet();
        }
        statusCounts.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();

        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordResponse(status, costMs);
    }

    void recordFailure(String clientName, VKHttpErrorCode code, long costMs) {
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

        HttpMetrics cm = forClient(clientName);
        if (cm != null) cm.recordFailure(code, costMs);
    }

    // -----------------------------------------------------------------------
    // 快照
    // -----------------------------------------------------------------------

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

    /** 扩展5：返回指定客户端的 Metrics 快照，未找到时返回 empty()。 */
    VKHttpMetrics snapshot(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            return snapshot();
        }
        HttpMetrics cm = perClient.get(clientName);
        return cm != null ? cm.snapshot() : VKHttpMetrics.empty();
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
        perClient.clear();
    }
}
