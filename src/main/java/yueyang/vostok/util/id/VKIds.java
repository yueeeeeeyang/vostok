package yueyang.vostok.util.id;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class VKIds {
    private static final char[] ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private VKIds() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String randomAlphaNum(int len) {
        if (len <= 0) {
            return "";
        }
        char[] out = new char[len];
        for (int i = 0; i < len; i++) {
            out[i] = ALPHANUM[RNG.nextInt(ALPHANUM.length)];
        }
        return new String(out);
    }

    /**
     * 生成固定格式 trace ID：{@code <13位十六进制时间戳>-<16位十六进制随机数>}，总长度固定 30 字符。
     *
     * <p>示例：{@code 019c075c4000-3f8a2b1c4e5d6f7a}
     *
     * <p>trace ID 仅用于可观测性，使用 {@link ThreadLocalRandom} 而非 {@link SecureRandom}，
     * 避免高并发下的竞争开销。固定宽度保证下游系统（列宽对齐、分桶等）行为一致。
     */
    public static String traceId() {
        long now = System.currentTimeMillis();
        long rnd = ThreadLocalRandom.current().nextLong();
        // %013x：时间戳高位补零，当前毫秒值约 11 位十六进制，13 位可用至 2527 年
        // %016x：long 的十六进制表示，负数按无符号处理，始终 16 位
        return String.format("%013x-%016x", now, rnd);
    }
}
