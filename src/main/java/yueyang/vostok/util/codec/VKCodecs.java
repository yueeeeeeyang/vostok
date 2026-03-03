package yueyang.vostok.util.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.zip.CRC32;

public final class VKCodecs {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 线程本地 MD5 实例。
     * MessageDigest 非线程安全，ThreadLocal 保证每线程独享一个实例，避免每次调用走 JCA provider 查找链。
     * {@link MessageDigest#digest(byte[])} 调用后自动 reset，可安全复用。
     */
    private static final ThreadLocal<MessageDigest> MD5_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    });

    /** 线程本地 SHA-256 实例，复用策略与 MD5_LOCAL 相同。 */
    private static final ThreadLocal<MessageDigest> SHA256_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    });

    private VKCodecs() {
    }

    public static String base64Encode(byte[] value) {
        if (value == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value);
    }

    public static String base64Encode(String value) {
        if (value == null) {
            return null;
        }
        return base64Encode(value.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] base64DecodeToBytes(String value) {
        if (value == null) {
            return null;
        }
        return Base64.getDecoder().decode(value);
    }

    public static String base64DecodeToString(String value) {
        byte[] bytes = base64DecodeToBytes(value);
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String hexEncode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(out);
    }

    public static byte[] hexDecode(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.trim();
        if ((s.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex length must be even");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < s.length(); i += 2) {
            int hi = Character.digit(s.charAt(i), 16);
            int lo = Character.digit(s.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex string");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static long crc32(byte[] bytes) {
        if (bytes == null) {
            return 0L;
        }
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    public static String md5Hex(String value) {
        if (value == null) {
            return null;
        }
        // digest() 隐式 reset，ThreadLocal 实例可安全复用
        return hexEncode(MD5_LOCAL.get().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Hex(String value) {
        if (value == null) {
            return null;
        }
        return hexEncode(SHA256_LOCAL.get().digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
