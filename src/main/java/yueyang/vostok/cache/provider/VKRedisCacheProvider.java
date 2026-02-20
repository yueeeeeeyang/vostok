package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;
import yueyang.vostok.cache.VKRedisMode;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import java.util.ArrayList;
import java.util.List;

public class VKRedisCacheProvider implements VKCacheProvider {
    private VKCacheConfig config;
    private VKRedisTopologyResolver resolver;

    @Override
    public String type() {
        return "redis";
    }

    @Override
    public void init(VKCacheConfig config) {
        if (config == null) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "VKCacheConfig is null");
        }
        String[] eps = config.getEndpoints();
        if (eps.length == 0) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Redis endpoints are empty");
        }
        List<VKRedisEndpoint> endpoints = new ArrayList<>();
        for (String ep : eps) {
            try {
                endpoints.add(VKRedisEndpoint.parse(ep));
            } catch (Exception e) {
                throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR,
                        "Redis endpoint invalid: " + ep, e);
            }
        }
        VKRedisMode mode = config.getRedisMode() == null ? VKRedisMode.SINGLE : config.getRedisMode();
        this.config = config.copy();
        this.resolver = new VKRedisTopologyResolver(mode, endpoints);
    }

    @Override
    public VKCacheClient createClient() {
        ensureInit();
        return new VKRedisClient(resolver, config);
    }

    @Override
    public boolean validate(VKCacheClient client) {
        return client != null && client.ping();
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
        if (config == null || resolver == null) {
            throw new VKCacheException(VKCacheErrorCode.STATE_ERROR,
                    "Redis cache provider is not initialized");
        }
    }
}
