package yueyang.vostok.data.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.exception.VKArgumentException;
import yueyang.vostok.data.meta.FieldMeta;

/**
 * 字段级加解密工具。
 */
public final class VKFieldCrypto {
    private static final String CIPHER_PREFIX = "vk1:aes:";

    private VKFieldCrypto() {
    }

    /**
     * 写入参数绑定前执行字段加密。
     */
    public static Object encryptWrite(FieldMeta field, Object value, VKDataConfig config) {
        if (!shouldHandle(field, config) || value == null) {
            return value;
        }
        if (!(value instanceof String)) {
            throw new VKArgumentException("Encrypted field value must be String: " + field.getField().getName());
        }
        String keyId = resolveKeyId(field, config);
        try {
            return Vostok.Security.encryptWithKeyId((String) value, keyId);
        } catch (Exception e) {
            throw new VKArgumentException("Encrypt field failed: " + field.getField().getName(), e);
        }
    }

    /**
     * 读取结果映射前执行字段解密。
     */
    public static Object decryptRead(FieldMeta field, Object value, VKDataConfig config) {
        if (!shouldHandle(field, config) || value == null) {
            return value;
        }
        if (!(value instanceof String)) {
            throw new VKArgumentException("Encrypted field db value must be String: " + field.getField().getName());
        }
        String text = (String) value;
        if (!text.startsWith(CIPHER_PREFIX)) {
            if (config.isAllowPlaintextRead()) {
                return text;
            }
            throw new VKArgumentException("Encrypted payload format invalid for field: " + field.getField().getName());
        }
        try {
            return Vostok.Security.decryptWithKeyId(text);
        } catch (Exception e) {
            throw new VKArgumentException("Decrypt field failed: " + field.getField().getName(), e);
        }
    }

    private static boolean shouldHandle(FieldMeta field, VKDataConfig config) {
        return config != null && config.isFieldEncryptionEnabled() && field != null && field.isEncrypted();
    }

    private static String resolveKeyId(FieldMeta field, VKDataConfig config) {
        String keyId = field.getEncryptionKeyId();
        if (keyId != null && !keyId.isBlank()) {
            return keyId;
        }
        String fallback = config == null ? null : config.getDefaultEncryptionKeyId();
        if (fallback == null || fallback.isBlank()) {
            throw new VKArgumentException("Encryption keyId is blank for field: " + field.getField().getName());
        }
        return fallback;
    }
}
