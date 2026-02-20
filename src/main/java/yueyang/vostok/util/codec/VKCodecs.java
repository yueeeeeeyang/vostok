package yueyang.vostok.util.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.zip.CRC32;

public final class VKCodecs {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

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
        return digestHex("MD5", value);
    }

    public static String sha256Hex(String value) {
        return digestHex("SHA-256", value);
    }

    private static String digestHex(String algorithm, String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return hexEncode(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Digest failed: " + algorithm, e);
        }
    }
}
