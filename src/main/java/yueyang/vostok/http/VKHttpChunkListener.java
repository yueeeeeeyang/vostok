package yueyang.vostok.http;

public interface VKHttpChunkListener {
    default void onOpen(VKHttpResponseMeta meta) {
    }

    void onChunk(byte[] chunk);

    default void onError(Throwable t) {
    }

    default void onComplete() {
    }
}
