package yueyang.vostok.security.crypto;

import yueyang.vostok.security.exception.VKSecurityException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class VKAesCrypto {
    private static final int DEFAULT_AES_KEY_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private VKAesCrypto() {
    }

    public static String generateAesKeyBase64() {
        return generateAesKeyBase64(DEFAULT_AES_KEY_BITS);
    }

    public static String generateAesKeyBase64(int bits) {
        try {
            int keyBits = normalizeBits(bits);
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(keyBits);
            return Base64.getEncoder().encodeToString(generator.generateKey().getEncoded());
        } catch (Exception e) {
            throw new VKSecurityException("Generate AES key failed: " + e.getMessage());
        }
    }

    public static String encrypt(String plainText, String secret) {
        try {
            byte[] key = deriveOrDecodeKey(secret);
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(valueBytes(plainText));

            byte[] packed = new byte[1 + iv.length + encrypted.length];
            packed[0] = (byte) iv.length;
            System.arraycopy(iv, 0, packed, 1, iv.length);
            System.arraycopy(encrypted, 0, packed, 1 + iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("AES encrypt failed: " + e.getMessage());
        }
    }

    public static String decrypt(String cipherTextBase64, String secret) {
        try {
            byte[] packed = Base64.getDecoder().decode(cipherTextBase64);
            if (packed.length < 2) {
                throw new VKSecurityException("AES payload invalid");
            }
            int ivLen = packed[0] & 0xFF;
            if (ivLen <= 0 || packed.length <= 1 + ivLen) {
                throw new VKSecurityException("AES payload invalid IV");
            }
            byte[] iv = Arrays.copyOfRange(packed, 1, 1 + ivLen);
            byte[] encrypted = Arrays.copyOfRange(packed, 1 + ivLen, packed.length);

            byte[] key = deriveOrDecodeKey(secret);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("AES decrypt failed: " + e.getMessage());
        }
    }

    private static byte[] deriveOrDecodeKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new VKSecurityException("Secret is blank");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (isAesKeyLength(decoded.length)) {
                return decoded;
            }
        } catch (Exception ignore) {
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(raw, 32);
        } catch (Exception e) {
            throw new VKSecurityException("Secret derive failed: " + e.getMessage());
        }
    }

    private static int normalizeBits(int bits) {
        if (bits == 128 || bits == 192 || bits == 256) {
            return bits;
        }
        return DEFAULT_AES_KEY_BITS;
    }

    private static boolean isAesKeyLength(int len) {
        return len == 16 || len == 24 || len == 32;
    }

    private static byte[] valueBytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
