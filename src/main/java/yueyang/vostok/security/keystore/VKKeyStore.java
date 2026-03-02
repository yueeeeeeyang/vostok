package yueyang.vostok.security.keystore;

import yueyang.vostok.security.crypto.VKRsaKeyPair;

/**
 * 密钥存储接口。
 *
 * <p>Ext5：新增 TTL（密钥有效期）支持：
 * <ul>
 *   <li>{@link #isExpiredAesKey(String, long)}：检查 AES 密钥是否已超过 TTL</li>
 *   <li>{@link #getOrCreateAesKey(String, long)}：TTL 过期时自动轮换后返回新密钥</li>
 *   <li>{@link #isExpiredRsaKeyPair(String, long)}：检查 RSA 密钥对是否已超过 TTL</li>
 *   <li>{@link #getOrCreateRsaKeyPair(String, long)}：TTL 过期时自动轮换</li>
 * </ul>
 * TTL 以秒为单位，{@code ttlSeconds <= 0} 表示永不过期。
 * 默认实现不做过期检查，自定义 {@link VKKeyStore} 实现需覆盖 default 方法以支持 TTL。
 */
public interface VKKeyStore {

    /** 获取（不存在则创建）指定 keyId 对应的 AES 密钥（Base64 格式） */
    String getOrCreateAesKey(String keyId);

    /** 获取（不存在则创建）指定 keyId 对应的 RSA 密钥对 */
    VKRsaKeyPair getOrCreateRsaKeyPair(String keyId);

    /** 轮换指定 keyId 的 AES 密钥（生成新密钥覆盖旧密钥） */
    void rotateAesKey(String keyId);

    /** 轮换指定 keyId 的 RSA 密钥对（生成新密钥对覆盖旧密钥对） */
    void rotateRsaKeyPair(String keyId);

    // ---------------------------------------------------------------- Ext5: TTL 支持

    /**
     * Ext5：检查 AES 密钥是否已超过 TTL（距最后写入时间）。
     * {@code ttlSeconds <= 0} 时固定返回 false（永不过期）。
     *
     * @param keyId      密钥 ID
     * @param ttlSeconds TTL 秒数；{@code <= 0} 表示不限制
     * @return true 表示已过期，应调用 rotateAesKey 或 getOrCreateAesKey(keyId, ttl) 轮换
     */
    default boolean isExpiredAesKey(String keyId, long ttlSeconds) {
        return false;
    }

    /**
     * Ext5：若 AES 密钥已超过 TTL，自动轮换后返回新密钥；否则返回现有密钥。
     * {@code ttlSeconds <= 0} 时等价于 {@link #getOrCreateAesKey(String)}。
     */
    default String getOrCreateAesKey(String keyId, long ttlSeconds) {
        return getOrCreateAesKey(keyId);
    }

    /**
     * Ext5：检查 RSA 密钥对是否已超过 TTL（以公钥文件修改时间为准）。
     */
    default boolean isExpiredRsaKeyPair(String keyId, long ttlSeconds) {
        return false;
    }

    /**
     * Ext5：若 RSA 密钥对已超过 TTL，自动轮换后返回新密钥对；否则返回现有密钥对。
     */
    default VKRsaKeyPair getOrCreateRsaKeyPair(String keyId, long ttlSeconds) {
        return getOrCreateRsaKeyPair(keyId);
    }

    // ---------------------------------------------------------------- Key Wrapping（双层密钥）

    /**
     * 用 keyId 当前 KEK（Key Encryption Key）包裹 DEK（Data Encryption Key）。
     * 若 KEK 尚未创建，自动初始化 v1 版本。
     * 操作原子性：版本获取与 AES 加密在同一 keyId 锁内完成，防止并发竞争。
     *
     * @param keyId     KEK 标识符（[A-Za-z0-9._-]+，无冒号）
     * @param dekBase64 待包裹的 DEK（Base64 格式）
     * @return "{kekVersion}:{wrappedDekBase64}"，kekVersion 为正整数
     * @throws UnsupportedOperationException 若实现不支持 Key Wrapping
     */
    default String wrapDek(String keyId, String dekBase64) {
        throw new UnsupportedOperationException("Key wrapping not supported");
    }

    /**
     * 解包被历史 KEK 包裹的 DEK，支持跨轮换解密（kekVersion 来自密文 payload）。
     * 读取对应版本的 KEK 文件并解密 wrappedDek，不依赖当前版本号。
     *
     * @param keyId      KEK 标识符
     * @param kekVersion 包裹 DEK 时使用的 KEK 版本号（正整数，来自 payload）
     * @param wrappedDek 被 KEK 加密后的 DEK（Base64）
     * @return 原始 DEK（Base64 格式）
     * @throws yueyang.vostok.security.exception.VKSecurityException 若指定版本的 KEK 不存在
     * @throws UnsupportedOperationException                         若实现不支持 Key Wrapping
     */
    default String unwrapDek(String keyId, long kekVersion, String wrappedDek) {
        throw new UnsupportedOperationException("Key wrapping not supported");
    }

    /**
     * 轮换 KEK：追加新版本（v{N+1}），旧版本文件保留不删除。
     * 历史密文仍可通过旧版本 KEK 解密，不影响已有密文的可用性。
     *
     * @param keyId KEK 标识符
     * @throws UnsupportedOperationException 若实现不支持 KEK 轮换
     */
    default void rotateKek(String keyId) {
        throw new UnsupportedOperationException("KEK rotation not supported");
    }
}
