package yueyang.vostok.cache.provider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VKCacheClient extends AutoCloseable {
    byte[] get(String key);

    void set(String key, byte[] value, long ttlMs);

    long del(String... keys);

    boolean exists(String key);

    boolean expire(String key, long ttlMs);

    long incrBy(String key, long delta);

    List<byte[]> mget(String... keys);

    void mset(Map<String, byte[]> kv);

    long hset(String key, String field, byte[] value);

    byte[] hget(String key, String field);

    Map<String, byte[]> hgetAll(String key);

    long hdel(String key, String... fields);

    long lpush(String key, byte[]... values);

    List<byte[]> lrange(String key, long start, long stop);

    long sadd(String key, byte[]... members);

    Set<byte[]> smembers(String key);

    long zadd(String key, double score, byte[] member);

    List<byte[]> zrange(String key, long start, long stop);

    List<String> scan(String pattern, int count);

    boolean ping();

    @Override
    void close();
}
