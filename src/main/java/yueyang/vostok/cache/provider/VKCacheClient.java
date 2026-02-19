package yueyang.vostok.cache.provider;

import java.util.List;
import java.util.Map;

public interface VKCacheClient extends AutoCloseable {
    byte[] get(String key);

    void set(String key, byte[] value, long ttlMs);

    long del(String... keys);

    boolean exists(String key);

    boolean expire(String key, long ttlMs);

    long incrBy(String key, long delta);

    List<byte[]> mget(String... keys);

    void mset(Map<String, byte[]> kv);

    @Override
    void close();
}
