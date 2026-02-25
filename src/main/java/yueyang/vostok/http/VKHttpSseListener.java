package yueyang.vostok.http;

public interface VKHttpSseListener {
    default void onOpen(VKHttpResponseMeta meta) {
    }

    void onEvent(VKHttpSseEvent event);

    default void onError(Throwable t) {
    }

    default void onComplete() {
    }
}
