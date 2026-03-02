package yueyang.vostok.cache;

public enum VKCacheProviderType {
    MEMORY,
    REDIS,
    /** L1（内存）+ L2（任意 Provider）两级缓存模式。需配合 l1Config / l2Config 使用。 */
    TIERED;

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
