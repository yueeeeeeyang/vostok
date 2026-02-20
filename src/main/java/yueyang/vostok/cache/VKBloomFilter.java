package yueyang.vostok.cache;

public interface VKBloomFilter {
    boolean mightContain(String key);

    void put(String key);

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
