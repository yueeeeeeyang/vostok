package yueyang.vostok.security.crypto;

import yueyang.vostok.security.exception.VKSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class VKHashCrypto {
    private VKHashCrypto() {
    }

    public static String sha256Base64(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(valueBytes(text));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new VKSecurityException("SHA-256 failed: " + e.getMessage());
        }
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(valueBytes(text));
            return toHex(out);
        } catch (Exception e) {
            throw new VKSecurityException("SHA-256 failed: " + e.getMessage());
        }
    }

    public static String hmacSha256Base64(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(valueBytes(secret), "HmacSHA256"));
            byte[] sign = mac.doFinal(valueBytes(text));
            return Base64.getEncoder().encodeToString(sign);
        } catch (Exception e) {
            throw new VKSecurityException("HMAC-SHA256 failed: " + e.getMessage());
        }
    }

    private static byte[] valueBytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
