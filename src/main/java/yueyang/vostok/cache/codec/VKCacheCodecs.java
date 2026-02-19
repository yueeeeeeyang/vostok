package yueyang.vostok.cache.codec;

import yueyang.vostok.cache.exception.VKCacheErrorCode;
import yueyang.vostok.cache.exception.VKCacheException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VKCacheCodecs {
    private static final Map<String, VKCacheCodec> CODECS = new ConcurrentHashMap<>();

    static {
        register(new VKJsonCacheCodec());
        register(new VKStringCacheCodec());
        register(new VKBytesCacheCodec());
    }

    private VKCacheCodecs() {
    }

    public static void register(VKCacheCodec codec) {
        if (codec == null || codec.name() == null || codec.name().isBlank()) {
            return;
        }
        CODECS.put(codec.name().toLowerCase(), codec);
    }

    public static VKCacheCodec get(String name) {
        String key = name == null || name.isBlank() ? "json" : name.trim().toLowerCase();
        VKCacheCodec codec = CODECS.get(key);
        if (codec == null) {
            throw new VKCacheException(VKCacheErrorCode.CONFIG_ERROR, "Unknown cache codec: " + name);
        }
        return codec;
    }
}
