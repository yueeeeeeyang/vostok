package yueyang.vostok.file;

import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

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

    String suggestDatePath(String relativePath, Instant atTime, VKFileConfig config);

    VKFileMigrateResult migrateBaseDir(String targetBaseDir, VKFileMigrateOptions options);

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

    /**
     * ZIP 压缩，可选择是否保留源目录名作为压缩包根目录。
     *
     * <p>includeBaseDir=true 与 {@link #zip(String, String)} 一致；
     * includeBaseDir=false 时仅压缩目录内容。
     */
    default void zip(String sourcePath, String zipPath, boolean includeBaseDir) {
        if (includeBaseDir) {
            zip(sourcePath, zipPath);
            return;
        }
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "zip(source, target, includeBaseDir=false) not supported");
    }

    void unzip(String zipPath, String targetDir, boolean replaceExisting);

    void unzip(String zipPath, String targetDir, VKUnzipOptions options);

    VKFileWatchHandle watch(String path, VKFileWatchListener listener);

    VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener);

    /** 计算目录下所有文件的总大小（字节），不包括目录本身。不支持该操作时抛 UNSUPPORTED。 */
    default long totalSize(String dirPath) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "totalSize not supported by this store");
    }

    /**
     * 在 tmp/ 子目录下创建临时文件，返回相对路径。
     * prefix/suffix 语义同 Files.createTempFile。
     */
    default String createTemp(String prefix, String suffix) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "createTemp not supported by this store");
    }

    /**
     * 在指定子目录下创建临时文件，返回相对路径。
     * subDir 为 null 时退回 tmp/。
     */
    default String createTemp(String subDir, String prefix, String suffix) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "createTemp not supported by this store");
    }

    /** 将 sourcePath 文件 GZip 压缩为 gzPath。source 与 target 不可相同。 */
    default void gzip(String sourcePath, String gzPath) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "gzip not supported by this store");
    }

    /** 将 gzPath GZip 文件解压到 targetPath。source 与 target 不可相同。 */
    default void gunzip(String gzPath, String targetPath) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "gunzip not supported by this store");
    }

    /**
     * 使用安全模块（AES-256-GCM vkf2 分块流式格式）加密 sourcePath 并写入 targetPath。
     *
     * <p>调用前须确保 {@code Vostok.Security} 已通过 {@code initKeyStore()} 初始化；
     * {@code keyId} 对应 KeyStore 中的 KEK，不存在时自动创建。
     * source 与 target 路径不可相同。
     *
     * @throws VKFileException ENCRYPT_ERROR   加密失败（含密钥操作异常）
     * @throws VKFileException UNSUPPORTED     当前 Store 不支持此操作
     */
    default void encryptFile(String sourcePath, String targetPath, String keyId) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "encryptFile not supported by this store");
    }

    /**
     * 解密 vkf2（或 vkf1 遗留）格式的 sourcePath 并写入 targetPath。
     *
     * <p>keyId 和 KEK 版本号均从文件头自动读取，支持跨 KEK 轮换后的历史文件解密。
     * 解密失败（认证标签不匹配、文件截断、密钥错误）时不向 targetPath 写入任何字节。
     * source 与 target 路径不可相同。
     *
     * @throws VKFileException ENCRYPT_ERROR   解密失败（含篡改检测、密钥不存在）
     * @throws VKFileException UNSUPPORTED     当前 Store 不支持此操作
     */
    default void decryptFile(String sourcePath, String targetPath) {
        throw new VKFileException(VKFileErrorCode.UNSUPPORTED, "decryptFile not supported by this store");
    }

    default void close() {
    }
}
