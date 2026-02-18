package yueyang.vostok.file;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import java.io.OutputStream;

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

    byte[] readBytes(String path);

    byte[] readRange(String path, long offset, int length);

    long readRangeTo(String path, long offset, long length, OutputStream output);

    long readTo(String path, OutputStream output);

    void writeBytes(String path, byte[] content);

    void appendBytes(String path, byte[] content);

    long writeFrom(String path, InputStream input);

    long writeFrom(String path, InputStream input, boolean replaceExisting);

    long appendFrom(String path, InputStream input);

    byte[] thumbnail(String imagePath, VKThumbnailOptions options);

    void thumbnailTo(String imagePath, String targetPath, VKThumbnailOptions options);

    String hash(String path, String algorithm);

    boolean delete(String path);

    boolean deleteIfExists(String path);

    boolean deleteRecursively(String path);

    boolean exists(String path);

    boolean isFile(String path);

    boolean isDirectory(String path);

    void append(String path, String content);

    List<String> readLines(String path);

    void writeLines(String path, List<String> lines);

    List<VKFileInfo> list(String path, boolean recursive);

    List<VKFileInfo> walk(String path, boolean recursive, Predicate<VKFileInfo> filter);

    void mkdir(String path);

    void mkdirs(String path);

    void rename(String path, String newName);

    void copy(String sourcePath, String targetPath, boolean replaceExisting);

    void move(String sourcePath, String targetPath, boolean replaceExisting);

    void copyDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy);

    void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy);

    void touch(String path);

    long size(String path);

    Instant lastModified(String path);

    void zip(String sourcePath, String zipPath);

    void unzip(String zipPath, String targetDir, boolean replaceExisting);

    void unzip(String zipPath, String targetDir, VKUnzipOptions options);

    VKFileWatchHandle watch(String path, VKFileWatchListener listener);

    VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener);

    default void close() {
    }
}
