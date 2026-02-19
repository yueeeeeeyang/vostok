package yueyang.vostok.cache;

public enum VKCacheProviderType {
    MEMORY,
    REDIS;

    public static VKCacheProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return MEMORY;
        }
        for (VKCacheProviderType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown cache provider type: " + value);
    }
}
