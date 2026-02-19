package yueyang.vostok.cache.codec;

import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

public class VKBytesCacheCodec implements VKCacheCodec {
    @Override
    public String name() {
        return "bytes";
    }

    @Override
    public byte[] encode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] b) {
            return b;
        }
        throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT,
                "Bytes codec only supports byte[] values");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(byte[] data, Class<T> type) {
        if (data == null) {
            return null;
        }
        if (type == null || type == byte[].class || Object.class == type) {
            return (T) data;
        }
        throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT,
                "Bytes codec only supports byte[] target type");
    }
}
