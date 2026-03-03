package yueyang.vostok.data.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.data.VKDataConfig;
import yueyang.vostok.data.exception.VKArgumentException;
import yueyang.vostok.data.meta.FieldMeta;

import java.util.Base64;

/**
 * 字段级加解密工具。
 *
 * <p>底层使用 vkf3 格式（AES-256-GCM + DEK/KEK 双层密钥，自描述头携带 tableKeyId 哈希和 DEK 版本号）。
 * 加密委托给 {@link Vostok.Security#encryptField}，解密委托给 {@link Vostok.Security#decryptField}
 * （自描述解密，无需调用方传入 tableKeyId）。
 *
 * <p>vkf3 密文格式（Base64 编码的二进制载荷）：
 * <pre>
 * byte[0]     = 0x03          版本标识
 * byte[1..4]  = keyIdHash     SHA-256(tableKeyId)[0:4]，大端 int32
 * byte[5..8]  = dekVersion    int32 大端
 * byte[9..20] = GCM IV        12字节
 * byte[21..]  = AES-256-GCM 密文 + 16字节认证标签
 * </pre>
 */
public final class VKFieldCrypto {

    /** vkf3 格式版本字节 */
    private static final int VKF3_VERSION = 0x03;

    /**
     * vkf3 密文解码后最小字节数：
     * 1（版本）+ 4（keyIdHash）+ 4（dekVersion）+ 12（IV）+ 16（GCM 标签）= 37
     */
    private static final int VKF3_MIN_BYTES = 37;

    private VKFieldCrypto() {
    }

    /**
     * 写入参数绑定前执行字段加密，返回 vkf3 格式 Base64 密文。
     *
     * @param field  字段元数据（含 encrypted 标志和可选 keyId）
     * @param value  原始值；null 直接透传
     * @param config 数据配置（含 fieldEncryptionEnabled、defaultEncryptionKeyId）
     * @return vkf3 Base64 密文，或原始值（不需加密时）
     */
    public static Object encryptWrite(FieldMeta field, Object value, VKDataConfig config) {
        if (!shouldHandle(field, config) || value == null) {
            return value;
        }
        if (!(value instanceof String)) {
            throw new VKArgumentException("Encrypted field value must be String: " + field.getField().getName());
        }
        String tableKeyId = resolveKeyId(field, config);
        try {
            return Vostok.Security.encryptField((String) value, tableKeyId);
        } catch (Exception e) {
            throw new VKArgumentException("Encrypt field failed: " + field.getField().getName(), e);
        }
    }

    /**
     * 读取结果映射前执行字段解密（vkf3 自描述解密，不需要调用方传入 tableKeyId）。
     *
     * <p>若值不是 vkf3 格式（非 Base64 或版本字节不匹配）：
     * <ul>
     *   <li>{@code allowPlaintextRead=true}：直接返回原始字符串（迁移期兼容）</li>
     *   <li>{@code allowPlaintextRead=false}：抛 {@link VKArgumentException}</li>
     * </ul>
     *
     * @param field  字段元数据
     * @param value  数据库读取到的值；null 直接透传
     * @param config 数据配置
     * @return 明文字符串，或原始值（不需解密时）
     */
    public static Object decryptRead(FieldMeta field, Object value, VKDataConfig config) {
        if (!shouldHandle(field, config) || value == null) {
            return value;
        }
        if (!(value instanceof String)) {
            throw new VKArgumentException("Encrypted field db value must be String: " + field.getField().getName());
        }
        String text = (String) value;
        if (!isVkf3Cipher(text)) {
            if (config.isAllowPlaintextRead()) {
                return text;
            }
            throw new VKArgumentException("Encrypted payload format invalid for field: " + field.getField().getName());
        }
        try {
            // 自描述解密：vkf3 头携带 keyIdHash，Security 模块自动找到对应 tableKeyId
            return Vostok.Security.decryptField(text);
        } catch (Exception e) {
            throw new VKArgumentException("Decrypt field failed: " + field.getField().getName(), e);
        }
    }

    /**
     * 检测字符串是否为 vkf3 格式密文。
     *
     * <p>判定条件：Base64 解码成功 且 字节长度 >= 37 且 首字节 == 0x03。
     *
     * @param text 待检测字符串
     * @return true 表示是 vkf3 密文
     */
    static boolean isVkf3Cipher(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(text);
            return raw.length >= VKF3_MIN_BYTES && (raw[0] & 0xFF) == VKF3_VERSION;
        } catch (Exception e) {
            return false;
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
