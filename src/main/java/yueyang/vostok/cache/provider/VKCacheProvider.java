package yueyang.vostok.cache.provider;

import yueyang.vostok.cache.VKCacheConfig;

public interface VKCacheProvider extends AutoCloseable {
    String type();

    void init(VKCacheConfig config);

    VKCacheClient createClient();

    boolean validate(VKCacheClient client);

    void destroy(VKCacheClient client);

    @Override
    void close();
}
