package yueyang.vostok.security.field;

import yueyang.vostok.security.exception.VKSecurityException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * vkf3 字段加密二进制编解码工具类（纯静态）。
 *
 * <h3>vkf3 密文格式（Base64 前的二进制布局）：</h3>
 * <pre>
 * byte[0]      = 0x03          版本标识
 * byte[1..4]   = keyIdHash     SHA-256(tableKeyId)[0:4]，大端 int32
 * byte[5..8]   = dekVersion    int32 大端
 * byte[9..20]  = GCM IV        12字节，SecureRandom 独立生成
 * byte[21..]   = AES-256-GCM 密文 + 16字节认证标签（doFinal 一次性输出）
 * </pre>
 *
 * <p>固定开销 {@value #MIN_CIPHER_BYTES} 字节（含 16 字节 GCM 认证标签），Base64 编码后约 +50 字符前缀。
 *
 * <p>版本校验和最小长度校验由调用方（{@link VKFieldSession} / {@link yueyang.vostok.security.VostokSecurity}）执行，
 * 本类不重复校验。
 */
public final class VKFieldCrypto {

    /** vkf3 版本字节标识 */
    public static final int VERSION = 0x03;

    /** 头部长度：version(1) + keyIdHash(4) + dekVersion(4) + iv(12) = 21 字节 */
    public static final int HEADER_BYTES = 21;

    /** GCM 认证标签长度：16 字节 */
    public static final int TAG_BYTES = 16;

    /** 最小有效密文长度：头部(21) + 最小密文(0) + 认证标签(16) = 37 字节 */
    public static final int MIN_CIPHER_BYTES = HEADER_BYTES + TAG_BYTES;

    private static final int GCM_TAG_BITS = 128;

    private VKFieldCrypto() {
    }

    /**
     * 计算 tableKeyId 的 SHA-256 前 4 字节作为 keyIdHash（大端 int32）。
     * 用于自描述解密时快速定位对应的 tableKeyId。
     *
     * <p>注意：此哈希仅在 JVM 进程内做碰撞检测，不保证全局唯一性。
     *
     * @param tableKeyId 表级密钥 ID
     * @return SHA-256(tableKeyId) 的前 4 字节（大端 int32 解释）
     */
    public static int computeKeyIdHash(String tableKeyId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(tableKeyId.getBytes(StandardCharsets.UTF_8));
            // 取前 4 字节，ByteBuffer 默认大端
            return ByteBuffer.wrap(hash, 0, 4).getInt();
        } catch (Exception e) {
            throw new VKSecurityException("computeKeyIdHash failed: " + e.getMessage());
        }
    }

    /**
     * 从 vkf3 原始字节数组中解析 keyIdHash（bytes[1..4] 大端 int32）。
     *
     * @param raw Base64 解码后的原始字节数组（长度 >= {@link #MIN_CIPHER_BYTES}）
     * @return keyIdHash int32
     */
    public static int parseKeyIdHash(byte[] raw) {
        // bytes[1..4]：偏移 1，长度 4
        return ByteBuffer.wrap(raw, 1, 4).getInt();
    }

    /**
     * 从 vkf3 原始字节数组中解析 dekVersion（bytes[5..8] 大端 int32）。
     *
     * @param raw Base64 解码后的原始字节数组（长度 >= {@link #MIN_CIPHER_BYTES}）
     * @return dekVersion int32
     */
    public static int parseDekVersion(byte[] raw) {
        // bytes[5..8]：偏移 5，长度 4
        return ByteBuffer.wrap(raw, 5, 4).getInt();
    }

    /**
     * 用 DEK 加密字节数组，返回 Base64 编码的 vkf3 密文。
     *
     * <p>流程：SecureRandom 生成 12 字节 IV → AES/GCM/NoPadding doFinal → ByteBuffer 组装头部 → Base64。
     *
     * @param plain      明文字节数组
     * @param dek        数据加密密钥（AES-256）
     * @param keyIdHash  tableKeyId 的 hash（写入头部，供自描述解密）
     * @param dekVersion DEK 版本号（写入头部，供多版本解密）
     * @return Base64 编码的 vkf3 格式密文
     */
    public static String encryptBytes(byte[] plain, SecretKey dek, int keyIdHash, int dekVersion) {
        try {
            // 生成随机 IV（每次加密独立，保证语义安全）
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            // doFinal 一次性输出：密文 + 16 字节 GCM 认证标签
            byte[] cipherAndTag = cipher.doFinal(plain);

            // ByteBuffer 默认大端，组装头部 + 密文
            ByteBuffer buf = ByteBuffer.allocate(HEADER_BYTES + cipherAndTag.length);
            buf.put((byte) VERSION);   // byte[0]: 版本标识 0x03
            buf.putInt(keyIdHash);     // byte[1..4]: keyIdHash 大端 int32
            buf.putInt(dekVersion);    // byte[5..8]: dekVersion 大端 int32
            buf.put(iv);               // byte[9..20]: 12 字节 IV
            buf.put(cipherAndTag);     // byte[21..]: 密文 + 认证标签

            return Base64.getEncoder().encodeToString(buf.array());
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("Field encrypt failed: " + e.getMessage());
        }
    }

    /**
     * 用 DEK 加密 UTF-8 字符串，返回 Base64 编码的 vkf3 密文。
     *
     * @param plain      明文字符串
     * @param dek        数据加密密钥
     * @param keyIdHash  tableKeyId 的 hash
     * @param dekVersion DEK 版本号
     * @return Base64 编码的 vkf3 格式密文
     */
    public static String encryptString(String plain, SecretKey dek, int keyIdHash, int dekVersion) {
        return encryptBytes(plain.getBytes(StandardCharsets.UTF_8), dek, keyIdHash, dekVersion);
    }

    /**
     * 解密 vkf3 密文（Base64），返回原始字节数组。
     *
     * <p>流程：Base64 解码 → 提取 IV（bytes[9..20]） → 提取密文+tag（bytes[21..]） →
     * AES/GCM/NoPadding doFinal → 返回明文字节。
     *
     * <p>调用方需先校验：{@code raw[0] == VERSION} 且 {@code raw.length >= MIN_CIPHER_BYTES}。
     *
     * @param base64Cipher Base64 编码的 vkf3 密文
     * @param dek          数据加密密钥（必须与加密时一致）
     * @return 解密后的原始字节数组
     * @throws VKSecurityException GCM 认证标签验证失败（密文被篡改）或解密异常
     */
    public static byte[] decryptBytes(String base64Cipher, SecretKey dek) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64Cipher);

            // 提取 IV：bytes[9..20]（12 字节）
            byte[] iv = new byte[12];
            System.arraycopy(raw, 9, iv, 0, 12);

            // 提取密文+认证标签：bytes[21..]
            int cipherLen = raw.length - HEADER_BYTES;
            byte[] cipherAndTag = new byte[cipherLen];
            System.arraycopy(raw, HEADER_BYTES, cipherAndTag, 0, cipherLen);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            // doFinal 自动验证 GCM 认证标签，篡改时抛 AEADBadTagException
            return cipher.doFinal(cipherAndTag);
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("Field decrypt failed: " + e.getMessage());
        }
    }

    /**
     * 解密 vkf3 密文（Base64），返回 UTF-8 字符串。
     *
     * @param base64Cipher Base64 编码的 vkf3 密文
     * @param dek          数据加密密钥
     * @return 解密后的 UTF-8 字符串
     */
    public static String decryptString(String base64Cipher, SecretKey dek) {
        return new String(decryptBytes(base64Cipher, dek), StandardCharsets.UTF_8);
    }

    /**
     * 计算可搜索 Blind Index：HMAC-SHA256(plain, blindKey)，返回 64 字符十六进制字符串。
     *
     * <p>Blind Index 是确定性的（相同输入 + 相同密钥 → 相同输出），可用于等值查询而不暴露明文。
     * Blind Key 终身不轮换，确保历史查询索引持续有效。
     *
     * @param plain    明文字符串（UTF-8 编码后作为 HMAC 输入）
     * @param blindKey Blind HMAC 密钥（AES-256，作为 HMAC 密钥使用）
     * @return 64 字符十六进制 HMAC-SHA256 值
     */
    public static String computeBlindIndex(String plain, SecretKey blindKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(blindKey);
            byte[] hmac = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            // 转换为 64 字符十六进制字符串
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hmac) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new VKSecurityException("Blind index compute failed: " + e.getMessage());
        }
    }
}
