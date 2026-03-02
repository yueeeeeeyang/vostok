package yueyang.vostok.cache;

import yueyang.vostok.cache.event.VKCacheEventListener;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class VKCacheConfig {
    private VKCacheProviderType providerType = VKCacheProviderType.MEMORY;
    private String[] endpoints = new String[]{"127.0.0.1:6379"};
    private VKRedisMode redisMode = VKRedisMode.SINGLE;
    private String sentinelMaster = "mymaster";
    private int clusterVirtualNodes = 128;

    private String username;
    private String password;
    private int database = 0;
    private boolean ssl = false;

    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 2000;
    private int heartbeatIntervalMs = 15000;
    private int reconnectMaxAttempts = 2;

    private int minIdle = 1;
    private int maxActive = 8;
    private long maxWaitMs = 3000;
    private boolean testOnBorrow = true;
    private boolean testOnReturn = false;
    private long idleValidationIntervalMs = 30000;
    private long idleTimeoutMs = 120000;
    private long leakDetectMs = 60000;

    private boolean retryEnabled = true;
    private int maxRetries = 2;
    private long retryBackoffBaseMs = 30;
    private long retryBackoffMaxMs = 500;
    private boolean retryJitterEnabled = true;

    private long defaultTtlMs = 0;
    private long ttlJitterMs = 0;
    private String keyPrefix = "";
    private String codec = "json";
    private boolean metricsEnabled = true;

    private boolean nullCacheEnabled = true;
    private long nullCacheTtlMs = 30000;
    private boolean singleFlightEnabled = true;
    private boolean keyMutexEnabled = true;
    private int keyMutexMaxSize = 10000;

    private int rateLimitQps = 0;
    private VKCacheDegradePolicy degradePolicy = VKCacheDegradePolicy.FAIL_FAST;

    private transient VKBloomFilter bloomFilter = VKBloomFilter.noOp();

    // ---------- Feature2：容量 + 淘汰策略 ----------
    /** 内存 Provider 最大条目数，0 表示不限。 */
    private int maxEntries = 0;
    /** 淘汰策略，默认 NONE（不主动淘汰）。 */
    private VKEvictionPolicy evictionPolicy = VKEvictionPolicy.NONE;
    /** 后台驱逐线程扫描周期（毫秒），默认 5000ms。 */
    private long memoryEvictionIntervalMs = 5000;

    // ---------- Feature1：两级缓存 ----------
    /** TIERED 模式的 L1（内存）配置，null 时使用默认内存配置。 */
    private VKCacheConfig l1Config = null;
    /** TIERED 模式的 L2（下层，如 Redis）配置。 */
    private VKCacheConfig l2Config = null;

    // ---------- Feature5：事件监听器 ----------
    /** 缓存事件监听器，null 表示不启用。 */
    private transient VKCacheEventListener eventListener = null;

    private Map<String, String> options = new LinkedHashMap<>();

    public VKCacheProviderType getProviderType() {
        return providerType;
    }

    public VKCacheConfig providerType(VKCacheProviderType providerType) {
        this.providerType = providerType == null ? VKCacheProviderType.MEMORY : providerType;
        return this;
    }

    public String[] getEndpoints() {
        return endpoints == null ? new String[0] : endpoints.clone();
    }

    public VKCacheConfig endpoints(String... endpoints) {
        this.endpoints = endpoints == null ? new String[0] : endpoints.clone();
        return this;
    }

    public VKRedisMode getRedisMode() {
        return redisMode;
    }

    public VKCacheConfig redisMode(VKRedisMode redisMode) {
        this.redisMode = redisMode == null ? VKRedisMode.SINGLE : redisMode;
        return this;
    }

    public String getSentinelMaster() {
        return sentinelMaster;
    }

    public VKCacheConfig sentinelMaster(String sentinelMaster) {
        this.sentinelMaster = sentinelMaster == null ? "mymaster" : sentinelMaster;
        return this;
    }

    public int getClusterVirtualNodes() {
        return clusterVirtualNodes;
    }

    public VKCacheConfig clusterVirtualNodes(int clusterVirtualNodes) {
        this.clusterVirtualNodes = clusterVirtualNodes;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public VKCacheConfig username(String username) {
        this.username = username;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public VKCacheConfig password(String password) {
        this.password = password;
        return this;
    }

    public int getDatabase() {
        return database;
    }

    public VKCacheConfig database(int database) {
        this.database = database;
        return this;
    }

    public boolean isSsl() {
        return ssl;
    }

    public VKCacheConfig ssl(boolean ssl) {
        this.ssl = ssl;
        return this;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public VKCacheConfig connectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        return this;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public VKCacheConfig readTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
        return this;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public VKCacheConfig heartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        return this;
    }

    public int getReconnectMaxAttempts() {
        return reconnectMaxAttempts;
    }

    public VKCacheConfig reconnectMaxAttempts(int reconnectMaxAttempts) {
        this.reconnectMaxAttempts = reconnectMaxAttempts;
        return this;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public VKCacheConfig minIdle(int minIdle) {
        this.minIdle = minIdle;
        return this;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public VKCacheConfig maxActive(int maxActive) {
        this.maxActive = maxActive;
        return this;
    }

    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    public VKCacheConfig maxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
        return this;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public VKCacheConfig testOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        return this;
    }

    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    public VKCacheConfig testOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        return this;
    }

    public long getIdleValidationIntervalMs() {
        return idleValidationIntervalMs;
    }

    public VKCacheConfig idleValidationIntervalMs(long idleValidationIntervalMs) {
        this.idleValidationIntervalMs = idleValidationIntervalMs;
        return this;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public VKCacheConfig idleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
        return this;
    }

    public long getLeakDetectMs() {
        return leakDetectMs;
    }

    public VKCacheConfig leakDetectMs(long leakDetectMs) {
        this.leakDetectMs = leakDetectMs;
        return this;
    }

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public VKCacheConfig retryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
        return this;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public VKCacheConfig maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public long getRetryBackoffBaseMs() {
        return retryBackoffBaseMs;
    }

    public VKCacheConfig retryBackoffBaseMs(long retryBackoffBaseMs) {
        this.retryBackoffBaseMs = retryBackoffBaseMs;
        return this;
    }

    public long getRetryBackoffMaxMs() {
        return retryBackoffMaxMs;
    }

    public VKCacheConfig retryBackoffMaxMs(long retryBackoffMaxMs) {
        this.retryBackoffMaxMs = retryBackoffMaxMs;
        return this;
    }

    public boolean isRetryJitterEnabled() {
        return retryJitterEnabled;
    }

    public VKCacheConfig retryJitterEnabled(boolean retryJitterEnabled) {
        this.retryJitterEnabled = retryJitterEnabled;
        return this;
    }

    public long getDefaultTtlMs() {
        return defaultTtlMs;
    }

    public VKCacheConfig defaultTtlMs(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
        return this;
    }

    public long getTtlJitterMs() {
        return ttlJitterMs;
    }

    public VKCacheConfig ttlJitterMs(long ttlJitterMs) {
        this.ttlJitterMs = ttlJitterMs;
        return this;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public VKCacheConfig keyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        return this;
    }

    public String getCodec() {
        return codec;
    }

    public VKCacheConfig codec(String codec) {
        this.codec = codec == null ? "json" : codec;
        return this;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public VKCacheConfig metricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public boolean isNullCacheEnabled() {
        return nullCacheEnabled;
    }

    public VKCacheConfig nullCacheEnabled(boolean nullCacheEnabled) {
        this.nullCacheEnabled = nullCacheEnabled;
        return this;
    }

    public long getNullCacheTtlMs() {
        return nullCacheTtlMs;
    }

    public VKCacheConfig nullCacheTtlMs(long nullCacheTtlMs) {
        this.nullCacheTtlMs = nullCacheTtlMs;
        return this;
    }

    public boolean isSingleFlightEnabled() {
        return singleFlightEnabled;
    }

    public VKCacheConfig singleFlightEnabled(boolean singleFlightEnabled) {
        this.singleFlightEnabled = singleFlightEnabled;
        return this;
    }

    public boolean isKeyMutexEnabled() {
        return keyMutexEnabled;
    }

    public VKCacheConfig keyMutexEnabled(boolean keyMutexEnabled) {
        this.keyMutexEnabled = keyMutexEnabled;
        return this;
    }

    public int getKeyMutexMaxSize() {
        return keyMutexMaxSize;
    }

    public VKCacheConfig keyMutexMaxSize(int keyMutexMaxSize) {
        this.keyMutexMaxSize = keyMutexMaxSize;
        return this;
    }

    public int getRateLimitQps() {
        return rateLimitQps;
    }

    public VKCacheConfig rateLimitQps(int rateLimitQps) {
        this.rateLimitQps = rateLimitQps;
        return this;
    }

    public VKCacheDegradePolicy getDegradePolicy() {
        return degradePolicy;
    }

    public VKCacheConfig degradePolicy(VKCacheDegradePolicy degradePolicy) {
        this.degradePolicy = degradePolicy == null ? VKCacheDegradePolicy.FAIL_FAST : degradePolicy;
        return this;
    }

    public VKBloomFilter getBloomFilter() {
        return bloomFilter;
    }

    public VKCacheConfig bloomFilter(VKBloomFilter bloomFilter) {
        this.bloomFilter = bloomFilter == null ? VKBloomFilter.noOp() : bloomFilter;
        return this;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public VKCacheConfig maxEntries(int maxEntries) {
        this.maxEntries = Math.max(0, maxEntries);
        return this;
    }

    public VKEvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public VKCacheConfig evictionPolicy(VKEvictionPolicy evictionPolicy) {
        this.evictionPolicy = evictionPolicy == null ? VKEvictionPolicy.NONE : evictionPolicy;
        return this;
    }

    public long getMemoryEvictionIntervalMs() {
        return memoryEvictionIntervalMs;
    }

    public VKCacheConfig memoryEvictionIntervalMs(long memoryEvictionIntervalMs) {
        this.memoryEvictionIntervalMs = memoryEvictionIntervalMs <= 0 ? 5000 : memoryEvictionIntervalMs;
        return this;
    }

    public VKCacheConfig getL1Config() {
        return l1Config;
    }

    public VKCacheConfig l1Config(VKCacheConfig l1Config) {
        this.l1Config = l1Config;
        return this;
    }

    public VKCacheConfig getL2Config() {
        return l2Config;
    }

    public VKCacheConfig l2Config(VKCacheConfig l2Config) {
        this.l2Config = l2Config;
        return this;
    }

    public VKCacheEventListener getEventListener() {
        return eventListener;
    }

    public VKCacheConfig eventListener(VKCacheEventListener eventListener) {
        this.eventListener = eventListener;
        return this;
    }

    public Map<String, String> getOptions() {
        return new LinkedHashMap<>(options);
    }

    public VKCacheConfig option(String key, String value) {
        if (key != null && !key.isBlank()) {
            if (value == null) {
                options.remove(key);
            } else {
                options.put(key, value);
            }
        }
        return this;
    }

    public VKCacheConfig options(Map<String, String> options) {
        this.options = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
        return this;
    }

    public VKCacheConfig copy() {
        return new VKCacheConfig()
                .providerType(providerType)
                .endpoints(getEndpoints())
                .redisMode(redisMode)
                .sentinelMaster(sentinelMaster)
                .clusterVirtualNodes(clusterVirtualNodes)
                .username(username)
                .password(password)
                .database(database)
                .ssl(ssl)
                .connectTimeoutMs(connectTimeoutMs)
                .readTimeoutMs(readTimeoutMs)
                .heartbeatIntervalMs(heartbeatIntervalMs)
                .reconnectMaxAttempts(reconnectMaxAttempts)
                .minIdle(minIdle)
                .maxActive(maxActive)
                .maxWaitMs(maxWaitMs)
                .testOnBorrow(testOnBorrow)
                .testOnReturn(testOnReturn)
                .idleValidationIntervalMs(idleValidationIntervalMs)
                .idleTimeoutMs(idleTimeoutMs)
                .leakDetectMs(leakDetectMs)
                .retryEnabled(retryEnabled)
                .maxRetries(maxRetries)
                .retryBackoffBaseMs(retryBackoffBaseMs)
                .retryBackoffMaxMs(retryBackoffMaxMs)
                .retryJitterEnabled(retryJitterEnabled)
                .defaultTtlMs(defaultTtlMs)
                .ttlJitterMs(ttlJitterMs)
                .keyPrefix(keyPrefix)
                .codec(codec)
                .metricsEnabled(metricsEnabled)
                .nullCacheEnabled(nullCacheEnabled)
                .nullCacheTtlMs(nullCacheTtlMs)
                .singleFlightEnabled(singleFlightEnabled)
                .keyMutexEnabled(keyMutexEnabled)
                .keyMutexMaxSize(keyMutexMaxSize)
                .rateLimitQps(rateLimitQps)
                .degradePolicy(degradePolicy)
                .bloomFilter(bloomFilter)
                .maxEntries(maxEntries)
                .evictionPolicy(evictionPolicy)
                .memoryEvictionIntervalMs(memoryEvictionIntervalMs)
                .l1Config(l1Config)
                .l2Config(l2Config)
                .eventListener(eventListener)
                .options(options);
    }

    @Override
    public String toString() {
        return "VKCacheConfig{" +
                "providerType=" + providerType +
                ", endpoints=" + Arrays.toString(endpoints) +
                ", redisMode=" + redisMode +
                ", database=" + database +
                ", ssl=" + ssl +
                ", minIdle=" + minIdle +
                ", maxActive=" + maxActive +
                ", defaultTtlMs=" + defaultTtlMs +
                ", ttlJitterMs=" + ttlJitterMs +
                ", keyPrefix='" + keyPrefix + '\'' +
                ", codec='" + codec + '\'' +
                '}';
    }
}
