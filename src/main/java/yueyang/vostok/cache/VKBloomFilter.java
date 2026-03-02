package yueyang.vostok.cache;

public interface VKBloomFilter {
    boolean mightContain(String key);

    void put(String key);

    /**
     * 创建内置布隆过滤器实例（基于 Murmur3 双哈希 + AtomicLongArray 位数组）。
     * <p>
     * 等价于 {@link VKDefaultBloomFilter#create(long, double)}。
     *
     * @param expectedInsertions 预期插入的不同 key 数量，必须 &gt; 0
     * @param fpp                期望误判率（false positive probability），范围 (0, 1)
     * @return 配置好的布隆过滤器
     */
    static VKBloomFilter create(long expectedInsertions, double fpp) {
        return VKDefaultBloomFilter.create(expectedInsertions, fpp);
    }

    static VKBloomFilter noOp() {
        return new VKBloomFilter() {
            @Override
            public boolean mightContain(String key) {
                return true;
            }

            @Override
            public void put(String key) {
                // no-op
            }
        };
    }
}
