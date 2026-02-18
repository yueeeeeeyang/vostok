package yueyang.vostok.file;

@FunctionalInterface
public interface VKFileWatchListener {
    void onEvent(VKFileWatchEvent event);
}
