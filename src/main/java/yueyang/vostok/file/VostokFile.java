package yueyang.vostok.file;

import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;
import yueyang.vostok.security.VostokSecurity;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Vostok File entry.
 */
public class VostokFile {
    /**
     * 默认存储目录名称（相对于 user.dir）。
     * 未调用 init() 时懒加载使用，存储根路径为 {@code user.dir/vkfiles}。
     */
    public static final String DEFAULT_BASE_DIR_NAME = "vkfiles";

    private static final Object LOCK = new Object();
    private static final Map<String, VKFileStore> STORES = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> MODE_CONTEXT = new ThreadLocal<>();

    private static volatile String defaultMode;
    private static volatile boolean initialized;
    private static volatile VKFileConfig config;
    // Ext 5：只读模式标志。volatile 保证多线程可见性；所有写操作前通过 checkWritable() 检查。
    private static volatile boolean readOnly;

    protected VostokFile() {
    }

    public static void init(VKFileConfig fileConfig) {
        synchronized (LOCK) {
            requireNotNull(fileConfig, "VKFileConfig is null");
            requireNotBlank(fileConfig.getMode(), "Mode is blank");
            requireNotBlank(fileConfig.getBaseDir(), "Base directory is blank");
            requireNotNull(fileConfig.getCharset(), "Charset is null");
            requireNotBlank(fileConfig.getDatePartitionPattern(), "Date partition pattern is blank");
            requireNotBlank(fileConfig.getDatePartitionZoneId(), "Date partition zoneId is blank");
            validateDatePartitionConfig(fileConfig);

            if (initialized) {
                VKFileStore old = defaultMode == null ? null : STORES.get(defaultMode);
                if (old != null) {
                    try {
                        old.close();
                    } catch (Exception ignore) {
                    }
                    STORES.remove(defaultMode);
                }
                MODE_CONTEXT.remove();
            }

            String mode = normalizeMode(fileConfig.getMode());
            if (LocalFileStore.MODE.equals(mode)) {
                STORES.put(LocalFileStore.MODE,
                        new LocalFileStore(Path.of(fileConfig.getBaseDir()), fileConfig.getCharset()));
            } else if (!STORES.containsKey(mode)) {
                throw new VKFileException(VKFileErrorCode.CONFIG_ERROR,
                        "File mode is not registered: " + mode + ". Register store first.");
            }

            defaultMode = mode;
            config = copyConfig(fileConfig);
            initialized = true;
        }
    }

    public static boolean started() {
        return initialized;
    }

    public static VKFileConfig config() {
        ensureInitialized();
        return copyConfig(config);
    }

    public static void close() {
        synchronized (LOCK) {
            closeInternal();
        }
    }

    public static void registerStore(String mode, VKFileStore store) {
        requireNotBlank(mode, "Mode is blank");
        requireNotNull(store, "VKFileStore is null");
        STORES.put(normalizeMode(mode), store);
    }

    public static void setDefaultMode(String mode) {
        ensureInitialized();
        requireNotBlank(mode, "Mode is blank");
        String m = normalizeMode(mode);
        ensureStoreExists(m);
        defaultMode = m;
    }

    public static String defaultMode() {
        ensureInitialized();
        return defaultMode;
    }

    public static Set<String> modes() {
        return Set.copyOf(STORES.keySet());
    }

    // -------------------------------------------------------------------------
    // Ext 5：只读模式
    // -------------------------------------------------------------------------

    /**
     * 设置只读模式。启用后所有写操作（create/write/delete/gzip 等）均抛 READ_ONLY_ERROR。
     * close() 会自动将只读状态重置为 false。
     */
    public static void setReadOnly(boolean readOnly) {
        VostokFile.readOnly = readOnly;
    }

    /** 返回当前是否处于只读模式。 */
    public static boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 检查是否可写，只读模式下抛 READ_ONLY_ERROR。
     * 所有修改文件系统状态的公共方法均须在操作前调用此方法。
     */
    private static void checkWritable() {
        if (readOnly) {
            throw new VKFileException(VKFileErrorCode.READ_ONLY_ERROR, "File store is in read-only mode");
        }
    }

    public static void withMode(String mode, Runnable action) {
        ensureInitialized();
        requireNotNull(action, "Runnable is null");
        String prev = MODE_CONTEXT.get();
        String m = normalizeMode(mode);
        ensureStoreExists(m);
        MODE_CONTEXT.set(m);
        try {
            action.run();
        } finally {
            restoreMode(prev);
        }
    }

    public static <T> T withMode(String mode, Supplier<T> supplier) {
        ensureInitialized();
        requireNotNull(supplier, "Supplier is null");
        String prev = MODE_CONTEXT.get();
        String m = normalizeMode(mode);
        ensureStoreExists(m);
        MODE_CONTEXT.set(m);
        try {
            return supplier.get();
        } finally {
            restoreMode(prev);
        }
    }

    public static String currentMode() {
        ensureInitialized();
        String mode = MODE_CONTEXT.get();
        return mode == null || mode.isBlank() ? defaultMode : mode;
    }

    public static void create(String path, String content) {
        checkWritable();
        store().create(path, content);
    }

    public static void write(String path, String content) {
        checkWritable();
        store().write(path, content);
    }

    public static void update(String path, String content) {
        checkWritable();
        store().update(path, content);
    }

    public static String read(String path) {
        return store().read(path);
    }

    public static byte[] readBytes(String path) {
        return store().readBytes(path);
    }

    public static byte[] readRange(String path, long offset, int length) {
        return store().readRange(path, offset, length);
    }

    public static long readRangeTo(String path, long offset, long length, OutputStream output) {
        return store().readRangeTo(path, offset, length, output);
    }

    public static long readTo(String path, OutputStream output) {
        return store().readTo(path, output);
    }

    public static void writeBytes(String path, byte[] content) {
        checkWritable();
        store().writeBytes(path, content);
    }

    public static void appendBytes(String path, byte[] content) {
        checkWritable();
        store().appendBytes(path, content);
    }

    public static long writeFrom(String path, InputStream input) {
        checkWritable();
        return store().writeFrom(path, input);
    }

    public static long writeFrom(String path, InputStream input, boolean replaceExisting) {
        checkWritable();
        return store().writeFrom(path, input, replaceExisting);
    }

    public static long appendFrom(String path, InputStream input) {
        checkWritable();
        return store().appendFrom(path, input);
    }

    public static String suggestDatePath(String relativePath) {
        return suggestDatePath(relativePath, Instant.now());
    }

    public static String suggestDatePath(String relativePath, Instant atTime) {
        ensureInitialized();
        requireNotBlank(relativePath, "Relative path is blank");
        requireNotNull(atTime, "Instant is null");
        return store().suggestDatePath(relativePath, atTime, config());
    }

    public static String writeByDatePath(String relativePath, String content) {
        checkWritable();
        String path = suggestDatePath(relativePath);
        write(path, content);
        return path;
    }

    public static String writeBytesByDatePath(String relativePath, byte[] content) {
        checkWritable();
        String path = suggestDatePath(relativePath);
        writeBytes(path, content);
        return path;
    }

    public static String writeFromByDatePath(String relativePath, InputStream input) {
        checkWritable();
        String path = suggestDatePath(relativePath);
        writeFrom(path, input);
        return path;
    }

    public static VKFileMigrateResult migrateBaseDir(String targetBaseDir) {
        return migrateBaseDir(targetBaseDir, new VKFileMigrateOptions());
    }

    public static VKFileMigrateResult migrateBaseDir(String targetBaseDir, VKFileMigrateOptions options) {
        requireNotBlank(targetBaseDir, "Target baseDir is blank");
        requireNotNull(options, "VKFileMigrateOptions is null");
        return store().migrateBaseDir(targetBaseDir, options);
    }

    public static byte[] thumbnail(String imagePath, VKThumbnailOptions options) {
        return store().thumbnail(imagePath, options);
    }

    public static void thumbnailTo(String imagePath, String targetPath, VKThumbnailOptions options) {
        store().thumbnailTo(imagePath, targetPath, options);
    }

    public static String hash(String path, String algorithm) {
        return store().hash(path, algorithm);
    }

    public static boolean delete(String path) {
        checkWritable();
        return store().delete(path);
    }

    public static boolean deleteIfExists(String path) {
        checkWritable();
        return store().deleteIfExists(path);
    }

    public static boolean deleteRecursively(String path) {
        checkWritable();
        return store().deleteRecursively(path);
    }

    public static boolean exists(String path) {
        return store().exists(path);
    }

    public static boolean isFile(String path) {
        return store().isFile(path);
    }

    public static boolean isDirectory(String path) {
        return store().isDirectory(path);
    }

    public static void append(String path, String content) {
        checkWritable();
        store().append(path, content);
    }

    public static List<String> readLines(String path) {
        return store().readLines(path);
    }

    public static void writeLines(String path, List<String> lines) {
        checkWritable();
        store().writeLines(path, lines);
    }

    public static List<VKFileInfo> list(String path) {
        return store().list(path, false);
    }

    public static List<VKFileInfo> list(String path, boolean recursive) {
        return store().list(path, recursive);
    }

    public static List<VKFileInfo> walk(String path, boolean recursive, Predicate<VKFileInfo> filter) {
        return store().walk(path, recursive, filter);
    }

    public static List<VKFileInfo> walk(String path, boolean recursive) {
        return store().walk(path, recursive, null);
    }

    public static void mkdir(String path) {
        checkWritable();
        store().mkdir(path);
    }

    public static void mkdirs(String path) {
        checkWritable();
        store().mkdirs(path);
    }

    public static void rename(String path, String newName) {
        checkWritable();
        store().rename(path, newName);
    }

    public static void copy(String sourcePath, String targetPath) {
        checkWritable();
        store().copy(sourcePath, targetPath, true);
    }

    public static void copy(String sourcePath, String targetPath, boolean replaceExisting) {
        checkWritable();
        store().copy(sourcePath, targetPath, replaceExisting);
    }

    public static void move(String sourcePath, String targetPath) {
        checkWritable();
        store().move(sourcePath, targetPath, true);
    }

    public static void move(String sourcePath, String targetPath, boolean replaceExisting) {
        checkWritable();
        store().move(sourcePath, targetPath, replaceExisting);
    }

    public static void copyDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        checkWritable();
        store().copyDir(sourceDir, targetDir, strategy);
    }

    public static void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        checkWritable();
        store().moveDir(sourceDir, targetDir, strategy);
    }

    public static void touch(String path) {
        checkWritable();
        store().touch(path);
    }

    public static long size(String path) {
        return store().size(path);
    }

    public static Instant lastModified(String path) {
        return store().lastModified(path);
    }

    public static void zip(String sourcePath, String zipPath) {
        checkWritable();
        store().zip(sourcePath, zipPath);
    }

    public static void unzip(String zipPath, String targetDir) {
        checkWritable();
        VKFileConfig cfg = config();
        VKUnzipOptions opts = VKUnzipOptions.builder()
                .replaceExisting(true)
                .maxEntries(cfg.getUnzipMaxEntries())
                .maxTotalUncompressedBytes(cfg.getUnzipMaxTotalUncompressedBytes())
                .maxEntryUncompressedBytes(cfg.getUnzipMaxEntryUncompressedBytes())
                .build();
        store().unzip(zipPath, targetDir, opts);
    }

    public static void unzip(String zipPath, String targetDir, boolean replaceExisting) {
        checkWritable();
        VKFileConfig cfg = config();
        VKUnzipOptions opts = VKUnzipOptions.builder()
                .replaceExisting(replaceExisting)
                .maxEntries(cfg.getUnzipMaxEntries())
                .maxTotalUncompressedBytes(cfg.getUnzipMaxTotalUncompressedBytes())
                .maxEntryUncompressedBytes(cfg.getUnzipMaxEntryUncompressedBytes())
                .build();
        store().unzip(zipPath, targetDir, opts);
    }

    public static void unzip(String zipPath, String targetDir, VKUnzipOptions options) {
        checkWritable();
        store().unzip(zipPath, targetDir, options);
    }

    public static VKFileWatchHandle watch(String path, VKFileWatchListener listener) {
        return store().watch(path, config().isWatchRecursiveDefault(), listener);
    }

    public static VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener) {
        return store().watch(path, recursive, listener);
    }

    // -------------------------------------------------------------------------
    // Ext 1：目录总大小
    // -------------------------------------------------------------------------

    /** 递归计算目录下所有普通文件的字节总大小，目录本身不计入。 */
    public static long totalSize(String dirPath) {
        return store().totalSize(dirPath);
    }

    // -------------------------------------------------------------------------
    // Ext 2：临时文件
    // -------------------------------------------------------------------------

    /**
     * 在 tmp/ 子目录下创建临时文件，返回相对路径。
     * prefix/suffix 语义同 {@link java.nio.file.Files#createTempFile}。
     */
    public static String createTemp(String prefix, String suffix) {
        return store().createTemp(prefix, suffix);
    }

    /**
     * 在 subDir（相对 root）子目录下创建临时文件，返回相对路径。
     * subDir 为 null 时退回 tmp/。
     */
    public static String createTemp(String subDir, String prefix, String suffix) {
        return store().createTemp(subDir, prefix, suffix);
    }

    // -------------------------------------------------------------------------
    // Ext 3：GZip 压缩 / 解压
    // -------------------------------------------------------------------------

    /** GZip 压缩 sourcePath 到 gzPath，写操作前检查只读模式。 */
    public static void gzip(String sourcePath, String gzPath) {
        checkWritable();
        store().gzip(sourcePath, gzPath);
    }

    /** 解压 gzPath 到 targetPath，写操作前检查只读模式。 */
    public static void gunzip(String gzPath, String targetPath) {
        checkWritable();
        store().gunzip(gzPath, targetPath);
    }

    // -------------------------------------------------------------------------
    // Ext 4：文件加密 / 解密（委托给 VostokSecurity AES-256-GCM）
    // -------------------------------------------------------------------------

    /**
     * 读取 sourcePath 的原始字节，Base64 编码后用 secret 加密（AES-256-GCM），
     * 将密文文本写入 targetPath。
     * 加密格式：明文字节 → Base64 → AES-GCM → Base64 密文文本文件。
     *
     * @throws VKFileException ENCRYPT_ERROR 加密过程出错
     * @throws VKFileException READ_ONLY_ERROR 只读模式下写入被拒绝
     */
    public static void encryptFile(String sourcePath, String targetPath, String secret) {
        checkWritable();
        try {
            byte[] bytes = store().readBytes(sourcePath);
            String b64 = Base64.getEncoder().encodeToString(bytes);
            String encrypted = VostokSecurity.encrypt(b64, secret);
            store().write(targetPath, encrypted);
        } catch (VKFileException e) {
            throw e;
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.ENCRYPT_ERROR, "File encrypt failed: " + sourcePath, e);
        }
    }

    /**
     * 读取 sourcePath 的密文文本，用 secret 解密并 Base64 解码，
     * 将原始字节写入 targetPath。
     *
     * @throws VKFileException ENCRYPT_ERROR 解密过程出错（含密钥错误）
     * @throws VKFileException READ_ONLY_ERROR 只读模式下写入被拒绝
     */
    public static void decryptFile(String sourcePath, String targetPath, String secret) {
        checkWritable();
        try {
            String encrypted = store().read(sourcePath);
            String b64 = VostokSecurity.decrypt(encrypted, secret);
            byte[] bytes = Base64.getDecoder().decode(b64);
            store().writeBytes(targetPath, bytes);
        } catch (VKFileException e) {
            throw e;
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.ENCRYPT_ERROR, "File decrypt failed: " + sourcePath, e);
        }
    }

    private static VKFileStore store() {
        ensureInitialized();
        String mode = currentMode();
        VKFileStore store = STORES.get(mode);
        if (store == null) {
            throw new VKFileException(VKFileErrorCode.STATE_ERROR, "File mode not found: " + mode);
        }
        return store;
    }

    private static void ensureStoreExists(String mode) {
        if (!STORES.containsKey(mode)) {
            throw new VKFileException(VKFileErrorCode.STATE_ERROR, "File mode not found: " + mode);
        }
    }

    private static String normalizeMode(String mode) {
        return mode == null ? "" : mode.trim().toLowerCase();
    }

    private static void restoreMode(String previous) {
        if (previous == null || previous.isBlank()) {
            MODE_CONTEXT.remove();
            return;
        }
        MODE_CONTEXT.set(previous);
    }

    /**
     * 确保模块已初始化。若尚未初始化，则使用默认配置懒加载：
     * 存储根目录为 <程序文件所在目录>/vkfiles，其余选项取 {@link VKFileConfig} 默认值。
     *
     * <p>双重检查加锁（{@code initialized} 为 volatile），保证多线程下只初始化一次。
     * {@link #init} 本身也持有 LOCK，Java 内置锁可重入，此处不会死锁。
     */
    private static void ensureInitialized() {
        if (!initialized) {
            synchronized (LOCK) {
                if (!initialized) {
                    init(new VKFileConfig().baseDir(resolveDefaultBaseDir()));
                }
            }
        }
    }

    /**
     * 解析默认存储目录，策略：
     * <ol>
     *   <li>优先取当前运行的 JAR/class 文件所在目录，拼接 {@value #DEFAULT_BASE_DIR_NAME}。
     *       例如 {@code java -jar /root/demo.jar} → {@code /root/vkfiles}。</li>
     *   <li>若无法获取代码路径（权限受限、WAR 部署等），回退到
     *       {@code user.dir/vkfiles}。</li>
     * </ol>
     */
    private static String resolveDefaultBaseDir() {
        try {
            java.net.URL location = VostokFile.class
                    .getProtectionDomain().getCodeSource().getLocation();
            Path codePath = Path.of(location.toURI()).toAbsolutePath().normalize();
            // JAR 文件（java -jar）：codePath 为 /root/demo.jar，parent 即 /root
            // 目录（IDE/exploded）：codePath 本身就是目录，直接使用
            Path codeDir = java.nio.file.Files.isRegularFile(codePath)
                    ? codePath.getParent() : codePath;
            return codeDir.resolve(DEFAULT_BASE_DIR_NAME).toString();
        } catch (Exception ignored) {
            // 回退：使用当前工作目录
            return System.getProperty("user.dir", ".") + "/" + DEFAULT_BASE_DIR_NAME;
        }
    }

    private static VKFileConfig copyConfig(VKFileConfig source) {
        return new VKFileConfig()
                .mode(source.getMode())
                .baseDir(source.getBaseDir())
                .charset(source.getCharset())
                .unzipMaxEntries(source.getUnzipMaxEntries())
                .unzipMaxTotalUncompressedBytes(source.getUnzipMaxTotalUncompressedBytes())
                .unzipMaxEntryUncompressedBytes(source.getUnzipMaxEntryUncompressedBytes())
                .watchRecursiveDefault(source.isWatchRecursiveDefault())
                .datePartitionPattern(source.getDatePartitionPattern())
                .datePartitionZoneId(source.getDatePartitionZoneId());
    }

    private static void closeInternal() {
        for (VKFileStore store : STORES.values()) {
            try {
                store.close();
            } catch (Exception ignore) {
            }
        }
        STORES.clear();
        MODE_CONTEXT.remove();
        defaultMode = null;
        config = null;
        initialized = false;
        // Ext 5：close 时重置只读模式，防止重新初始化后残留只读状态
        readOnly = false;
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new VKFileException(VKFileErrorCode.INVALID_ARGUMENT, message);
        }
    }

    private static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new VKFileException(VKFileErrorCode.INVALID_ARGUMENT, message);
        }
    }

    private static void validateDatePartitionConfig(VKFileConfig cfg) {
        try {
            DateTimeFormatter.ofPattern(cfg.getDatePartitionPattern());
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.CONFIG_ERROR,
                    "Invalid datePartitionPattern: " + cfg.getDatePartitionPattern(), e);
        }
        try {
            ZoneId.of(cfg.getDatePartitionZoneId());
        } catch (Exception e) {
            throw new VKFileException(VKFileErrorCode.CONFIG_ERROR,
                    "Invalid datePartitionZoneId: " + cfg.getDatePartitionZoneId(), e);
        }
    }
}
