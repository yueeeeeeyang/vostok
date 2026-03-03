package yueyang.vostok.security.field;

import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.keystore.VKKeyStore;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库字段加密 DEK / Blind Key 缓存。
 *
 * <p>职责：
 * <ul>
 *   <li>维护 columnKeyId → keyIdHash 的注册表，支持自描述解密（无需调用方传入 columnKeyId）。</li>
 *   <li>缓存从 keyStore 加载的 DEK 和 Blind Key，减少重复的密钥解包开销。</li>
 *   <li>通过雷群防护（Thundering Herd）机制确保并发缓存 miss 时只有一个线程去 keyStore 加载。</li>
 * </ul>
 *
 * <p>实例由 {@link yueyang.vostok.security.VostokSecurity} 持有并通过 {@code currentDekCache()} 提供访问。
 *
 * <p>线程安全：所有公共方法均为并发安全。
 */
public final class VKTableDekCache {

    private final VKKeyStore keyStore;
    private final VKFieldEncryptConfig config;

    /**
     * DEK 缓存：key = "columnKeyId:dekVersion" → CachedEntry。
     * 同一 columnKeyId 可能存在多个版本（当前版本 + 历史版本，均可用于解密）。
     */
    private final ConcurrentHashMap<String, CachedEntry> dekCache = new ConcurrentHashMap<>();

    /**
     * Blind Key 缓存：key = "columnKeyId{blindSuffix}" → CachedEntry。
     * Blind Key 终身不轮换，通常永久缓存。
     */
    private final ConcurrentHashMap<String, CachedEntry> blindCache = new ConcurrentHashMap<>();

    /**
     * keyIdHash → columnKeyId 映射（JVM 进程内）。
     * 用于自描述解密：从密文头部读出 keyIdHash 后反查 columnKeyId。
     * 注意：此映射仅保证进程内唯一，不防跨进程碰撞。
     */
    private final ConcurrentHashMap<Integer, String> hashRegistry = new ConcurrentHashMap<>();

    /**
     * 雷群防护锁池：key = cacheKey → Object 锁。
     * 缓存 miss 时，同一 cacheKey 的多个线程共用同一把锁，只有一个线程执行加载，其余等待后复用结果。
     */
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public VKTableDekCache(VKKeyStore keyStore, VKFieldEncryptConfig config) {
        this.keyStore = keyStore;
        this.config = config;
    }

    /**
     * 注册 columnKeyId，建立 keyIdHash → columnKeyId 映射。
     *
     * <p>幂等操作：同一 columnKeyId 重复注册不报错。
     * 若不同 columnKeyId 产生相同 keyIdHash（SHA-256 前 4 字节碰撞），抛 {@link VKSecurityException}。
     * 碰撞检测范围：当前 JVM 进程内，不跨进程。
     *
     * @param columnKeyId 列级密钥 ID
     * @throws VKSecurityException 若不同 columnKeyId 产生 keyIdHash 碰撞
     */
    public void registerColumnKey(String columnKeyId) {
        int hash = VKFieldCrypto.computeKeyIdHash(columnKeyId);
        // putIfAbsent 原子操作：若该 hash 已有其他 columnKeyId 注册则返回现有值
        String existing = hashRegistry.putIfAbsent(hash, columnKeyId);
        if (existing != null && !existing.equals(columnKeyId)) {
            throw new VKSecurityException(
                    "keyIdHash collision detected: columnKeyId='" + columnKeyId
                    + "' collides with already registered '" + existing + "'");
        }
    }

    /**
     * 自描述解密查表：通过 keyIdHash 查找对应的 columnKeyId。
     *
     * @param keyIdHash 密文头部中的 keyIdHash（SHA-256 前 4 字节大端 int32）
     * @return 对应的 columnKeyId
     * @throws VKSecurityException 若 keyIdHash 未注册（columnKeyId 从未 registerColumnKey）
     */
    public String resolveColumnKeyId(int keyIdHash) {
        String columnKeyId = hashRegistry.get(keyIdHash);
        if (columnKeyId == null) {
            throw new VKSecurityException(
                    "Unknown keyIdHash=0x" + Integer.toHexString(keyIdHash)
                    + "; call registerColumnKey(columnKeyId) before decryptField");
        }
        return columnKeyId;
    }

    /**
     * 加密路径：获取 columnKeyId 的当前版本 DEK。
     * 若 keyStore 中尚未创建任何 DEK，自动创建第一个版本。
     *
     * @param columnKeyId 表级密钥 ID
     * @return 当前 DEK 句柄（含版本号和 SecretKey）
     */
    public DekHandle currentDek(String columnKeyId) {
        // 每次读取最新版本号（文件读），确保 DEK 轮换后自动切换到新版本
        int version = keyStore.getFieldDekVersion(columnKeyId);
        if (version == 0) {
            // 尚未创建 DEK，原子性创建第一个版本
            version = keyStore.createNextFieldDek(columnKeyId);
        }
        SecretKey dek = getDekForVersion(columnKeyId, version);
        return new DekHandle(version, dek);
    }

    /**
     * 解密路径：获取 columnKeyId 指定版本的 DEK（优先从缓存读取）。
     *
     * <p>雷群防护流程：
     * <ol>
     *   <li>快速路径：直接检查 dekCache（无锁读，大多数情况命中）</li>
     *   <li>缓存 miss：获取 cacheKey 对应锁对象</li>
     *   <li>加锁后双重检查（防止多线程同时进入加载）</li>
     *   <li>仅一个线程执行 keyStore 加载，其余线程等待并复用结果</li>
     * </ol>
     *
     * @param columnKeyId 表级密钥 ID
     * @param dekVersion DEK 版本号
     * @return 对应版本的 AES SecretKey
     */
    public SecretKey getDekForVersion(String columnKeyId, int dekVersion) {
        int ttl = config.getDekCacheTtlSeconds();

        // TTL == 0 表示禁用缓存，每次直接从 keyStore 加载
        if (ttl == 0) {
            return loadDek(columnKeyId, dekVersion);
        }

        String cacheKey = columnKeyId + ":" + dekVersion;

        // 快速路径：无锁读 ConcurrentHashMap（大多数情况命中，避免锁竞争）
        CachedEntry entry = dekCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            return entry.key;
        }

        // 缓存 miss：雷群防护，同一 cacheKey 共用同一把锁
        Object lock = loadLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            // 双重检查：可能另一线程已加载完成
            entry = dekCache.get(cacheKey);
            if (entry != null && !entry.isExpired()) {
                return entry.key;
            }
            SecretKey dek = loadDek(columnKeyId, dekVersion);
            long expireAt = System.currentTimeMillis() + (long) ttl * 1000;
            dekCache.put(cacheKey, new CachedEntry(dek, expireAt));
            return dek;
        }
    }

    /**
     * 获取 columnKeyId 的 Blind Key（优先从缓存读取）。
     * Blind Key 终身不轮换，通常长期有效。
     *
     * @param columnKeyId 表级密钥 ID
     * @return Blind Key 的 AES SecretKey（用于 HMAC-SHA256 计算）
     */
    public SecretKey getBlindKey(String columnKeyId) {
        int ttl = config.getDekCacheTtlSeconds();

        // TTL == 0 表示禁用缓存
        if (ttl == 0) {
            return loadBlindKey(columnKeyId);
        }

        String cacheKey = columnKeyId + config.getBlindKeyIdSuffix();

        // 快速路径
        CachedEntry entry = blindCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            return entry.key;
        }

        // 雷群防护
        Object lock = loadLocks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            // 双重检查
            entry = blindCache.get(cacheKey);
            if (entry != null && !entry.isExpired()) {
                return entry.key;
            }
            SecretKey blindKey = loadBlindKey(columnKeyId);
            long expireAt = System.currentTimeMillis() + (long) ttl * 1000;
            blindCache.put(cacheKey, new CachedEntry(blindKey, expireAt));
            return blindKey;
        }
    }

    /**
     * 清除指定 columnKeyId 的所有 DEK + Blind Key 缓存条目，以及 hashRegistry 中的对应条目。
     *
     * <p>调用场景：DEK 轮换后主动使缓存失效，迫使下次访问时重新加载新 DEK。
     *
     * @param columnKeyId 表级密钥 ID
     */
    public void invalidate(String columnKeyId) {
        // 清除该 columnKeyId 所有版本的 DEK 缓存
        dekCache.keySet().removeIf(k -> k.startsWith(columnKeyId + ":"));
        // 清除 blind key 缓存
        String blindCacheKey = columnKeyId + config.getBlindKeyIdSuffix();
        blindCache.remove(blindCacheKey);
        // 从 hashRegistry 移除（以 columnKeyId 为条件精确删除，避免误删碰撞注册的其他 key）
        int hash = VKFieldCrypto.computeKeyIdHash(columnKeyId);
        hashRegistry.remove(hash, columnKeyId);
    }

    /**
     * 清除所有缓存（DEK + Blind Key + hashRegistry）。
     *
     * <p>调用场景：安全模块 close()，或需要强制全局重新初始化时。
     */
    public void invalidateAll() {
        dekCache.clear();
        blindCache.clear();
        hashRegistry.clear();
    }

    // ---------------------------------------------------------------- 私有方法：keyStore 加载

    /**
     * 从 keyStore 加载并解包指定版本的 DEK。
     *
     * <p>流程：
     * <ol>
     *   <li>{@code keyStore.loadFieldWrappedDek(columnKeyId, version)} → {@code "{kekVersion}:{wrappedDek}"}</li>
     *   <li>解析 kekVersion 和 wrappedDek</li>
     *   <li>{@code keyStore.unwrapDek(columnKeyId, kekVersion, wrappedDek)} → dekBase64</li>
     *   <li>Base64 解码 → new SecretKeySpec(bytes, "AES")</li>
     * </ol>
     */
    private SecretKey loadDek(String columnKeyId, int dekVersion) {
        // 加载 "{kekVersion}:{wrappedDekBase64}"
        String wrapped = keyStore.loadFieldWrappedDek(columnKeyId, dekVersion);
        int idx = wrapped.indexOf(':');
        if (idx < 0) {
            throw new VKSecurityException(
                    "Invalid wrapped DEK format for columnKeyId=" + columnKeyId + " version=" + dekVersion);
        }
        long kekVersion;
        try {
            kekVersion = Long.parseLong(wrapped.substring(0, idx));
        } catch (NumberFormatException e) {
            throw new VKSecurityException(
                    "Invalid kekVersion in wrapped DEK for columnKeyId=" + columnKeyId);
        }
        String wrappedDek = wrapped.substring(idx + 1);
        // 用历史 KEK（kekVersion 版本）解包 DEK
        String dekBase64 = keyStore.unwrapDek(columnKeyId, kekVersion, wrappedDek);
        byte[] keyBytes = Base64.getDecoder().decode(dekBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 从 keyStore 获取或创建 Blind Key，并解包返回。
     *
     * <p>流程与 loadDek 类似，但调用 {@code keyStore.getOrCreateFieldBlindKey}。
     */
    private SecretKey loadBlindKey(String columnKeyId) {
        // 获取或创建 "{kekVersion}:{wrappedBlindKeyBase64}"
        String wrapped = keyStore.getOrCreateFieldBlindKey(columnKeyId, config.getBlindKeyIdSuffix());
        int idx = wrapped.indexOf(':');
        if (idx < 0) {
            throw new VKSecurityException(
                    "Invalid wrapped blind key format for columnKeyId=" + columnKeyId);
        }
        long kekVersion;
        try {
            kekVersion = Long.parseLong(wrapped.substring(0, idx));
        } catch (NumberFormatException e) {
            throw new VKSecurityException(
                    "Invalid kekVersion in wrapped blind key for columnKeyId=" + columnKeyId);
        }
        String wrappedBlindKey = wrapped.substring(idx + 1);
        // 用历史 KEK 解包 Blind Key
        String blindKeyBase64 = keyStore.unwrapDek(columnKeyId, kekVersion, wrappedBlindKey);
        byte[] keyBytes = Base64.getDecoder().decode(blindKeyBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // ---------------------------------------------------------------- 内部数据结构

    /**
     * 缓存条目：密钥 + 过期时间戳。
     *
     * <p>过期语义：
     * <ul>
     *   <li>{@code expireAt == 0}：永不过期（保留作为扩展，当前未使用）</li>
     *   <li>{@code expireAt > 0}：Unix 毫秒时间戳，超过则视为过期</li>
     * </ul>
     */
    private record CachedEntry(SecretKey key, long expireAt) {
        /**
         * 检查是否已过期。
         * expireAt == 0 视为永不过期。
         */
        boolean isExpired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 当前 DEK 句柄：含版本号和对应 SecretKey。
     * 由 {@link #currentDek(String)} 返回，调用方用于加密操作。
     */
    public record DekHandle(int version, SecretKey dek) {
    }
}
