package yueyang.vostok.config.core;

import yueyang.vostok.config.VKConfigOptions;
import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;
import yueyang.vostok.config.loader.VKConfigScanResult;
import yueyang.vostok.config.loader.VKConfigScanner;
import yueyang.vostok.config.loader.VKConfigSource;
import yueyang.vostok.config.parser.PropertiesConfigParser;
import yueyang.vostok.config.parser.VKConfigParser;
import yueyang.vostok.config.parser.YamlConfigParser;
import yueyang.vostok.config.validate.VKConfigValidator;
import yueyang.vostok.config.validate.VKConfigView;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class VKConfigRuntime {
    private static final Object LOCK = new Object();
    private static final VKConfigRuntime INSTANCE = new VKConfigRuntime();

    private final CopyOnWriteArrayList<VKConfigParser> parsers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VKConfigValidator> validators = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Path> manualFiles = new CopyOnWriteArrayList<>();
    private final Map<String, String> runtimeOverrides = new ConcurrentHashMap<>();

    private volatile VKConfigOptions options = new VKConfigOptions();
    private volatile Snapshot snapshot = Snapshot.empty();
    private volatile boolean loaded;
    private volatile boolean initialized;
    private volatile String lastWatchError;

    private volatile WatchService watchService;
    private volatile Thread watchThread;

    private VKConfigRuntime() {
        parsers.add(new PropertiesConfigParser());
        parsers.add(new YamlConfigParser());
    }

    public static VKConfigRuntime getInstance() {
        return INSTANCE;
    }

    public boolean started() {
        return loaded || initialized;
    }

    public String lastWatchError() {
        return lastWatchError;
    }

    public void close() {
        synchronized (LOCK) {
            stopWatcher();
            snapshot = Snapshot.empty();
            options = new VKConfigOptions();
            manualFiles.clear();
            runtimeOverrides.clear();
            validators.clear();
            parsers.clear();
            parsers.add(new PropertiesConfigParser());
            parsers.add(new YamlConfigParser());
            loaded = false;
            initialized = false;
            lastWatchError = null;
        }
    }

    public void init(VKConfigOptions initOptions) {
        if (initOptions == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigOptions is null");
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            options = initOptions.copy();
            reloadInternal(true);
            initialized = true;
        }
    }

    public void reinit(VKConfigOptions initOptions) {
        if (initOptions == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigOptions is null");
        }
        synchronized (LOCK) {
            options = initOptions.copy();
            reloadInternal(true);
            initialized = true;
        }
    }

    public void configure(Consumer<VKConfigOptions> customizer) {
        if (customizer == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Customizer is null");
        }
        synchronized (LOCK) {
            VKConfigOptions next = options.copy();
            customizer.accept(next);
            options = next;
            if (loaded) {
                reloadInternal(true);
            }
        }
    }

    public void registerParser(VKConfigParser parser) {
        if (parser == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigParser is null");
        }
        parsers.add(0, parser);
        reload();
    }

    public void registerValidator(VKConfigValidator validator) {
        if (validator == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigValidator is null");
        }
        validators.add(validator);
        if (loaded) {
            reload();
        }
    }

    public void clearValidators() {
        validators.clear();
        if (loaded) {
            reload();
        }
    }

    public void putOverride(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Override key is blank");
        }
        if (value == null) {
            runtimeOverrides.remove(key);
        } else {
            runtimeOverrides.put(key, value);
        }
        reload();
    }

    public void removeOverride(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        runtimeOverrides.remove(key);
        reload();
    }

    public void clearOverrides() {
        runtimeOverrides.clear();
        reload();
    }

    public void addFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Config file path is blank");
        }
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR, "Config file does not exist: " + path);
        }
        if (!isSupported(path.getFileName().toString())) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Unsupported config file extension: " + path.getFileName());
        }

        manualFiles.add(path);
        reload();
    }

    public void addFiles(Collection<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }
        for (String filePath : filePaths) {
            addFile(filePath);
        }
    }

    public void clearManualFiles() {
        manualFiles.clear();
        reload();
    }

    public void reload() {
        synchronized (LOCK) {
            reloadInternal(true);
            initialized = true;
        }
    }

    public String get(String key) {
        if (key == null || key.isBlank()) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Config key is blank");
        }
        return current().data().get(key);
    }

    public String required(String key) {
        String value = get(key);
        if (value == null) {
            throw new VKConfigException(VKConfigErrorCode.KEY_NOT_FOUND, "Config key not found: " + key);
        }
        return value;
    }

    public boolean has(String key) {
        return get(key) != null;
    }

    public Set<String> keys() {
        return current().data().keySet();
    }

    public Map<String, String> snapshot() {
        return current().data();
    }

    private Snapshot current() {
        Snapshot local = snapshot;
        if (loaded) {
            return local;
        }

        synchronized (LOCK) {
            if (!loaded) {
                reloadInternal(true);
            }
            return snapshot;
        }
    }

    private void reloadInternal(boolean refreshWatcher) {
        VKConfigScanResult defaults = VKConfigScanner.scanDefaults(options);

        List<VKConfigSource> ordered = new ArrayList<>(defaults.defaultSources());
        for (Path file : manualFiles) {
            if (Files.exists(file) && Files.isRegularFile(file)) {
                ordered.add(VKConfigSource.ofFile(file));
            }
        }

        Map<String, String> merged = new LinkedHashMap<>();
        Map<String, String> namespaceSource = options.isStrictNamespaceConflict()
                ? new LinkedHashMap<>()
                : Collections.emptyMap();

        for (VKConfigSource source : ordered) {
            VKConfigParser parser = resolveParser(source.fileName());
            if (parser == null) {
                continue;
            }

            if (options.isStrictNamespaceConflict()) {
                String existing = namespaceSource.get(source.namespace());
                if (existing != null && !existing.equals(source.sourceId())) {
                    throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                            "Namespace conflict: " + source.namespace() + " from " + existing + " and " + source.sourceId());
                }
                namespaceSource.put(source.namespace(), source.sourceId());
            }

            Map<String, String> values = parser.parse(source.sourceId(), source.openStream());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = source.namespace() + "." + entry.getKey();
                merged.put(key, entry.getValue());
            }
        }

        applyEnvironment(merged);
        applySystemProperties(merged);
        if (!runtimeOverrides.isEmpty()) {
            merged.putAll(runtimeOverrides);
        }

        runValidators(merged);

        snapshot = new Snapshot(Collections.unmodifiableMap(merged));
        loaded = true;

        if (refreshWatcher) {
            restartWatcher(buildWatchRoots(defaults.watchRoots()));
        }
    }

    private void runValidators(Map<String, String> merged) {
        if (validators.isEmpty()) {
            return;
        }
        VKConfigView view = new VKConfigView(Collections.unmodifiableMap(merged));
        for (VKConfigValidator validator : validators) {
            validator.validate(view);
        }
    }

    private Set<Path> buildWatchRoots(Set<Path> scanRoots) {
        Set<Path> roots = new LinkedHashSet<>(scanRoots);
        for (Path file : manualFiles) {
            Path parent = file.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                roots.add(parent.toAbsolutePath().normalize());
            }
        }
        return roots;
    }

    private void applyEnvironment(Map<String, String> merged) {
        if (!options.isLoadEnv()) {
            return;
        }
        Map<String, String> env;
        try {
            env = options.getEnvProvider().get();
        } catch (Exception e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR, "Failed to read environment variables", e);
        }
        if (env == null || env.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : env.entrySet()) {
            merged.put(entry.getKey(), entry.getValue());
            merged.put(normalizeKey(entry.getKey()), entry.getValue());
        }
    }

    private void applySystemProperties(Map<String, String> merged) {
        if (!options.isLoadSystemProperties()) {
            return;
        }
        Properties props;
        try {
            props = options.getSystemPropertiesProvider().get();
        } catch (Exception e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR, "Failed to read system properties", e);
        }
        if (props == null || props.isEmpty()) {
            return;
        }
        for (String name : props.stringPropertyNames()) {
            String value = props.getProperty(name);
            merged.put(name, value);
            merged.put(normalizeKey(name), value);
        }
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
    }

    private VKConfigParser resolveParser(String fileName) {
        for (VKConfigParser parser : parsers) {
            if (parser.supports(fileName)) {
                return parser;
            }
        }
        return null;
    }

    private boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private void restartWatcher(Set<Path> roots) {
        stopWatcher();
        if (!options.isWatchEnabled() || roots.isEmpty()) {
            return;
        }

        try {
            WatchService ws = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keyMap = new ConcurrentHashMap<>();
            for (Path root : roots) {
                registerRecursive(root, ws, keyMap);
            }

            watchService = ws;
            Thread thread = new Thread(() -> watchLoop(ws, keyMap), "vostok-config-watch");
            thread.setDaemon(true);
            watchThread = thread;
            thread.start();
        } catch (Exception e) {
            lastWatchError = "Watch disabled due to error: " + e.getMessage();
            stopWatcher();
        }
    }

    private void watchLoop(WatchService ws, Map<WatchKey, Path> keyMap) {
        boolean dirty = false;
        long lastEventAt = 0L;
        long debounce = options.getWatchDebounceMs();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.poll(200, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                if (key != null) {
                    Path dir = keyMap.get(key);
                    if (dir != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                dirty = true;
                                lastEventAt = now;
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                            Path full = dir.resolve(pathEvent.context()).toAbsolutePath().normalize();

                            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(full)) {
                                registerRecursive(full, ws, keyMap);
                            }

                            if (isSupported(full.getFileName().toString())) {
                                dirty = true;
                                lastEventAt = now;
                            }
                        }
                    }
                    key.reset();
                }

                if (dirty && (now - lastEventAt) >= debounce) {
                    tryReloadFromWatcher();
                    dirty = false;
                }
            }
        } catch (Exception e) {
            lastWatchError = "Watch loop stopped: " + e.getMessage();
        }
    }

    private void tryReloadFromWatcher() {
        synchronized (LOCK) {
            Snapshot old = snapshot;
            try {
                reloadInternal(false);
                lastWatchError = null;
            } catch (Exception e) {
                snapshot = old;
                loaded = true;
                lastWatchError = "Hot reload failed, old snapshot kept: " + e.getMessage();
            }
        }
    }

    private void registerRecursive(Path root, WatchService ws, Map<WatchKey, Path> keyMap) {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    WatchKey key = dir.register(ws,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    keyMap.put(key, dir);
                } catch (IOException ignore) {
                }
            });
        } catch (Exception ignore) {
        }
    }

    private void stopWatcher() {
        Thread t = watchThread;
        watchThread = null;
        if (t != null) {
            t.interrupt();
        }

        WatchService ws = watchService;
        watchService = null;
        if (ws != null) {
            try {
                ws.close();
            } catch (Exception ignore) {
            }
        }
    }

    private record Snapshot(Map<String, String> data) {
        static Snapshot empty() {
            return new Snapshot(Collections.emptyMap());
        }
    }
}
