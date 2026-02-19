package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheProviderType;
import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

public final class VKCacheProviderFactory {
    private VKCacheProviderFactory() {
    }

    public static VKCacheProvider create(VKCacheProviderType type) {
        VKCacheProviderType t = type == null ? VKCacheProviderType.MEMORY : type;
        if (t == VKCacheProviderType.MEMORY) {
            return new VKMemoryCacheProvider();
        }
        if (t == VKCacheProviderType.REDIS) {
            return new VKRedisCacheProvider();
        }
        throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Unsupported cache provider: " + t);
    }
}
