package yueyang.vostok.util.id;

import java.security.SecureRandom;
import java.util.UUID;

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

    public static String traceId() {
        long now = System.currentTimeMillis();
        long rnd = RNG.nextLong();
        return Long.toHexString(now) + "-" + Long.toHexString(rnd);
    }
}
