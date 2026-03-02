package yueyang.vostok.config;

import yueyang.vostok.config.bind.VKConfigPrefix;
import yueyang.vostok.config.core.VKConfigRuntime;
import yueyang.vostok.config.listener.VKConfigChangeListener;
import yueyang.vostok.config.validate.VKConfigValidator;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Config 模块公开门面类（Facade）。
 * <p>
 * 所有方法均为静态方法，通过委托到单例 {@link VKConfigRuntime} 实现。
 * 不应直接实例化此类，请通过 {@code Vostok.Config} 或直接调用静态方法访问。
 *
 * <h3>优先级（从低到高）</h3>
 * <ol>
 *   <li>classpath 配置文件</li>
 *   <li>extraScanDirs 配置文件</li>
 *   <li>userDir 配置文件</li>
 *   <li>手动 addFile 添加的文件</li>
 *   <li>环境变量（LOAD_ENV）</li>
 *   <li>System Properties（LOAD_SYSTEM_PROPERTIES）</li>
 *   <li>runtimeOverrides（putOverride）</li>
 * </ol>
 *
 * <h3>插值语法</h3>
 * config 值中可使用 {@code ${key}} 或 {@code ${key:defaultValue}} 引用其他 key，
 * 循环引用会在加载时抛出 {@link yueyang.vostok.config.exception.VKConfigException}。
 */
public class VostokConfig {

    private static final VKConfigRuntime RUNTIME = VKConfigRuntime.getInstance();

    protected VostokConfig() {
    }

    // ── 生命周期 ──────────────────────────────────────────────────────────────

    /** 使用默认 options 初始化（幂等）。 */
    public static void init() {
        RUNTIME.init(new VKConfigOptions());
    }

    /** 使用指定 options 初始化（幂等，已初始化则忽略）。 */
    public static void init(VKConfigOptions options) {
        RUNTIME.init(options);
    }

    /** 强制重新初始化，忽略之前的 initialized 状态。 */
    public static void reinit(VKConfigOptions options) {
        RUNTIME.reinit(options);
    }

    /** 修改当前 options（对已加载状态立即生效并触发 reload）。 */
    public static void configure(Consumer<VKConfigOptions> customizer) {
        RUNTIME.configure(customizer);
    }

    /** 判断是否已初始化或已加载。 */
    public static boolean started() {
        return RUNTIME.started();
    }

    /** 返回上一次 file watcher 发生的错误信息；无错误时返回 null。 */
    public static String lastWatchError() {
        return RUNTIME.lastWatchError();
    }

    /** 强制全量重新加载配置。 */
    public static void reload() {
        RUNTIME.reload();
    }

    /** 关闭并重置所有状态（含 watcher、listeners、validators、parsers）。 */
    public static void close() {
        RUNTIME.close();
    }

    // ── Parser / Validator ───────────────────────────────────────────────────

    /**
     * 注册自定义解析器，插入到内置 parsers 之前（优先级最高）。
     * 注册后自定义格式的文件可通过 {@link #addFile} 加载。
     */
    public static void registerParser(yueyang.vostok.config.parser.VKConfigParser parser) {
        RUNTIME.registerParser(parser);
    }

    public static void registerValidator(VKConfigValidator validator) {
        RUNTIME.registerValidator(validator);
    }

    public static void clearValidators() {
        RUNTIME.clearValidators();
    }

    // ── runtimeOverrides ─────────────────────────────────────────────────────

    /**
     * 设置运行时覆盖值（最高优先级）。value 为 null 时等价于 removeOverride。
     * 不触发文件重扫，仅重建快照（性能友好）。
     */
    public static void putOverride(String key, String value) {
        RUNTIME.putOverride(key, value);
    }

    public static void removeOverride(String key) {
        RUNTIME.removeOverride(key);
    }

    public static void clearOverrides() {
        RUNTIME.clearOverrides();
    }

    // ── 手动文件管理 ──────────────────────────────────────────────────────────

    /**
     * 添加单个配置文件（文件必须已存在且格式受支持，含用户自定义 parser 格式）。
     */
    public static void addFile(String path) {
        RUNTIME.addFile(path);
    }

    /**
     * 批量添加配置文件，全部校验完成后触发一次 reload（而非 N 次）。
     */
    public static void addFiles(String... paths) {
        if (paths == null || paths.length == 0) {
            return;
        }
        RUNTIME.addFiles(Arrays.asList(paths));
    }

    public static void clearManualFiles() {
        RUNTIME.clearManualFiles();
    }

    // ── 变更监听 ──────────────────────────────────────────────────────────────

    /**
     * 注册全量变更监听器，每次快照发生实质变化后回调。
     *
     * @param listener 监听器，不应阻塞；异常会被捕获并忽略
     */
    public static void addChangeListener(VKConfigChangeListener listener) {
        RUNTIME.addChangeListener(listener);
    }

    public static void removeChangeListener(VKConfigChangeListener listener) {
        RUNTIME.removeChangeListener(listener);
    }

    /**
     * 便捷方法：仅当指定 key 发生变化时回调。返回创建的监听器实例，可用于后续 remove。
     *
     * <pre>{@code
     * var listener = VostokConfig.onChange("db.url", (old, next) -> reconnect(next));
     * // 不再需要时：
     * VostokConfig.removeChangeListener(listener);
     * }</pre>
     *
     * @param key      关注的 config key
     * @param callback 变更回调，参数为 (oldValue, newValue)；值不存在时为 null
     * @return 已注册的监听器（可用于取消注册）
     */
    public static VKConfigChangeListener onChange(String key, BiConsumer<String, String> callback) {
        if (key == null || callback == null) {
            throw new yueyang.vostok.config.exception.VKConfigException(
                    yueyang.vostok.config.exception.VKConfigErrorCode.INVALID_ARGUMENT,
                    "key and callback must not be null");
        }
        VKConfigChangeListener listener = event -> {
            if (event.changedKeys().contains(key)) {
                callback.accept(event.oldValue(key), event.newValue(key));
            }
        };
        RUNTIME.addChangeListener(listener);
        return listener;
    }

    // ── 读取 API ──────────────────────────────────────────────────────────────

    /** 获取原始字符串值，key 不存在时返回 null。 */
    public static String get(String key) {
        return RUNTIME.get(key);
    }

    /**
     * 获取字符串值，key 不存在时抛出 {@link yueyang.vostok.config.exception.VKConfigException}。
     */
    public static String required(String key) {
        return RUNTIME.required(key);
    }

    public static boolean has(String key) {
        return RUNTIME.has(key);
    }

    public static Set<String> keys() {
        return RUNTIME.keys();
    }

    public static String getString(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBool(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
            return false;
        }
        return defaultValue;
    }

    public static List<String> getList(String key) {
        String direct = get(key);
        if (direct != null) {
            if (direct.isBlank()) {
                return List.of();
            }
            return Arrays.stream(direct.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int i = 0; ; i++) {
            String v = get(key + "[" + i + "]");
            if (v == null) {
                break;
            }
            out.add(v);
        }
        return out;
    }

    // ── 来源追踪 ──────────────────────────────────────────────────────────────

    /**
     * 返回指定 key 的来源描述。
     *
     * @return 文件绝对路径、{@code "env"}、{@code "system-property"}、
     *         {@code "runtime-override"}；key 不存在时返回 null
     */
    public static String sourceOf(String key) {
        return RUNTIME.sourceOf(key);
    }

    /**
     * 返回完整的 key → source 映射（只读快照）。
     * 可用于调试时输出每个 config key 的来源。
     */
    public static Map<String, String> sources() {
        return RUNTIME.sources();
    }

    // ── 类型安全绑定 ──────────────────────────────────────────────────────────

    /**
     * 将 config 中前缀为 prefix 的 key 绑定到 type 的新实例。
     *
     * <pre>{@code
     * public class DbConfig {
     *     private String host;
     *     private int port;
     * }
     * DbConfig db = VostokConfig.bind("database", DbConfig.class);
     * }</pre>
     *
     * @param prefix config key 前缀（不含尾部 '.'）
     * @param type   目标 POJO 类，需有无参构造器
     */
    public static <T> T bind(String prefix, Class<T> type) {
        return RUNTIME.bind(prefix, type);
    }

    /**
     * 从带 {@link VKConfigPrefix} 注解的 POJO 类自动读取前缀后绑定。
     *
     * <pre>{@code
     * @VKConfigPrefix("database")
     * public class DbConfig { ... }
     *
     * DbConfig db = VostokConfig.bind(DbConfig.class);
     * }</pre>
     */
    public static <T> T bind(Class<T> type) {
        return RUNTIME.bind(type);
    }
}
