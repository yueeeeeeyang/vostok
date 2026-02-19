package yueyang.vostok.event;

@FunctionalInterface
public interface VKEventListener<T> {
    void onEvent(T event) throws Exception;
}
