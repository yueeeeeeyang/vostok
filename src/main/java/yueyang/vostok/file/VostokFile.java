package yueyang.vostok.file;

import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
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
    private static final Object LOCK = new Object();
    private static final Map<String, VKFileStore> STORES = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> MODE_CONTEXT = new ThreadLocal<>();

    private static volatile String defaultMode;
    private static volatile boolean initialized;
    private static volatile VKFileConfig config;

    protected VostokFile() {
    }

    public static void init(VKFileConfig fileConfig) {
        synchronized (LOCK) {
            requireNotNull(fileConfig, "VKFileConfig is null");
            requireNotBlank(fileConfig.getMode(), "Mode is blank");
            requireNotBlank(fileConfig.getBaseDir(), "Base directory is blank");
            requireNotNull(fileConfig.getCharset(), "Charset is null");

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
        store().create(path, content);
    }

    public static void write(String path, String content) {
        store().write(path, content);
    }

    public static void update(String path, String content) {
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
        store().writeBytes(path, content);
    }

    public static void appendBytes(String path, byte[] content) {
        store().appendBytes(path, content);
    }

    public static long writeFrom(String path, InputStream input) {
        return store().writeFrom(path, input);
    }

    public static long writeFrom(String path, InputStream input, boolean replaceExisting) {
        return store().writeFrom(path, input, replaceExisting);
    }

    public static long appendFrom(String path, InputStream input) {
        return store().appendFrom(path, input);
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
        return store().delete(path);
    }

    public static boolean deleteIfExists(String path) {
        return store().deleteIfExists(path);
    }

    public static boolean deleteRecursively(String path) {
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
        store().append(path, content);
    }

    public static List<String> readLines(String path) {
        return store().readLines(path);
    }

    public static void writeLines(String path, List<String> lines) {
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
        store().mkdir(path);
    }

    public static void mkdirs(String path) {
        store().mkdirs(path);
    }

    public static void rename(String path, String newName) {
        store().rename(path, newName);
    }

    public static void copy(String sourcePath, String targetPath) {
        store().copy(sourcePath, targetPath, true);
    }

    public static void copy(String sourcePath, String targetPath, boolean replaceExisting) {
        store().copy(sourcePath, targetPath, replaceExisting);
    }

    public static void move(String sourcePath, String targetPath) {
        store().move(sourcePath, targetPath, true);
    }

    public static void move(String sourcePath, String targetPath, boolean replaceExisting) {
        store().move(sourcePath, targetPath, replaceExisting);
    }

    public static void copyDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        store().copyDir(sourceDir, targetDir, strategy);
    }

    public static void moveDir(String sourceDir, String targetDir, VKFileConflictStrategy strategy) {
        store().moveDir(sourceDir, targetDir, strategy);
    }

    public static void touch(String path) {
        store().touch(path);
    }

    public static long size(String path) {
        return store().size(path);
    }

    public static Instant lastModified(String path) {
        return store().lastModified(path);
    }

    public static void zip(String sourcePath, String zipPath) {
        store().zip(sourcePath, zipPath);
    }

    public static void unzip(String zipPath, String targetDir) {
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
        store().unzip(zipPath, targetDir, options);
    }

    public static VKFileWatchHandle watch(String path, VKFileWatchListener listener) {
        return store().watch(path, config().isWatchRecursiveDefault(), listener);
    }

    public static VKFileWatchHandle watch(String path, boolean recursive, VKFileWatchListener listener) {
        return store().watch(path, recursive, listener);
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

    private static void ensureInitialized() {
        if (!initialized) {
            throw new VKFileException(VKFileErrorCode.NOT_INITIALIZED,
                    "Vostok.File is not initialized. Call Vostok.File.init(...) first.");
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
                .watchRecursiveDefault(source.isWatchRecursiveDefault());
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
}
