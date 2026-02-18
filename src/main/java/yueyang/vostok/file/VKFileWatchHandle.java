package yueyang.vostok.file;

public interface VKFileWatchHandle extends AutoCloseable {
    @Override
    void close();
}
