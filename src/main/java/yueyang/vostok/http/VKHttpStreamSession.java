package yueyang.vostok.http;

public interface VKHttpStreamSession extends AutoCloseable {
    boolean isOpen();

    void cancel();

    @Override
    void close();
}
