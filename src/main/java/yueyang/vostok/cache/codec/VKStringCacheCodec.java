package yueyang.vostok.cache.codec;

import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import java.nio.charset.StandardCharsets;

public class VKStringCacheCodec implements VKCacheCodec {
    @Override
    public String name() {
        return "string";
    }

    @Override
    public byte[] encode(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(byte[] data, Class<T> type) {
        if (data == null) {
            return null;
        }
        String s = new String(data, StandardCharsets.UTF_8);
        if (type == null || type == String.class || type == Object.class) {
            return (T) s;
        }
        try {
            if (type == Integer.class || type == int.class) {
                return (T) Integer.valueOf(s);
            }
            if (type == Long.class || type == long.class) {
                return (T) Long.valueOf(s);
            }
            if (type == Double.class || type == double.class) {
                return (T) Double.valueOf(s);
            }
            if (type == Boolean.class || type == boolean.class) {
                return (T) Boolean.valueOf(s);
            }
        } catch (Exception e) {
            throw new VKCacheException(VKCacheErrorCode.COMMAND_ERROR,
                    "Failed to decode value as " + type.getSimpleName(), e);
        }
        throw new VKCacheException(VKCacheErrorCode.INVALID_ARGUMENT,
                "String codec cannot decode target type: " + type.getName());
    }
}
