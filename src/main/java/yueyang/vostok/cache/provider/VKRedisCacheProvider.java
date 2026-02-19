package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

public class VKRedisCacheProvider implements VKCacheProvider {
    private VKCacheConfig config;
    private String host;
    private int port;

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public void init(VKCacheConfig config) {
        if (config == null) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "VKCacheConfig is null");
        }
        String[] endpoints = config.getEndpoints();
        if (endpoints.length == 0) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Redis endpoints are empty");
        }
        String endpoint = endpoints[0];
        String[] parts = endpoint.split(":", 2);
        if (parts.length != 2) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Redis endpoint format invalid: " + endpoint);
        }
        this.host = parts[0].trim();
        try {
            this.port = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Redis endpoint port invalid: " + endpoint);
        }
        this.config = config.copy();
    }

    @Override
    public VKCacheClient createClient() {
        ensureInit();
        return new VKRedisClient(host, port, config);
    }

    @Override
    public boolean validate(VKCacheClient client) {
        if (!(client instanceof VKRedisClient redisClient)) {
            return false;
        }
        return redisClient.ping();
    }

    @Override
    public void destroy(VKCacheClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public void close() {
        // no-op
    }

    private void ensureInit() {
        if (config == null) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR,
                    "Redis cache provider is not initialized");
        }
    }
}
