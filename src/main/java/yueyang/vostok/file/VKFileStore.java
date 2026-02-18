package yueyang.vostok.file;

import java.time.Instant;
import java.util.List;

/**
 * File store abstraction. Local file system is the default implementation.
 * OSS/object storage can implement this interface in the future.
 */
public interface VKFileStore {
    String mode();

    void create(String path, String content);

    void write(String path, String content);

    void update(String path, String content);

    String read(String path);

    boolean delete(String path);

    boolean exists(String path);

    void append(String path, String content);

    List<String> readLines(String path);

    void writeLines(String path, List<String> lines);

    List<VKFileInfo> list(String path, boolean recursive);

    void mkdirs(String path);

    void copy(String sourcePath, String targetPath, boolean replaceExisting);

    void move(String sourcePath, String targetPath, boolean replaceExisting);

    void touch(String path);

    long size(String path);

    Instant lastModified(String path);
}
