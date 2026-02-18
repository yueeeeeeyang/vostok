package yueyang.vostok.config;

import yueyang.vostok.config.core.VKConfigRuntime;
import yueyang.vostok.config.validate.VKConfigValidator;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class VostokConfig {
    private static final VKConfigRuntime RUNTIME = VKConfigRuntime.getInstance();

    protected VostokConfig() {
    }

    public static void init() {
        RUNTIME.init(new VKConfigOptions());
    }

    public static void init(VKConfigOptions options) {
        RUNTIME.init(options);
    }

    public static void reinit(VKConfigOptions options) {
        RUNTIME.reinit(options);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static String lastWatchError() {
        return RUNTIME.lastWatchError();
    }

    public static void reload() {
        RUNTIME.reload();
    }

    public static void close() {
        RUNTIME.close();
    }

    public static void configure(Consumer<VKConfigOptions> customizer) {
        RUNTIME.configure(customizer);
    }

    public static void registerParser(yueyang.vostok.config.parser.VKConfigParser parser) {
        RUNTIME.registerParser(parser);
    }

    public static void registerValidator(VKConfigValidator validator) {
        RUNTIME.registerValidator(validator);
    }

    public static void clearValidators() {
        RUNTIME.clearValidators();
    }

    public static void putOverride(String key, String value) {
        RUNTIME.putOverride(key, value);
    }

    public static void removeOverride(String key) {
        RUNTIME.removeOverride(key);
    }

    public static void clearOverrides() {
        RUNTIME.clearOverrides();
    }

    public static void addFile(String path) {
        RUNTIME.addFile(path);
    }

    public static void addFiles(String... paths) {
        if (paths == null || paths.length == 0) {
            return;
        }
        RUNTIME.addFiles(Arrays.asList(paths));
    }

    public static void clearManualFiles() {
        RUNTIME.clearManualFiles();
    }

    public static String get(String key) {
        return RUNTIME.get(key);
    }

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
        String v = value.trim().toLowerCase();
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
}
