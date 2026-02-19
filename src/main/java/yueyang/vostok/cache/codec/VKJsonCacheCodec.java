package yueyang.vostok.cache.codec;

import yueyang.vostok.common.json.VKJson;

import java.nio.charset.StandardCharsets;

public class VKJsonCacheCodec implements VKCacheCodec {
    @Override
    public String name() {
        return "json";
    }

    @Override
    public byte[] encode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] b) {
            return b;
        }
        if (value instanceof String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
        return VKJson.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(byte[] data, Class<T> type) {
        if (data == null) {
            return null;
        }
        if (type == null || type == Object.class || type == byte[].class) {
            return (T) data;
        }
        String json = new String(data, StandardCharsets.UTF_8);
        if (type == String.class) {
            return (T) json;
        }
        return VKJson.fromJson(json, type);
    }
}
