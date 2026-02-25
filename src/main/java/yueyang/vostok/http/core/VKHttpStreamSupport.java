package yueyang.vostok.http.core;

import yueyang.vostok.http.VKHttpSseEvent;
import yueyang.vostok.http.VKHttpStreamSession;
import yueyang.vostok.http.exception.VKHttpErrorCode;
import yueyang.vostok.http.exception.VKHttpException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class StreamSessionImpl implements VKHttpStreamSession {
    private final InputStream input;
    private final Bulkhead bulkhead;
    private final CircuitBreaker circuitBreaker;
    private final boolean statusFailure;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final AtomicBoolean idleTimeout = new AtomicBoolean(false);
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private final AtomicLong lastActiveAt = new AtomicLong(System.currentTimeMillis());

    StreamSessionImpl(InputStream input, Bulkhead bulkhead, CircuitBreaker circuitBreaker, boolean statusFailure) {
        this.input = input;
        this.bulkhead = bulkhead;
        this.circuitBreaker = circuitBreaker;
        this.statusFailure = statusFailure;
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(input);
    }

    @Override
    public void close() {
        cancel();
    }

    void complete(boolean ignored) {
        if (open.compareAndSet(true, false)) {
            releaseBulkhead();
            if (circuitBreaker != null) {
                if (statusFailure) {
                    circuitBreaker.onFailure();
                } else {
                    circuitBreaker.onSuccess();
                }
            }
            done.complete(null);
        }
    }

    void fail(VKHttpException error, boolean ignored) {
        if (open.compareAndSet(true, false)) {
            releaseBulkhead();
            if (circuitBreaker != null) {
                circuitBreaker.onFailure();
            }
            done.completeExceptionally(error == null
                    ? new VKHttpException(VKHttpErrorCode.STREAM_CLOSED, "HTTP stream failed")
                    : error);
        }
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    void touch() {
        lastActiveAt.set(System.currentTimeMillis());
    }

    long lastActiveAt() {
        return lastActiveAt.get();
    }

    InputStream input() {
        return input;
    }

    void markIdleTimeout() {
        idleTimeout.set(true);
    }

    boolean idleTimeoutTriggered() {
        return idleTimeout.get();
    }

    void await() {
        try {
            done.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VKHttpException(VKHttpErrorCode.STREAM_CLOSED, "HTTP stream waiting interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof VKHttpException ex) {
                throw ex;
            }
            throw new VKHttpException(VKHttpErrorCode.STREAM_CLOSED, "HTTP stream failed", cause);
        }
    }

    private void releaseBulkhead() {
        if (bulkhead != null && released.compareAndSet(false, true)) {
            bulkhead.release();
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (IOException ignore) {
        }
    }
}

final class SseAccumulator {
    private String event;
    private String id;
    private Long retryMs;
    private final StringBuilder data = new StringBuilder(128);
    private final Map<String, String> extFields = new LinkedHashMap<>();

    void appendLine(String rawLine) {
        int idx = rawLine.indexOf(':');
        String field = idx < 0 ? rawLine : rawLine.substring(0, idx);
        String value = idx < 0 ? "" : rawLine.substring(idx + 1);
        if (!value.isEmpty() && value.charAt(0) == ' ') {
            value = value.substring(1);
        }
        switch (field) {
            case "event" -> event = value;
            case "id" -> id = value;
            case "retry" -> {
                try {
                    retryMs = Long.parseLong(value);
                } catch (Exception ignore) {
                }
            }
            case "data" -> {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(value);
            }
            default -> {
                if (!field.isBlank()) {
                    extFields.put(field, value);
                }
            }
        }
    }

    boolean isEmpty() {
        return event == null && id == null && retryMs == null && data.length() == 0 && extFields.isEmpty();
    }

    VKHttpSseEvent toEvent() {
        return new VKHttpSseEvent(event, id, retryMs, data.toString(), extFields);
    }

    void reset() {
        event = null;
        id = null;
        retryMs = null;
        data.setLength(0);
        extFields.clear();
    }
}
