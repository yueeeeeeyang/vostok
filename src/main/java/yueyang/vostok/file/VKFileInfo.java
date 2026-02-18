package yueyang.vostok.file;

import java.time.Instant;

/**
 * File metadata.
 */
public final class VKFileInfo {
    private final String path;
    private final boolean directory;
    private final long size;
    private final Instant lastModified;

    public VKFileInfo(String path, boolean directory, long size, Instant lastModified) {
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String path() {
        return path;
    }

    public boolean directory() {
        return directory;
    }

    public long size() {
        return size;
    }

    public Instant lastModified() {
        return lastModified;
    }
}
