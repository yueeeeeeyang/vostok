package yueyang.vostok.security.field;

import yueyang.vostok.security.exception.VKSecurityException;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * 数据库字段加密 Session，实现 {@link AutoCloseable}。
 *
 * <p>由 {@link yueyang.vostok.security.VostokSecurity#fieldSession(String)} 构建，
 * 绑定到单个 tableKeyId。Session 内复用缓存的 DEK 和 Blind Key，避免重复密钥解包开销。
 *
 * <p>使用模式（try-with-resources）：
 * <pre>{@code
 * try (VKFieldSession session = VostokSecurity.fieldSession("users")) {
 *     String cipher = session.encrypt(plainText);
 *     String plain  = session.decrypt(cipher);
 *     String blind  = session.blindIndex(plain);
 * }
 * }</pre>
 *
 * <p>线程安全：单个 Session 实例不建议在多线程间共享，应每线程独立创建 Session。
 * 底层 {@link VKTableDekCache} 是线程安全的，允许多 Session 并发访问同一 cache。
 *
 * <p>关闭后调用任意方法抛 {@link VKSecurityException}（"VKFieldSession is closed"）。
 */
public final class VKFieldSession implements AutoCloseable {

    private final String tableKeyId;
    /** 预计算的 keyIdHash，避免每次加解密都重新计算 */
    private final int keyIdHash;
    private final VKTableDekCache cache;
    private final VKNullPolicy nullPolicy;

    /** closed 用 volatile 保证可见性（虽然不建议多线程共享 session） */
    private volatile boolean closed;

    /**
     * 构建 Session，并向 cache 注册 tableKeyId（建立 keyIdHash → tableKeyId 映射）。
     *
     * @param tableKeyId  表级密钥 ID
     * @param cache       DEK/Blind Key 缓存（由 VostokSecurity 持有）
     * @param nullPolicy  空值处理策略
     */
    public VKFieldSession(String tableKeyId, VKTableDekCache cache, VKNullPolicy nullPolicy) {
        this.tableKeyId = tableKeyId;
        this.keyIdHash = VKFieldCrypto.computeKeyIdHash(tableKeyId);
        this.cache = cache;
        this.nullPolicy = nullPolicy;
        // 注册 tableKeyId，建立 hash 到 tableKeyId 的反查映射（用于自描述解密）
        cache.registerTableKey(tableKeyId);
    }

    /**
     * 加密字符串字段（UTF-8），返回 vkf3 格式 Base64 密文。
     *
     * @param plain 明文字符串；null 按 {@link VKNullPolicy} 处理
     * @return vkf3 Base64 密文，或 null（NULL_PASSTHROUGH 且入参为 null）
     */
    public String encrypt(String plain) {
        checkClosed();
        if (plain == null) {
            return handleNull();
        }
        VKTableDekCache.DekHandle handle = cache.currentDek(tableKeyId);
        return VKFieldCrypto.encryptString(plain, handle.dek(), keyIdHash, handle.version());
    }

    /**
     * 解密 vkf3 格式 Base64 密文，返回明文字符串。
     *
     * <p>跨表防护：若密文的 keyIdHash 与本 Session 的 tableKeyId 不匹配，
     * 抛 {@link VKSecurityException}（防止误用其他表的密文进行解密）。
     *
     * @param cipher vkf3 Base64 密文；null 返回 null
     * @return 明文字符串
     */
    public String decrypt(String cipher) {
        checkClosed();
        if (cipher == null) {
            return null;
        }
        byte[] raw = validateAndDecode(cipher);
        // 跨表防护：校验 keyIdHash 与本 Session 的 tableKeyId 一致
        int cipherKeyIdHash = VKFieldCrypto.parseKeyIdHash(raw);
        if (cipherKeyIdHash != keyIdHash) {
            throw new VKSecurityException(
                    "Cross-table decrypt detected: cipher belongs to a different table");
        }
        int dekVersion = VKFieldCrypto.parseDekVersion(raw);
        SecretKey dek = cache.getDekForVersion(tableKeyId, dekVersion);
        return VKFieldCrypto.decryptString(cipher, dek);
    }

    /**
     * 计算字段的可搜索 Blind Index（HMAC-SHA256，64 字符十六进制）。
     *
     * <p>Blind Index 是确定性的，相同明文 + 相同 Blind Key → 相同 index，可用于数据库等值查询。
     *
     * @param plain 明文字符串；null 按 {@link VKNullPolicy} 处理
     * @return 64 字符十六进制 HMAC-SHA256，或 null
     */
    public String blindIndex(String plain) {
        checkClosed();
        if (plain == null) {
            return handleNull();
        }
        SecretKey blindKey = cache.getBlindKey(tableKeyId);
        return VKFieldCrypto.computeBlindIndex(plain, blindKey);
    }

    /**
     * 类型安全加密：将 Java 对象序列化后加密，返回 vkf3 Base64 密文。
     *
     * @param value 待加密的 Java 对象；null 按 {@link VKNullPolicy} 处理
     * @param type  字段类型，决定序列化方式
     * @return vkf3 Base64 密文，或 null
     */
    public String encryptTyped(Object value, VKFieldType type) {
        checkClosed();
        if (value == null) {
            return handleNull();
        }
        byte[] bytes = type.toBytes(value);
        VKTableDekCache.DekHandle handle = cache.currentDek(tableKeyId);
        return VKFieldCrypto.encryptBytes(bytes, handle.dek(), keyIdHash, handle.version());
    }

    /**
     * 类型安全解密：解密 vkf3 密文后反序列化为指定 Java 类型。
     *
     * @param cipher vkf3 Base64 密文；null 返回 null
     * @param type   字段类型，决定反序列化方式
     * @return 反序列化后的 Java 对象（具体类型见 {@link VKFieldType} 枚举注释）
     */
    public Object decryptTyped(String cipher, VKFieldType type) {
        checkClosed();
        if (cipher == null) {
            return null;
        }
        byte[] raw = validateAndDecode(cipher);
        int cipherKeyIdHash = VKFieldCrypto.parseKeyIdHash(raw);
        if (cipherKeyIdHash != keyIdHash) {
            throw new VKSecurityException(
                    "Cross-table decrypt detected: cipher belongs to a different table");
        }
        int dekVersion = VKFieldCrypto.parseDekVersion(raw);
        SecretKey dek = cache.getDekForVersion(tableKeyId, dekVersion);
        byte[] plainBytes = VKFieldCrypto.decryptBytes(cipher, dek);
        return type.fromBytes(plainBytes);
    }

    /**
     * 重新加密：用旧 DEK 解密后立即用当前 DEK 重新加密，返回新密文。
     *
     * <p>字节级路径，不经过 String 中转，对 BYTES 类型和任意二进制内容均安全正确。
     * 常用于 DEK 轮换后批量迁移旧密文（配合 {@code VostokSecurity.reEncryptFields}）。
     *
     * @param cipher 旧 vkf3 Base64 密文；null 返回 null
     * @return 用当前 DEK 重新加密后的新 vkf3 Base64 密文
     */
    public String reEncrypt(String cipher) {
        checkClosed();
        if (cipher == null) {
            return null;
        }
        byte[] raw = validateAndDecode(cipher);
        int cipherKeyIdHash = VKFieldCrypto.parseKeyIdHash(raw);
        if (cipherKeyIdHash != keyIdHash) {
            throw new VKSecurityException(
                    "Cross-table decrypt detected: cipher belongs to a different table");
        }
        // 用旧版本 DEK 解密，纯字节操作不经 String 中转
        int oldDekVersion = VKFieldCrypto.parseDekVersion(raw);
        SecretKey oldDek = cache.getDekForVersion(tableKeyId, oldDekVersion);
        byte[] plainBytes = VKFieldCrypto.decryptBytes(cipher, oldDek);
        // 用当前最新 DEK 重新加密
        VKTableDekCache.DekHandle handle = cache.currentDek(tableKeyId);
        return VKFieldCrypto.encryptBytes(plainBytes, handle.dek(), keyIdHash, handle.version());
    }

    /**
     * 返回本 Session 绑定的 tableKeyId。
     */
    public String getTableKeyId() {
        return tableKeyId;
    }

    /**
     * 关闭 Session。关闭后调用任意方法均抛 {@link VKSecurityException}。
     */
    @Override
    public void close() {
        closed = true;
    }

    // ---------------------------------------------------------------- 私有方法

    /** 检查 Session 是否已关闭，已关闭则抛异常 */
    private void checkClosed() {
        if (closed) {
            throw new VKSecurityException("VKFieldSession is closed");
        }
    }

    /**
     * 处理 null 值：NULL_PASSTHROUGH → 返回 null；REJECT → 抛异常。
     */
    private String handleNull() {
        if (nullPolicy == VKNullPolicy.REJECT) {
            throw new VKSecurityException("Null value rejected by VKNullPolicy.REJECT");
        }
        return null;
    }

    /**
     * 校验并解码 vkf3 密文：Base64 解码 + 版本校验 + 最小长度校验。
     *
     * @param cipher vkf3 Base64 密文
     * @return 解码后的原始字节数组（已通过校验）
     */
    private byte[] validateAndDecode(String cipher) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(cipher);
        } catch (Exception e) {
            throw new VKSecurityException("Invalid vkf3 cipher: not valid Base64");
        }
        if (raw.length < VKFieldCrypto.MIN_CIPHER_BYTES) {
            throw new VKSecurityException(
                    "Invalid vkf3 cipher: too short (length=" + raw.length
                    + ", min=" + VKFieldCrypto.MIN_CIPHER_BYTES + ")");
        }
        if ((raw[0] & 0xFF) != VKFieldCrypto.VERSION) {
            throw new VKSecurityException(
                    "Invalid vkf3 cipher: wrong version byte 0x"
                    + Integer.toHexString(raw[0] & 0xFF)
                    + " (expected 0x03)");
        }
        return raw;
    }
}
