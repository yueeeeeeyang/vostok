package yueyang.vostok.cache;

import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class VKCacheConfigFactory {
    private VKCacheConfigFactory() {
    }

    public static VKCacheConfig fromEnv(String prefix) {
        return fromMap(System.getenv(), prefix);
    }

    public static VKCacheConfig fromProperties(Path path, String prefix) {
        if (path == null) {
            throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT, "Properties path is null");
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Properties file does not exist: " + path);
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR,
                    "Failed to load properties: " + path, e);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return fromMap(map, prefix);
    }

    public static VKCacheConfig fromMap(Map<String, String> map, String prefix) {
        Map<String, String> source = map == null ? Map.of() : map;
        String p = normalizePrefix(prefix);

        VKCacheConfig cfg = new VKCacheConfig();
        cfg.providerType(VKCacheProviderType.from(read(source, p, "provider", cfg.getProviderType().name())));
        cfg.endpoints(splitCsv(read(source, p, "endpoints", String.join(",", cfg.getEndpoints()))));
        cfg.username(read(source, p, "username", cfg.getUsername()));
        cfg.password(read(source, p, "password", cfg.getPassword()));
        cfg.database(readInt(source, p, "database", cfg.getDatabase()));
        cfg.ssl(readBool(source, p, "ssl", cfg.isSsl()));
        cfg.connectTimeoutMs(readInt(source, p, "connectTimeoutMs", cfg.getConnectTimeoutMs()));
        cfg.readTimeoutMs(readInt(source, p, "readTimeoutMs", cfg.getReadTimeoutMs()));

        cfg.minIdle(readInt(source, p, "minIdle", cfg.getMinIdle()));
        cfg.maxActive(readInt(source, p, "maxActive", cfg.getMaxActive()));
        cfg.maxWaitMs(readLong(source, p, "maxWaitMs", cfg.getMaxWaitMs()));
        cfg.testOnBorrow(readBool(source, p, "testOnBorrow", cfg.isTestOnBorrow()));
        cfg.testOnReturn(readBool(source, p, "testOnReturn", cfg.isTestOnReturn()));
        cfg.idleValidationIntervalMs(readLong(source, p, "idleValidationIntervalMs", cfg.getIdleValidationIntervalMs()));
        cfg.idleTimeoutMs(readLong(source, p, "idleTimeoutMs", cfg.getIdleTimeoutMs()));

        cfg.retryEnabled(readBool(source, p, "retryEnabled", cfg.isRetryEnabled()));
        cfg.maxRetries(readInt(source, p, "maxRetries", cfg.getMaxRetries()));
        cfg.retryBackoffBaseMs(readLong(source, p, "retryBackoffBaseMs", cfg.getRetryBackoffBaseMs()));
        cfg.retryBackoffMaxMs(readLong(source, p, "retryBackoffMaxMs", cfg.getRetryBackoffMaxMs()));

        cfg.defaultTtlMs(readLong(source, p, "defaultTtlMs", cfg.getDefaultTtlMs()));
        cfg.keyPrefix(read(source, p, "keyPrefix", cfg.getKeyPrefix()));
        cfg.codec(read(source, p, "codec", cfg.getCodec()));
        cfg.metricsEnabled(readBool(source, p, "metricsEnabled", cfg.isMetricsEnabled()));
        return cfg;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String p = prefix.trim();
        if (!p.endsWith(".")) {
            p += ".";
        }
        return p;
    }

    private static String read(Map<String, String> source, String prefix, String key, String defaultValue) {
        String v = source.get(prefix + key);
        return v == null ? defaultValue : v;
    }

    private static int readInt(Map<String, String> source, String prefix, String key, int defaultValue) {
        String v = read(source, prefix, key, null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static long readLong(Map<String, String> source, String prefix, String key, long defaultValue) {
        String v = read(source, prefix, key, null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static boolean readBool(Map<String, String> source, String prefix, String key, boolean defaultValue) {
        String v = read(source, prefix, key, null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        String s = v.trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)) {
            return true;
        }
        if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s)) {
            return false;
        }
        return defaultValue;
    }

    private static String[] splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .toArray(String[]::new);
    }
}
