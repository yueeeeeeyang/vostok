package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiChatDelta;
import yueyang.vostok.ai.VKAiChatDeltaStream;
import yueyang.vostok.ai.VKAiUsage;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;
import yueyang.vostok.ai.tool.VKAiToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 流式聊天响应实现。
 *
 * Bug 1 修复：通过 terminateCallback 在流真正结束（complete/fail）时通知调用方更新指标，
 *            不再在流建立时立即计入成功。
 * Ext 1：accumulateToolCallDelta 逐块累积 tool_calls delta，流结束后可通过 toolCalls() 获取完整列表。
 */
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

    // Bug 1：流终止时触发的回调，参数 true=成功 false=失败
    private volatile Consumer<Boolean> terminateCallback;

    // Ext 1：按 tool call index 累积的流式 tool call 数据
    // key = index, value = accumulator
    private final ConcurrentHashMap<Integer, ToolCallAccumulator> streamingToolCalls = new ConcurrentHashMap<>();

    VKAiChatDeltaStreamImpl(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    // -------------------------------------------------------------------------
    // 公开接口
    // -------------------------------------------------------------------------

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

    /**
     * Ext 1：返回流中累积的完整 tool_calls 列表。
     * 仅在 isDone() 后调用才能保证列表完整。
     */
    @Override
    public List<VKAiToolCall> toolCalls() {
        List<ToolCallAccumulator> sorted = new ArrayList<>(streamingToolCalls.values());
        sorted.sort(java.util.Comparator.comparingInt(a -> a.index));
        List<VKAiToolCall> out = new ArrayList<>(sorted.size());
        for (ToolCallAccumulator acc : sorted) {
            String name = acc.name;
            if (name == null || name.isBlank()) {
                continue;
            }
            out.add(new VKAiToolCall(acc.id, name, acc.arguments.toString()));
        }
        return out;
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

    // -------------------------------------------------------------------------
    // 内部方法（供 VKAiRuntime 调用）
    // -------------------------------------------------------------------------

    /** Bug 1：注册终止回调，参数 true=成功（无错误），false=失败。 */
    void onTerminate(Consumer<Boolean> callback) {
        this.terminateCallback = callback;
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

    /**
     * Ext 1：累积单个 tool_call delta（OpenAI streaming tool_calls 格式）。
     * @param index tool call 在 choices[0].delta.tool_calls 中的 index 字段
     * @param tcMap 当前 delta 块中该 tool call 的 Map
     */
    void accumulateToolCallDelta(int index, Map<?, ?> tcMap) {
        ToolCallAccumulator acc = streamingToolCalls.computeIfAbsent(index, ToolCallAccumulator::new);
        // id 只在首个 delta 中出现
        Object idObj = tcMap.get("id");
        if (idObj != null && acc.id == null) {
            acc.id = String.valueOf(idObj);
        }
        // function.name 只在首个 delta 中出现
        Map<?, ?> fn = VKAiJsonOps.asMap(tcMap.get("function"));
        Object nameObj = fn.get("name");
        if (nameObj != null && acc.name == null) {
            acc.name = String.valueOf(nameObj);
        }
        // function.arguments 逐 token 追加
        Object argsObj = fn.get("arguments");
        if (argsObj != null) {
            acc.arguments.append(argsObj);
        }
    }

    /**
     * Bug 1：流正常完成时调用。
     * 触发 terminateCallback(true)（若已注册），然后入队 END 标记。
     */
    void complete() {
        if (done.compareAndSet(false, true)) {
            queue.offer(END);
            // Bug 1：此处才是真正成功的时机
            Consumer<Boolean> cb = terminateCallback;
            if (cb != null) {
                try {
                    cb.accept(error == null);
                } catch (Throwable ignore) {
                }
            }
        }
    }

    /**
     * Bug 1：流发生错误时调用。设置 error 后调用 complete()，
     * complete 内判断 error != null → 触发 callback(false)。
     */
    void fail(VKAiException e) {
        this.error = Objects.requireNonNullElseGet(e,
                () -> new VKAiException(VKAiErrorCode.STATE_ERROR, "Stream failed"));
        complete();
    }

    // -------------------------------------------------------------------------
    // 内部辅助
    // -------------------------------------------------------------------------

    private void throwIfError() {
        if (error != null) {
            throw error;
        }
    }

    // -------------------------------------------------------------------------
    // tool call 累积器
    // -------------------------------------------------------------------------

    private static final class ToolCallAccumulator {
        final int index;
        volatile String id;
        volatile String name;
        final StringBuilder arguments = new StringBuilder();

        ToolCallAccumulator(int index) {
            this.index = index;
        }
    }
}
