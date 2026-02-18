package yueyang.vostok.file;

import yueyang.vostok.util.VKAssert;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Vostok File entry.
 */
public class VostokFile {
    private static final Map<String, VKFileStore> STORES = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> MODE_CONTEXT = new ThreadLocal<>();
    private static final String DEFAULT_LOCAL_ROOT = System.getProperty("user.dir", ".");
    private static volatile String defaultMode;

    static {
        registerStore(LocalTextFileStore.MODE, new LocalTextFileStore(Path.of(DEFAULT_LOCAL_ROOT)));
        defaultMode = LocalTextFileStore.MODE;
    }

    protected VostokFile() {
    }

    public static void initLocal(String baseDir) {
        VKAssert.notBlank(baseDir, "Base directory is blank");
        registerStore(LocalTextFileStore.MODE, new LocalTextFileStore(Path.of(baseDir)));
        defaultMode = LocalTextFileStore.MODE;
    }

    public static void registerStore(String mode, VKFileStore store) {
        VKAssert.notBlank(mode, "Mode is blank");
        VKAssert.notNull(store, "VKFileStore is null");
        STORES.put(normalizeMode(mode), store);
    }

    public static void setDefaultMode(String mode) {
        VKAssert.notBlank(mode, "Mode is blank");
        String m = normalizeMode(mode);
        ensureStoreExists(m);
        defaultMode = m;
    }

    public static String defaultMode() {
        return defaultMode;
    }

    public static Set<String> modes() {
        return Set.copyOf(STORES.keySet());
    }

    public static void withMode(String mode, Runnable action) {
        VKAssert.notNull(action, "Runnable is null");
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
        VKAssert.notNull(supplier, "Supplier is null");
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

    public static boolean delete(String path) {
        return store().delete(path);
    }

    public static boolean exists(String path) {
        return store().exists(path);
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

    public static void mkdirs(String path) {
        store().mkdirs(path);
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

    public static void touch(String path) {
        store().touch(path);
    }

    public static long size(String path) {
        return store().size(path);
    }

    public static Instant lastModified(String path) {
        return store().lastModified(path);
    }

    private static VKFileStore store() {
        String mode = currentMode();
        VKFileStore store = STORES.get(mode);
        if (store == null) {
            throw new IllegalStateException("File mode not found: " + mode);
        }
        return store;
    }

    private static void ensureStoreExists(String mode) {
        if (!STORES.containsKey(mode)) {
            throw new IllegalStateException("File mode not found: " + mode);
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
}
