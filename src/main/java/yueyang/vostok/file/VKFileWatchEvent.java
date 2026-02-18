package yueyang.vostok.file;

import java.time.Instant;

public final class VKFileWatchEvent {
    private final VKFileWatchEventType type;
    private final String path;
    private final Instant time;

    public VKFileWatchEvent(VKFileWatchEventType type, String path, Instant time) {
        this.type = type;
        this.path = path;
        this.time = time;
    }

    public VKFileWatchEventType type() {
        return type;
    }

    public String path() {
        return path;
    }

    public Instant time() {
        return time;
    }
}
