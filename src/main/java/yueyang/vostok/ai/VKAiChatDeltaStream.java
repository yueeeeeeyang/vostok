package yueyang.vostok.ai;

public interface VKAiChatDeltaStream extends AutoCloseable {
    boolean hasNext(long timeoutMs);

    VKAiChatDelta next();

    void cancel();

    boolean isDone();

    VKAiUsage finalUsage();

    String finishReason();

    String providerRequestId();

    @Override
    void close();
}
