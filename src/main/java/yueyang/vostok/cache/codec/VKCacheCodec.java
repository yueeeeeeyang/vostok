package yueyang.vostok.cache.codec;

public interface VKCacheCodec {
    String name();

    byte[] encode(Object value);

    <T> T decode(byte[] data, Class<T> type);
}
