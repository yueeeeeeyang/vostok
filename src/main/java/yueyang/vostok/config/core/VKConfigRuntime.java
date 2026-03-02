package yueyang.vostok.config.core;

import yueyang.vostok.config.VKConfigOptions;
import yueyang.vostok.config.bind.VKConfigBinder;
import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;
import yueyang.vostok.config.listener.VKConfigChangeEvent;
import yueyang.vostok.config.listener.VKConfigChangeListener;
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
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Config 模块核心运行时，以单例形式持有配置状态。
 *
 * <h3>状态分层</h3>
 * <ol>
 *   <li>{@code baseData / baseSources}：文件 + env + sysProps 解析后的原始数据，
 *       不含 runtimeOverrides，由 {@link #reloadFiles()} 更新。</li>
 *   <li>{@code snapshot}：baseData + runtimeOverrides 经过插值（{@code ${key}}）
 *       和 Validator 验证后的最终快照，由 {@link #rebuildSnapshot()} 更新。</li>
 * </ol>
 *
 * <h3>性能优化</h3>
 * <ul>
 *   <li>override 变更（putOverride / removeOverride / clearOverrides）只触发
 *       {@link #rebuildSnapshot()}，不重扫文件，避免无谓的 IO。</li>
 *   <li>文件 watcher 仅在 watch roots 实际发生变化时重启，减少重复注册开销。</li>
 *   <li>env / sysProps 规范化 key 时，仅在原始 key 与规范化 key 不同时才额外写入，
 *       避免同一值被写两次。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * 所有写操作在 {@code LOCK} 内完成；读操作通过读取 volatile 快照实现无锁读，
 * 高并发读场景下不需要加锁。
 */
public class VKConfigRuntime {

    private static final Object LOCK = new Object();
    private static final VKConfigRuntime INSTANCE = new VKConfigRuntime();

    // ── 可变集合（CopyOnWriteArrayList 保证读路径无锁）────────────────────────
    private final CopyOnWriteArrayList<VKConfigParser>         parsers         = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VKConfigValidator>      validators      = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Path>                   manualFiles     = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VKConfigChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    /** runtimeOverrides 使用 ConcurrentHashMap，putOverride/removeOverride 无需加锁即可原子更新。 */
    private final Map<String, String> runtimeOverrides = new ConcurrentHashMap<>();

    // ── volatile 状态字段（写在 LOCK 内，读无锁）──────────────────────────────

    private volatile VKConfigOptions     options        = new VKConfigOptions();
    /** 文件 + env + sysProps 原始数据，不含 runtimeOverrides。 */
    private volatile Map<String, String> baseData       = Collections.emptyMap();
    /** baseData 中每个 key 对应的来源 sourceId（"env" / "system-property" / 绝对路径）。 */
    private volatile Map<String, String> baseSources    = Collections.emptyMap();
    /** 最终快照：interpolate(baseData + overrides)，经过 Validator 验证。 */
    private volatile Snapshot            snapshot       = Snapshot.empty();
    private volatile boolean             loaded;
    private volatile boolean             initialized;
    private volatile String              lastWatchError;

    // ── Watcher 相关 ─────────────────────────────────────────────────────────
    private volatile WatchService watchService;
    private volatile Thread       watchThread;
    /** 当前已注册的 watch root 集合，用于 Perf 2：仅在 roots 变化时重启 watcher。 */
    private volatile Set<Path>    activeWatchRoots = Set.of();

    private VKConfigRuntime() {
        parsers.add(new PropertiesConfigParser());
        parsers.add(new YamlConfigParser());
    }

    public static VKConfigRuntime getInstance() {
        return INSTANCE;
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    /**
     * 重置所有状态，包括 parsers、validators、listeners、overrides 等。
     * 同时停止 file watcher。
     */
    public void close() {
        synchronized (LOCK) {
            stopWatcher();
            snapshot       = Snapshot.empty();
            baseData       = Collections.emptyMap();
            baseSources    = Collections.emptyMap();
            options        = new VKConfigOptions();
            manualFiles.clear();
            runtimeOverrides.clear();
            validators.clear();
            changeListeners.clear();
            parsers.clear();
            parsers.add(new PropertiesConfigParser());
            parsers.add(new YamlConfigParser());
            loaded           = false;
            initialized      = false;
            lastWatchError   = null;
            activeWatchRoots = Set.of();
        }
    }

    /**
     * 幂等初始化：若已初始化则直接返回。
     * 需要重新初始化请使用 {@link #reinit}。
     */
    public void init(VKConfigOptions initOptions) {
        if (initOptions == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigOptions is null");
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            options = initOptions.copy();
            reloadFiles();
            initialized = true;
        }
    }

    /** 强制重新初始化，不受 initialized 状态限制。 */
    public void reinit(VKConfigOptions initOptions) {
        if (initOptions == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigOptions is null");
        }
        synchronized (LOCK) {
            options = initOptions.copy();
            reloadFiles();
            initialized = true;
        }
    }

    /**
     * 在当前 options 的副本上执行 customizer，然后应用新 options。
     * 若已加载则立即触发文件重新加载；未加载则只更新 options，等下次访问时懒加载。
     */
    public void configure(Consumer<VKConfigOptions> customizer) {
        if (customizer == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Customizer is null");
        }
        synchronized (LOCK) {
            VKConfigOptions next = options.copy();
            customizer.accept(next);
            options = next;
            if (loaded) {
                reloadFiles();
            }
        }
    }

    // ── Parser / Validator 注册 ───────────────────────────────────────────────

    /**
     * 注册自定义 Parser，插入到 parsers 列表头部（最高优先级）。
     * Bug 3 修复：仅在已加载时触发 reload，避免在 init() 前注册导致以默认 options 静默初始化。
     */
    public void registerParser(VKConfigParser parser) {
        if (parser == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "VKConfigParser is null");
        }
        parsers.add(0, parser);
        // 仅在已加载时重新解析；未加载时等待下次访问懒加载（届时使用新 parser）
        if (loaded) {
            reload();
        }
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

    // ── runtimeOverrides ─────────────────────────────────────────────────────

    /**
     * 设置运行时覆盖值。value 为 null 时等价于 removeOverride。
     * Bug 5 修复：未加载时仅更新 overrides map，不触发 rebuild（与 clearValidators 行为一致）。
     * 性能优化（Perf 1）：不重扫文件，仅调用 rebuildSnapshot。
     */
    public void putOverride(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Override key is blank");
        }
        if (value == null) {
            runtimeOverrides.remove(key);
        } else {
            runtimeOverrides.put(key, value);
        }
        if (loaded) {
            synchronized (LOCK) {
                rebuildSnapshot();
            }
        }
    }

    /**
     * Bug 5 修复：与 putOverride 保持一致，未加载时不触发 rebuild。
     */
    public void removeOverride(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        runtimeOverrides.remove(key);
        if (loaded) {
            synchronized (LOCK) {
                rebuildSnapshot();
            }
        }
    }

    /**
     * Bug 5 修复：与 clearValidators 保持一致，未加载时不触发 rebuild。
     */
    public void clearOverrides() {
        runtimeOverrides.clear();
        if (loaded) {
            synchronized (LOCK) {
                rebuildSnapshot();
            }
        }
    }

    // ── 手动文件管理 ──────────────────────────────────────────────────────────

    /**
     * 添加单个配置文件并重新加载。
     * Bug 2 修复：通过 resolveParser 判断文件格式是否受支持，而非硬编码扩展名，
     * 确保用户注册的自定义 parser 支持的文件格式也能通过校验。
     */
    public void addFile(String filePath) {
        addFileInternal(filePath);
        reload();
    }

    /**
     * 批量添加配置文件，整批完成后只触发一次 reload。
     * Bug 1 修复：原实现对每个文件调用 addFile()，导致 N 次完整 reload（含 N 次 watcher 重建）。
     */
    public void addFiles(Collection<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }
        for (String filePath : filePaths) {
            addFileInternal(filePath); // 只校验并收集，不触发 reload
        }
        reload(); // 批量添加完成后统一触发一次
    }

    public void clearManualFiles() {
        manualFiles.clear();
        reload();
    }

    /** 强制全量重新加载（扫描文件 + 重建快照 + 视情况重启 watcher）。 */
    public void reload() {
        synchronized (LOCK) {
            reloadFiles();
            initialized = true;
        }
    }

    // ── 变更监听 ──────────────────────────────────────────────────────────────

    public void addChangeListener(VKConfigChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(VKConfigChangeListener listener) {
        changeListeners.remove(listener);
    }

    // ── 读取 API ──────────────────────────────────────────────────────────────

    public boolean started() {
        return loaded || initialized;
    }

    public String lastWatchError() {
        return lastWatchError;
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

    // ── 来源追踪 ──────────────────────────────────────────────────────────────

    /**
     * 返回指定 key 的来源标识。
     *
     * @return 文件绝对路径、"env"、"system-property" 或 "runtime-override"；key 不存在时返回 null
     */
    public String sourceOf(String key) {
        return current().sources().get(key);
    }

    /** 返回完整的 key → source 映射（只读）。 */
    public Map<String, String> sources() {
        return current().sources();
    }

    // ── 类型安全绑定 ──────────────────────────────────────────────────────────

    public <T> T bind(String prefix, Class<T> type) {
        return VKConfigBinder.bind(prefix, type, current().data());
    }

    public <T> T bind(Class<T> type) {
        return VKConfigBinder.bind(type, current().data());
    }

    // ── 懒加载入口 ────────────────────────────────────────────────────────────

    /**
     * 返回当前有效快照。若尚未加载，则在 LOCK 内触发懒加载后返回。
     * 已加载时直接读取 volatile 字段，无需加锁（读路径无锁）。
     */
    private Snapshot current() {
        Snapshot local = snapshot;
        if (loaded) {
            return local;
        }
        synchronized (LOCK) {
            if (!loaded) {
                reloadFiles();
            }
            return snapshot;
        }
    }

    // ── 核心加载逻辑 ──────────────────────────────────────────────────────────

    /**
     * 全量文件加载：扫描文件、env、sysProps，更新 baseData/baseSources，
     * 调用 rebuildSnapshot，并视需要重启 file watcher。
     * 必须在持有 LOCK 的情况下调用。
     */
    private void reloadFiles() {
        VKConfigScanResult defaults = VKConfigScanner.scanDefaults(options);
        loadFromScanResult(defaults);
        manageWatcher(defaults.watchRoots());
    }

    /**
     * 根据扫描结果构建 baseData / baseSources，并调用 rebuildSnapshot。
     * 由 reloadFiles 和 tryReloadFromWatcher（不重启 watcher）共同使用。
     */
    private void loadFromScanResult(VKConfigScanResult defaults) {
        // 将手动添加的文件追加在扫描结果末尾（优先级最高的文件层）
        List<VKConfigSource> ordered = new ArrayList<>(defaults.defaultSources());
        for (Path file : manualFiles) {
            if (Files.exists(file) && Files.isRegularFile(file)) {
                ordered.add(VKConfigSource.ofFile(file));
            }
        }

        Map<String, String> newBaseData    = new LinkedHashMap<>();
        Map<String, String> newBaseSources = new LinkedHashMap<>();
        // strictNamespaceConflict 检查用的 namespace → sourceId 映射
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
                            "Namespace conflict: " + source.namespace() +
                            " from " + existing + " and " + source.sourceId());
                }
                namespaceSource.put(source.namespace(), source.sourceId());
            }

            Map<String, String> values = parser.parse(source.sourceId(), source.openStream());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String key = source.namespace() + "." + entry.getKey();
                newBaseData.put(key, entry.getValue());
                newBaseSources.put(key, source.sourceId());
            }
        }

        applyEnvironment(newBaseData, newBaseSources);
        applySystemProperties(newBaseData, newBaseSources);

        this.baseData    = Collections.unmodifiableMap(newBaseData);
        this.baseSources = Collections.unmodifiableMap(newBaseSources);

        rebuildSnapshot();
    }

    /**
     * 快速快照重建：在 baseData 基础上叠加 runtimeOverrides，执行插值和 Validator 校验。
     * 不重扫文件，适用于 override 变更场景（Perf 1 优化）。
     * 必须在持有 LOCK 的情况下调用。
     */
    private void rebuildSnapshot() {
        Snapshot oldSnapshot = this.snapshot;

        // 合并：baseData + runtimeOverrides（后者优先级更高）
        Map<String, String> rawMerged  = new LinkedHashMap<>(baseData);
        Map<String, String> rawSources = new LinkedHashMap<>(baseSources);

        if (!runtimeOverrides.isEmpty()) {
            for (Map.Entry<String, String> entry : runtimeOverrides.entrySet()) {
                rawMerged.put(entry.getKey(), entry.getValue());
                rawSources.put(entry.getKey(), "runtime-override");
            }
        }

        // 插值：递归解析 ${key} 引用
        Map<String, String> interpolated = resolveInterpolation(rawMerged);

        runValidators(interpolated);

        Snapshot newSnapshot = new Snapshot(
                Collections.unmodifiableMap(interpolated),
                Collections.unmodifiableMap(rawSources));
        this.snapshot = newSnapshot;
        this.loaded   = true;

        // 通知变更监听器
        notifyChanges(oldSnapshot, newSnapshot);
    }

    // ── 插值解析 ──────────────────────────────────────────────────────────────

    /**
     * 对整个 data map 执行 ${key} 插值。
     * 每个 key 单独解析，循环引用在单次解析链中检测。
     */
    private Map<String, String> resolveInterpolation(Map<String, String> data) {
        Map<String, String> resolved = new LinkedHashMap<>(data.size());
        for (Map.Entry<String, String> entry : data.entrySet()) {
            resolved.put(entry.getKey(),
                    resolveValue(entry.getValue(), data, new LinkedHashSet<>(), entry.getKey()));
        }
        return resolved;
    }

    /**
     * 递归解析单个值中的 ${ref} 占位符。
     *
     * <ul>
     *   <li>支持默认值语法：{@code ${key:defaultValue}}</li>
     *   <li>不完整的占位符（缺少 '}'）原样保留</li>
     *   <li>引用不存在且无默认值时原样保留 {@code ${key}}</li>
     *   <li>循环引用时抛出 {@link VKConfigException}</li>
     * </ul>
     *
     * @param value      待解析的值
     * @param data       完整 config map（未插值的原始 map）
     * @param chain      当前解析调用链（用于检测循环引用）
     * @param currentKey 当前正在解析的 key（用于错误消息）
     */
    private String resolveValue(String value, Map<String, String> data,
                                Set<String> chain, String currentKey) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start < 0) {
                sb.append(value, i, value.length());
                break;
            }
            sb.append(value, i, start);

            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                // 不完整的占位符，原样保留
                sb.append(value, start, value.length());
                break;
            }

            String ref        = value.substring(start + 2, end);
            String defaultVal = null;
            int colonIdx      = ref.indexOf(':');
            if (colonIdx >= 0) {
                defaultVal = ref.substring(colonIdx + 1);
                ref        = ref.substring(0, colonIdx);
            }

            if (chain.contains(ref)) {
                throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                        "Circular reference in config interpolation: " + currentKey + " → " + ref);
            }

            String refValue = data.get(ref);
            if (refValue == null) {
                sb.append(defaultVal != null ? defaultVal : "${" + ref + "}");
            } else {
                chain.add(ref);
                sb.append(resolveValue(refValue, data, chain, ref));
                chain.remove(ref);
            }
            i = end + 1;
        }
        return sb.toString();
    }

    // ── 变更通知 ──────────────────────────────────────────────────────────────

    /**
     * 比较新旧快照，收集实际变化的 key，并回调已注册的监听器。
     * 监听器异常被捕获并忽略，不影响配置主流程。
     */
    private void notifyChanges(Snapshot oldSnapshot, Snapshot newSnapshot) {
        if (changeListeners.isEmpty()) {
            return;
        }

        Map<String, String> oldData = oldSnapshot.data();
        Map<String, String> newData = newSnapshot.data();

        Set<String> changed = new LinkedHashSet<>();
        // 检查已有 key 的值是否变化（修改或删除）
        for (Map.Entry<String, String> entry : oldData.entrySet()) {
            if (!Objects.equals(entry.getValue(), newData.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        // 检查新增 key
        for (String key : newData.keySet()) {
            if (!oldData.containsKey(key)) {
                changed.add(key);
            }
        }

        if (changed.isEmpty()) {
            return;
        }

        VKConfigChangeEvent event = new VKConfigChangeEvent(changed, oldData, newData);
        for (VKConfigChangeListener listener : changeListeners) {
            try {
                listener.onChange(event);
            } catch (Exception ignore) {
                // 监听器异常不中断配置流程
            }
        }
    }

    // ── env / sysProps 注入 ───────────────────────────────────────────────────

    /**
     * 将环境变量注入到 merged map 中。
     * 每个 env key 同时以原始形式和规范化形式（大写→小写，_/-→.）写入，
     * 仅当两者不同时才写两次，避免重复写入。
     */
    private void applyEnvironment(Map<String, String> merged, Map<String, String> sources) {
        if (!options.isLoadEnv()) {
            return;
        }
        Map<String, String> env;
        try {
            env = options.getEnvProvider().get();
        } catch (Exception e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Failed to read environment variables", e);
        }
        if (env == null || env.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String origKey = entry.getKey();
            String normKey = normalizeKey(origKey);
            merged.put(origKey, entry.getValue());
            sources.put(origKey, "env");
            if (!origKey.equals(normKey)) {
                merged.put(normKey, entry.getValue());
                sources.put(normKey, "env");
            }
        }
    }

    /**
     * 将 System Properties 注入到 merged map 中，逻辑与 applyEnvironment 一致。
     */
    private void applySystemProperties(Map<String, String> merged, Map<String, String> sources) {
        if (!options.isLoadSystemProperties()) {
            return;
        }
        Properties props;
        try {
            props = options.getSystemPropertiesProvider().get();
        } catch (Exception e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Failed to read system properties", e);
        }
        if (props == null || props.isEmpty()) {
            return;
        }
        for (String name : props.stringPropertyNames()) {
            String value   = props.getProperty(name);
            String normKey = normalizeKey(name);
            merged.put(name, value);
            sources.put(name, "system-property");
            if (!name.equals(normKey)) {
                merged.put(normKey, value);
                sources.put(normKey, "system-property");
            }
        }
    }

    // ── Validator 执行 ────────────────────────────────────────────────────────

    private void runValidators(Map<String, String> data) {
        if (validators.isEmpty()) {
            return;
        }
        VKConfigView view = new VKConfigView(Collections.unmodifiableMap(data));
        for (VKConfigValidator validator : validators) {
            validator.validate(view);
        }
    }

    // ── Watcher 管理 ──────────────────────────────────────────────────────────

    /**
     * 根据 options 决定是否启动/重启/关闭 watcher。
     * Perf 2 优化：只有当 watch roots 实际发生变化时才重建 WatchService，
     * 避免每次 reload 都重走目录树注册监听。
     */
    private void manageWatcher(Set<Path> scanRoots) {
        if (!options.isWatchEnabled()) {
            if (watchService != null) {
                stopWatcher();
                activeWatchRoots = Set.of();
            }
            return;
        }

        Set<Path> newRoots = buildWatchRoots(scanRoots);
        // 若 roots 未变且 watcher 线程仍存活，跳过重建
        if (newRoots.equals(activeWatchRoots)
                && watchService != null
                && watchThread != null
                && watchThread.isAlive()) {
            return;
        }

        restartWatcher(newRoots);
        activeWatchRoots = newRoots;
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

    private void restartWatcher(Set<Path> roots) {
        stopWatcher();
        if (roots.isEmpty()) {
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

    /**
     * Watcher 事件循环，负责 debounce 防抖和触发 tryReloadFromWatcher。
     * Bug 4 修复：每轮循环从 volatile options 读取 debounceMs，反映 configure() 的动态修改，
     * 而非在线程启动时一次性快照。
     */
    private void watchLoop(WatchService ws, Map<WatchKey, Path> keyMap) {
        boolean dirty      = false;
        long    lastEventAt = 0L;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.poll(200, TimeUnit.MILLISECONDS);
                long     now = System.currentTimeMillis();

                if (key != null) {
                    Path dir = keyMap.get(key);
                    if (dir != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                dirty       = true;
                                lastEventAt = now;
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                            Path full = dir.resolve(pathEvent.context()).toAbsolutePath().normalize();

                            // 新建目录时递归注册子目录
                            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(full)) {
                                registerRecursive(full, ws, keyMap);
                            }

                            if (isSupported(full.getFileName().toString())) {
                                dirty       = true;
                                lastEventAt = now;
                            }
                        }
                    }
                    key.reset();
                }

                // Bug 4 修复：每次循环动态读取 debounceMs，而非启动时快照
                if (dirty && (now - lastEventAt) >= options.getWatchDebounceMs()) {
                    tryReloadFromWatcher();
                    dirty = false;
                }
            }
        } catch (Exception e) {
            lastWatchError = "Watch loop stopped: " + e.getMessage();
        }
    }

    /**
     * Watcher 触发的热重载：仅重新解析文件，不重启 watcher。
     * 发生异常时回滚到旧快照，并记录错误到 lastWatchError。
     */
    private void tryReloadFromWatcher() {
        synchronized (LOCK) {
            Snapshot            oldSnapshot = snapshot;
            Map<String, String> oldBase     = baseData;
            Map<String, String> oldSources  = baseSources;
            try {
                VKConfigScanResult defaults = VKConfigScanner.scanDefaults(options);
                loadFromScanResult(defaults); // 不调用 manageWatcher，避免 watcher 自重启
                lastWatchError = null;
            } catch (Exception e) {
                // 回滚：恢复旧快照和 baseData
                snapshot    = oldSnapshot;
                baseData    = oldBase;
                baseSources = oldSources;
                loaded      = true;
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
                    WatchKey k = dir.register(ws,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    keyMap.put(k, dir);
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

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    /**
     * Bug 2 修复：改为委托给 resolveParser，而非硬编码扩展名列表，
     * 确保用户注册的自定义 parser 所支持的格式也能通过 addFile 校验。
     */
    private boolean isSupported(String fileName) {
        return resolveParser(fileName) != null;
    }

    /** 遍历 parsers 列表，返回第一个支持该文件名的 parser；无匹配时返回 null。 */
    private VKConfigParser resolveParser(String fileName) {
        for (VKConfigParser parser : parsers) {
            if (parser.supports(fileName)) {
                return parser;
            }
        }
        return null;
    }

    /**
     * 规范化 key：trim → 全小写 → '_'/'-' 替换为 '.'。
     * 用于 env / sysProps 的大写 key 与 config 文件小写 key 互通。
     */
    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
    }

    /**
     * 校验并收集手动文件路径，不触发 reload（供 addFiles 批量使用）。
     */
    private void addFileInternal(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT, "Config file path is blank");
        }
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Config file does not exist: " + path);
        }
        if (!isSupported(path.getFileName().toString())) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Unsupported config file extension: " + path.getFileName());
        }
        manualFiles.add(path);
    }

    // ── 快照记录 ──────────────────────────────────────────────────────────────

    /**
     * 不可变快照，同时持有插值后的 data 和每个 key 的来源信息。
     *
     * @param data    插值后的最终 config map（只读）
     * @param sources key → sourceId 映射（只读）
     */
    private record Snapshot(Map<String, String> data, Map<String, String> sources) {
        static Snapshot empty() {
            return new Snapshot(Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
