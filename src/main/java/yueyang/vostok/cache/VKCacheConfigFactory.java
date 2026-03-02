package yueyang.vostok.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * VKCacheConfig 工厂工具类。
 * 提供从 Map 或 properties 文件按前缀加载配置的静态方法。
 */
public final class VKCacheConfigFactory {

    private VKCacheConfigFactory() {
    }

    /**
     * 从 Map 按指定前缀加载 VKCacheConfig。
     * Map 的 key 格式为 "{prefix}.{fieldName}"，例如 "cache.provider"。
     *
     * @param map    配置 Map
     * @param prefix 前缀（不含末尾的点）
     * @return 加载后的 VKCacheConfig
     */
    public static VKCacheConfig fromMap(Map<String, String> map, String prefix) {
        if (map == null) {
            return new VKCacheConfig();
        }
        String p = (prefix == null || prefix.isBlank()) ? "" : prefix.trim() + ".";
        VKCacheConfig cfg = new VKCacheConfig();
        applyEntries(cfg, key -> map.get(p + key));
        return cfg;
    }

    /**
     * 从 properties 文件按指定前缀加载 VKCacheConfig。
     * 文件中的 key 格式为 "{prefix}.{fieldName}"，例如 "cache.provider"。
     *
     * @param file   properties 文件路径
     * @param prefix 前缀（不含末尾的点）
     * @return 加载后的 VKCacheConfig
     */
    public static VKCacheConfig fromProperties(Path file, String prefix) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load cache config from file: " + file, e);
        }
        String p = (prefix == null || prefix.isBlank()) ? "" : prefix.trim() + ".";
        VKCacheConfig cfg = new VKCacheConfig();
        applyEntries(cfg, key -> props.getProperty(p + key));
        return cfg;
    }

    @FunctionalInterface
    private interface ValueLookup {
        String get(String key);
    }

    private static void applyEntries(VKCacheConfig cfg, ValueLookup lookup) {
        String v;

        if ((v = lookup.get("provider")) != null) {
            cfg.providerType(VKCacheProviderType.from(v));
        }
        if ((v = lookup.get("endpoints")) != null && !v.isBlank()) {
            cfg.endpoints(v.split(","));
        }
        if ((v = lookup.get("redisMode")) != null) {
            try {
                cfg.redisMode(VKRedisMode.valueOf(v.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if ((v = lookup.get("sentinelMaster")) != null) {
            cfg.sentinelMaster(v);
        }
        if ((v = lookup.get("clusterVirtualNodes")) != null) {
            parseIntSafe(v, cfg::clusterVirtualNodes);
        }
        if ((v = lookup.get("username")) != null) {
            cfg.username(v);
        }
        if ((v = lookup.get("password")) != null) {
            cfg.password(v);
        }
        if ((v = lookup.get("database")) != null) {
            parseIntSafe(v, cfg::database);
        }
        if ((v = lookup.get("ssl")) != null) {
            cfg.ssl(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("connectTimeoutMs")) != null) {
            parseIntSafe(v, cfg::connectTimeoutMs);
        }
        if ((v = lookup.get("readTimeoutMs")) != null) {
            parseIntSafe(v, cfg::readTimeoutMs);
        }
        if ((v = lookup.get("heartbeatIntervalMs")) != null) {
            parseIntSafe(v, cfg::heartbeatIntervalMs);
        }
        if ((v = lookup.get("reconnectMaxAttempts")) != null) {
            parseIntSafe(v, cfg::reconnectMaxAttempts);
        }
        if ((v = lookup.get("minIdle")) != null) {
            parseIntSafe(v, cfg::minIdle);
        }
        if ((v = lookup.get("maxActive")) != null) {
            parseIntSafe(v, cfg::maxActive);
        }
        if ((v = lookup.get("maxWaitMs")) != null) {
            parseLongSafe(v, cfg::maxWaitMs);
        }
        if ((v = lookup.get("testOnBorrow")) != null) {
            cfg.testOnBorrow(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("testOnReturn")) != null) {
            cfg.testOnReturn(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("idleValidationIntervalMs")) != null) {
            parseLongSafe(v, cfg::idleValidationIntervalMs);
        }
        if ((v = lookup.get("idleTimeoutMs")) != null) {
            parseLongSafe(v, cfg::idleTimeoutMs);
        }
        if ((v = lookup.get("leakDetectMs")) != null) {
            parseLongSafe(v, cfg::leakDetectMs);
        }
        if ((v = lookup.get("retryEnabled")) != null) {
            cfg.retryEnabled(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("maxRetries")) != null) {
            parseIntSafe(v, cfg::maxRetries);
        }
        if ((v = lookup.get("retryBackoffBaseMs")) != null) {
            parseLongSafe(v, cfg::retryBackoffBaseMs);
        }
        if ((v = lookup.get("retryBackoffMaxMs")) != null) {
            parseLongSafe(v, cfg::retryBackoffMaxMs);
        }
        if ((v = lookup.get("retryJitterEnabled")) != null) {
            cfg.retryJitterEnabled(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("defaultTtlMs")) != null) {
            parseLongSafe(v, cfg::defaultTtlMs);
        }
        if ((v = lookup.get("ttlJitterMs")) != null) {
            parseLongSafe(v, cfg::ttlJitterMs);
        }
        if ((v = lookup.get("keyPrefix")) != null) {
            cfg.keyPrefix(v);
        }
        if ((v = lookup.get("codec")) != null) {
            cfg.codec(v);
        }
        if ((v = lookup.get("metricsEnabled")) != null) {
            cfg.metricsEnabled(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("nullCacheEnabled")) != null) {
            cfg.nullCacheEnabled(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("nullCacheTtlMs")) != null) {
            parseLongSafe(v, cfg::nullCacheTtlMs);
        }
        if ((v = lookup.get("singleFlightEnabled")) != null) {
            cfg.singleFlightEnabled(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("keyMutexEnabled")) != null) {
            cfg.keyMutexEnabled(Boolean.parseBoolean(v.trim()));
        }
        if ((v = lookup.get("keyMutexMaxSize")) != null) {
            parseIntSafe(v, cfg::keyMutexMaxSize);
        }
        if ((v = lookup.get("rateLimitQps")) != null) {
            parseIntSafe(v, cfg::rateLimitQps);
        }
        if ((v = lookup.get("degradePolicy")) != null) {
            try {
                cfg.degradePolicy(VKCacheDegradePolicy.valueOf(v.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if ((v = lookup.get("maxEntries")) != null) {
            parseIntSafe(v, cfg::maxEntries);
        }
        if ((v = lookup.get("evictionPolicy")) != null) {
            try {
                cfg.evictionPolicy(VKEvictionPolicy.valueOf(v.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if ((v = lookup.get("memoryEvictionIntervalMs")) != null) {
            parseLongSafe(v, cfg::memoryEvictionIntervalMs);
        }
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    @FunctionalInterface
    private interface LongSetter {
        void set(long value);
    }

    private static void parseIntSafe(String value, IntSetter setter) {
        try {
            setter.set(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void parseLongSafe(String value, LongSetter setter) {
        try {
            setter.set(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
        }
    }
}
