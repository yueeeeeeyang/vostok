package yueyang.vostok.cache;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 内置布隆过滤器实现（线程安全）。
 * <p>
 * 算法：Murmur3 64-bit 双哈希 + {@link AtomicLongArray} 位数组（CAS 无锁）。
 * <ul>
 *   <li>位数量：{@code ceil(-n * ln(p) / (ln 2)^2)}</li>
 *   <li>哈希函数数量：{@code round(m/n * ln 2)}</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * VKBloomFilter bf = VKBloomFilter.create(1_000_000, 0.01);
 * new VKCacheConfig().bloomFilter(bf);
 * }</pre>
 *
 * @see VKBloomFilter#create(long, double)
 */
public final class VKDefaultBloomFilter implements VKBloomFilter {

    /** 位数组，每个 long 保存 64 位，CAS 方式置位 */
    private final AtomicLongArray bits;
    /** 位数组长度（以 bit 计） */
    private final long numBits;
    /** 哈希函数数量（双哈希模拟 k 个独立哈希） */
    private final int numHashFunctions;

    /**
     * 私有构造，请通过 {@link VKBloomFilter#create(long, double)} 或
     * {@link #create(long, double)} 创建实例。
     *
     * @param numBits          位数组大小（bits）
     * @param numHashFunctions 哈希函数数量
     */
    VKDefaultBloomFilter(long numBits, int numHashFunctions) {
        // 向上取整到 64 的倍数，以便整除 long 数组
        this.numBits = Math.max(64, numBits);
        int longs = (int) ((this.numBits + 63) / 64);
        this.bits = new AtomicLongArray(longs);
        this.numHashFunctions = Math.max(1, numHashFunctions);
    }

    /**
     * 工厂方法：根据预期插入量和误判率创建布隆过滤器。
     *
     * @param expectedInsertions 预期插入的不同 key 数量，必须 &gt; 0
     * @param fpp                期望误判率（false positive probability），范围 (0, 1)
     * @return 配置好的布隆过滤器实例
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public static VKDefaultBloomFilter create(long expectedInsertions, double fpp) {
        if (expectedInsertions <= 0) {
            throw new IllegalArgumentException("expectedInsertions must be > 0");
        }
        if (fpp <= 0.0 || fpp >= 1.0) {
            throw new IllegalArgumentException("fpp must be in (0, 1)");
        }
        // m = ceil(-n * ln(p) / (ln2)^2)
        double ln2 = Math.log(2);
        long m = (long) Math.ceil(-expectedInsertions * Math.log(fpp) / (ln2 * ln2));
        // k = round(m/n * ln2)
        int k = (int) Math.max(1, Math.round((double) m / expectedInsertions * ln2));
        return new VKDefaultBloomFilter(m, k);
    }

    @Override
    public boolean mightContain(String key) {
        if (key == null) {
            return false;
        }
        // 使用双哈希（Murmur3 h1 + h2）模拟 k 个哈希函数
        long h1 = murmur3Hash64(key, 0);
        long h2 = murmur3Hash64(key, h1);
        for (int i = 0; i < numHashFunctions; i++) {
            long combined = h1 + (long) i * h2;
            // 映射到 [0, numBits) 范围内，保证非负
            long bit = (combined & Long.MAX_VALUE) % numBits;
            if (!getBit(bit)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void put(String key) {
        if (key == null) {
            return;
        }
        long h1 = murmur3Hash64(key, 0);
        long h2 = murmur3Hash64(key, h1);
        for (int i = 0; i < numHashFunctions; i++) {
            long combined = h1 + (long) i * h2;
            long bit = (combined & Long.MAX_VALUE) % numBits;
            setBit(bit);
        }
    }

    // ---- 位操作（CAS 无锁） ----

    /**
     * 读取指定 bit 位是否为 1。
     *
     * @param bit 位索引（0 ~ numBits-1）
     * @return true 表示该位已设置
     */
    private boolean getBit(long bit) {
        int idx = (int) (bit / 64);
        long mask = 1L << (bit % 64);
        return (bits.get(idx) & mask) != 0;
    }

    /**
     * 将指定 bit 位 CAS 置为 1（已为 1 时幂等跳过）。
     *
     * @param bit 位索引
     */
    private void setBit(long bit) {
        int idx = (int) (bit / 64);
        long mask = 1L << (bit % 64);
        // 自旋直到 CAS 成功，或已有该位
        long old;
        do {
            old = bits.get(idx);
            if ((old & mask) != 0) {
                return; // 已设置，直接返回
            }
        } while (!bits.compareAndSet(idx, old, old | mask));
    }

    // ---- Murmur3 64-bit hash ----

    /**
     * Murmur3 变体 64-bit 哈希，用于双哈希布隆过滤器。
     * 以 seed 作为初始状态，支持用不同 seed 得到独立哈希值。
     *
     * @param key  输入字符串
     * @param seed 初始种子（第二次调用时传入第一次的返回值，实现双哈希）
     * @return 64-bit 哈希值
     */
    private static long murmur3Hash64(String key, long seed) {
        byte[] data = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = data.length;
        final long c1 = 0xff51afd7ed558ccdL;
        final long c2 = 0xc4ceb9fe1a85ec53L;

        long h1 = seed;
        long h2 = seed ^ len;

        // 处理 16 字节块
        int i = 0;
        while (i + 16 <= len) {
            long k1 = getLong(data, i);
            long k2 = getLong(data, i + 8);
            i += 16;

            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729L;

            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5L;
        }

        // 处理尾部字节
        long k1 = 0, k2 = 0;
        int rem = len - i;
        // 尾部最多 15 字节，按剩余数量填充 k1/k2
        if (rem > 8) {
            for (int j = rem - 1; j >= 8; j--) {
                k2 ^= ((long) (data[i + j] & 0xFF)) << ((j - 8) * 8);
            }
            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;
        }
        for (int j = Math.min(rem, 8) - 1; j >= 0; j--) {
            k1 ^= ((long) (data[i + j] & 0xFF)) << (j * 8);
        }
        k1 *= c1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= c2;
        h1 ^= k1;

        // finalization
        h1 ^= len;
        h2 ^= len;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        h1 += h2;
        // 返回 h1，外部第二次调用时传入 h1 作为 seed 得到 h2
        return h1;
    }

    /** 从字节数组偏移量处读取小端 64-bit long */
    private static long getLong(byte[] data, int offset) {
        long val = 0;
        for (int i = 0; i < 8; i++) {
            val |= ((long) (data[offset + i] & 0xFF)) << (i * 8);
        }
        return val;
    }

    /** Murmur3 finalizer 混淆 */
    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }
}
