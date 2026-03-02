package yueyang.vostok.ai;

import yueyang.vostok.ai.tool.VKAiToolCall;

import java.util.List;

public interface VKAiChatDeltaStream extends AutoCloseable {
    boolean hasNext(long timeoutMs);

    VKAiChatDelta next();

    void cancel();

    boolean isDone();

    VKAiUsage finalUsage();

    String finishReason();

    String providerRequestId();

    /**
     * 返回流中累积的 tool_calls（Ext 1：流式 tool call 解析）。
     * 仅在 isDone() == true 后调用才能保证数据完整。
     */
    default List<VKAiToolCall> toolCalls() {
        return List.of();
    }

    @Override
    void close();
}
