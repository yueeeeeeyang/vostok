package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiChatDelta;
import yueyang.vostok.ai.VKAiChatDeltaStream;
import yueyang.vostok.ai.VKAiUsage;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class VKAiChatDeltaStreamImpl implements VKAiChatDeltaStream {
    private static final Object END = new Object();

    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Runnable cancelAction;
    private volatile VKAiUsage finalUsage = new VKAiUsage(0, 0, 0);
    private volatile String finishReason;
    private volatile String providerRequestId;
    private volatile VKAiException error;
    private volatile VKAiChatDelta nextDelta;

    VKAiChatDeltaStreamImpl(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    @Override
    public boolean hasNext(long timeoutMs) {
        if (nextDelta != null) {
            return true;
        }
        long timeout = Math.max(0L, timeoutMs);
        Object item;
        try {
            item = queue.poll(done.get() ? 0L : timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VKAiException(VKAiErrorCode.STATE_ERROR, "Stream polling interrupted", e);
        }
        if (item == null) {
            throwIfError();
            return false;
        }
        if (item == END) {
            done.set(true);
            throwIfError();
            return false;
        }
        nextDelta = (VKAiChatDelta) item;
        return true;
    }

    @Override
    public VKAiChatDelta next() {
        if (nextDelta != null) {
            VKAiChatDelta out = nextDelta;
            nextDelta = null;
            return out;
        }
        if (hasNext(0)) {
            return next();
        }
        throw new VKAiException(VKAiErrorCode.STATE_ERROR, "No next stream delta available");
    }

    @Override
    public void cancel() {
        close();
    }

    @Override
    public boolean isDone() {
        return done.get();
    }

    @Override
    public VKAiUsage finalUsage() {
        return finalUsage;
    }

    @Override
    public String finishReason() {
        return finishReason;
    }

    @Override
    public String providerRequestId() {
        return providerRequestId;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        done.set(true);
        if (cancelAction != null) {
            try {
                cancelAction.run();
            } catch (Throwable ignore) {
            }
        }
        queue.offer(END);
    }

    void setProviderRequestId(String providerRequestId) {
        if (providerRequestId != null && !providerRequestId.isBlank()) {
            this.providerRequestId = providerRequestId;
        }
    }

    void setFinishReason(String finishReason) {
        if (finishReason != null && !finishReason.isBlank()) {
            this.finishReason = finishReason;
        }
    }

    void setFinalUsage(VKAiUsage usage) {
        if (usage != null) {
            this.finalUsage = usage;
        }
    }

    void emitDelta(String text) {
        if (text == null || text.isEmpty() || done.get()) {
            return;
        }
        queue.offer(new VKAiChatDelta(text, false));
    }

    void complete() {
        if (done.compareAndSet(false, true)) {
            queue.offer(END);
        }
    }

    void fail(VKAiException e) {
        this.error = Objects.requireNonNullElseGet(e,
                () -> new VKAiException(VKAiErrorCode.STATE_ERROR, "Stream failed"));
        complete();
    }

    private void throwIfError() {
        if (error != null) {
            throw error;
        }
    }
}
