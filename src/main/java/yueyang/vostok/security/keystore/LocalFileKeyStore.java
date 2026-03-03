package yueyang.vostok.security.keystore;

import yueyang.vostok.security.crypto.VKAesCrypto;
import yueyang.vostok.security.crypto.VKRsaCrypto;
import yueyang.vostok.security.crypto.VKRsaKeyPair;
import yueyang.vostok.security.exception.VKSecurityException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 基于本地文件系统的密钥存储实现。
 *
 * <p>Perf3 fix：将全方法粗粒度 {@code synchronized} 改为 per-keyId 细粒度锁（ConcurrentHashMap 条目锁）。
 * 不同 keyId 的操作不再互相阻塞，大幅提升高并发场景下的吞吐量。
 * 相同 keyId 的操作仍通过同一把锁串行化，保证幂等性。
 *
 * <p>Ext5：实现 TTL（密钥有效期）支持，利用文件最后修改时间（{@code FileTime}）作为密钥创建/轮换时间戳，
 * 无需额外 .ts 文件。
 */
public final class LocalFileKeyStore implements VKKeyStore {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final VKKeyStoreConfig config;
    private final Path baseDir;

    /**
     * Perf3 fix：per-keyId 锁对象池。
     * {@code computeIfAbsent} 保证同一 keyId 始终获得同一个锁对象。
     * 不同 keyId 使用不同锁，彼此独立，消除不必要的串行化。
     */
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    public LocalFileKeyStore(VKKeyStoreConfig config) {
        this.config = config == null ? new VKKeyStoreConfig() : config.copy();
        this.baseDir = Path.of(this.config.getBaseDir());
        if (this.config.isAutoCreate()) {
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                throw new VKSecurityException("Create keystore dir failed: " + e.getMessage());
            }
        }
    }

    @Override
    public String getOrCreateAesKey(String keyId) {
        String id = normalizeKeyId(keyId);
        // Perf3 fix: 仅对当前 keyId 加锁，不阻塞其他 keyId 的并发操作
        synchronized (lockFor(id)) {
            Path file = baseDir.resolve(id + ".aes.key");
            if (Files.exists(file)) {
                return decryptValue(readText(file));
            }
            String key = VKAesCrypto.generateAesKeyBase64();
            writeText(file, encryptValue(key));
            return key;
        }
    }

    @Override
    public VKRsaKeyPair getOrCreateRsaKeyPair(String keyId) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            Path pub = baseDir.resolve(id + ".rsa.public.pem");
            Path pri = baseDir.resolve(id + ".rsa.private.pem");
            if (Files.exists(pub) && Files.exists(pri)) {
                return new VKRsaKeyPair(decryptValue(readText(pub)), decryptValue(readText(pri)));
            }
            VKRsaKeyPair pair = VKRsaCrypto.generateRsaKeyPair();
            writeText(pub, encryptValue(pair.getPublicKeyPem()));
            writeText(pri, encryptValue(pair.getPrivateKeyPem()));
            return pair;
        }
    }

    @Override
    public void rotateAesKey(String keyId) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            Path file = baseDir.resolve(id + ".aes.key");
            String key = VKAesCrypto.generateAesKeyBase64();
            writeText(file, encryptValue(key));
        }
    }

    @Override
    public void rotateRsaKeyPair(String keyId) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            VKRsaKeyPair pair = VKRsaCrypto.generateRsaKeyPair();
            writeText(baseDir.resolve(id + ".rsa.public.pem"), encryptValue(pair.getPublicKeyPem()));
            writeText(baseDir.resolve(id + ".rsa.private.pem"), encryptValue(pair.getPrivateKeyPem()));
        }
    }

    // ---------------------------------------------------------------- Ext5: TTL 支持

    /**
     * Ext5：检查 AES 密钥是否超过 TTL。
     * 以密钥文件的最后修改时间（即最后写入/轮换时间）为基准。
     * {@code ttlSeconds <= 0} 时永不过期，返回 false。
     */
    @Override
    public boolean isExpiredAesKey(String keyId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return false;
        }
        String id = normalizeKeyId(keyId);
        Path file = baseDir.resolve(id + ".aes.key");
        return isFileExpired(file, ttlSeconds);
    }

    /**
     * Ext5：若 AES 密钥已超过 TTL，自动轮换并返回新密钥；否则返回现有密钥。
     * 操作原子：过期检查与轮换在同一把 keyId 锁内完成，防止并发双重轮换。
     */
    @Override
    public String getOrCreateAesKey(String keyId, long ttlSeconds) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            // 在锁内再次检查过期，避免并发两个线程都进入轮换
            if (ttlSeconds > 0 && isExpiredAesKey(keyId, ttlSeconds)) {
                Path file = baseDir.resolve(id + ".aes.key");
                String newKey = VKAesCrypto.generateAesKeyBase64();
                writeText(file, encryptValue(newKey));
                return newKey;
            }
            // 未过期，走普通 getOrCreate 路径（锁已持有，直接操作文件）
            Path file = baseDir.resolve(id + ".aes.key");
            if (Files.exists(file)) {
                return decryptValue(readText(file));
            }
            String key = VKAesCrypto.generateAesKeyBase64();
            writeText(file, encryptValue(key));
            return key;
        }
    }

    /**
     * Ext5：检查 RSA 密钥对是否超过 TTL（以公钥文件修改时间为准）。
     */
    @Override
    public boolean isExpiredRsaKeyPair(String keyId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return false;
        }
        String id = normalizeKeyId(keyId);
        return isFileExpired(baseDir.resolve(id + ".rsa.public.pem"), ttlSeconds);
    }

    /**
     * Ext5：若 RSA 密钥对已超过 TTL，自动轮换并返回新密钥对；否则返回现有密钥对。
     */
    @Override
    public VKRsaKeyPair getOrCreateRsaKeyPair(String keyId, long ttlSeconds) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            if (ttlSeconds > 0 && isExpiredRsaKeyPair(keyId, ttlSeconds)) {
                VKRsaKeyPair pair = VKRsaCrypto.generateRsaKeyPair();
                writeText(baseDir.resolve(id + ".rsa.public.pem"), encryptValue(pair.getPublicKeyPem()));
                writeText(baseDir.resolve(id + ".rsa.private.pem"), encryptValue(pair.getPrivateKeyPem()));
                return pair;
            }
            Path pub = baseDir.resolve(id + ".rsa.public.pem");
            Path pri = baseDir.resolve(id + ".rsa.private.pem");
            if (Files.exists(pub) && Files.exists(pri)) {
                return new VKRsaKeyPair(decryptValue(readText(pub)), decryptValue(readText(pri)));
            }
            VKRsaKeyPair pair = VKRsaCrypto.generateRsaKeyPair();
            writeText(pub, encryptValue(pair.getPublicKeyPem()));
            writeText(pri, encryptValue(pair.getPrivateKeyPem()));
            return pair;
        }
    }

    // ---------------------------------------------------------------- Key Wrapping（双层密钥）

    /**
     * 用 keyId 当前 KEK 包裹 DEK，原子操作（版本获取 + AES 加密在同一 keyId 锁内）。
     * 首次调用时自动创建 v1 KEK。
     *
     * @param keyId     KEK 标识符
     * @param dekBase64 待包裹的 DEK（Base64 格式）
     * @return "{kekVersion}:{wrappedDekBase64}"
     */
    @Override
    public String wrapDek(String keyId, String dekBase64) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            // 确保 KEK 存在（首次调用时创建 v1），返回当前版本号
            long version = ensureKekVersion(id);
            String kek = readKek(id, version);
            // 用 KEK 加密 DEK
            String wrappedDek = VKAesCrypto.encrypt(dekBase64, kek);
            return version + ":" + wrappedDek;
        }
    }

    /**
     * 解包被历史 KEK 包裹的 DEK，支持跨轮换解密。
     * 直接读取 kekVersion 对应的 KEK 文件，不依赖当前最新版本。
     *
     * @param keyId      KEK 标识符
     * @param kekVersion 包裹 DEK 时使用的 KEK 版本号
     * @param wrappedDek 被 KEK 加密后的 DEK（Base64）
     * @return 原始 DEK（Base64 格式）
     * @throws VKSecurityException 若指定版本的 KEK 文件不存在
     */
    @Override
    public String unwrapDek(String keyId, long kekVersion, String wrappedDek) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            // 验证指定版本的 KEK 文件存在，否则无法解密
            Path kekFile = baseDir.resolve(id + ".kek.v" + kekVersion);
            if (!Files.exists(kekFile)) {
                throw new VKSecurityException(
                        "KEK not found for keyId=" + keyId + " version=" + kekVersion);
            }
            String kek = readKek(id, kekVersion);
            return VKAesCrypto.decrypt(wrappedDek, kek);
        }
    }

    /**
     * 轮换 KEK：追加新版本（v{N+1}），旧版本文件保留，历史密文仍可解密。
     * 若 KEK 从未初始化（version==0），轮换后版本号为 1。
     *
     * @param keyId KEK 标识符
     */
    @Override
    public void rotateKek(String keyId) {
        String id = normalizeKeyId(keyId);
        synchronized (lockFor(id)) {
            // 读取当前版本号（0 表示尚未创建 KEK）
            long current = currentKekVersion(id);
            // 生成新 KEK 并写入下一版本文件
            String newKek = VKAesCrypto.generateAesKeyBase64();
            writeText(baseDir.resolve(id + ".kek.v" + (current + 1)), encryptValue(newKek));
            // 更新版本号文件（旧版本文件不删除，保留用于历史密文解密）
            writeKekVersion(id, current + 1);
        }
    }

    // ---------------------------------------------------------------- 字段级加密（vkf3）

    /**
     * 读取 columnKeyId 的当前 Field DEK 版本号。
     * 文件 {@code {id}.fdek.ver} 存储纯文本整数，不存在时返回 0。
     *
     * @param columnKeyId 表级密钥 ID
     * @return 当前版本号；0 表示尚未创建
     */
    @Override
    public int getFieldDekVersion(String columnKeyId) {
        String id = normalizeKeyId(columnKeyId);
        synchronized (lockFor(id)) {
            return readFieldDekVersionLocked(id);
        }
    }

    /**
     * 加载指定版本的 wrapped DEK，返回 {@code "{kekVersion}:{wrappedDekBase64}"}。
     * 文件 {@code {id}.fdek.v{dekVersion}} 存储 masterKey 加密后的字符串。
     *
     * @param columnKeyId 表级密钥 ID
     * @param dekVersion DEK 版本号
     * @return 解密后的 {@code "{kekVersion}:{wrappedDekBase64}"}
     */
    @Override
    public String loadFieldWrappedDek(String columnKeyId, int dekVersion) {
        String id = normalizeKeyId(columnKeyId);
        synchronized (lockFor(id)) {
            Path file = baseDir.resolve(id + ".fdek.v" + dekVersion);
            if (!Files.exists(file)) {
                throw new VKSecurityException(
                        "Field DEK not found for columnKeyId=" + columnKeyId + " version=" + dekVersion);
            }
            return decryptValue(readText(file));
        }
    }

    /**
     * 原子性创建下一版 Field DEK，在 per-keyId 锁内完成：
     * <ol>
     *   <li>读当前版本号</li>
     *   <li>生成新 DEK（AES-256）</li>
     *   <li>用当前 KEK 包裹（{@link #wrapDek} 可重入，安全）</li>
     *   <li>写 {@code {id}.fdek.v{next}} = encryptValue("{kekVersion}:{wrappedDek}")</li>
     *   <li>写 {@code {id}.fdek.ver} = next</li>
     * </ol>
     *
     * @param columnKeyId 表级密钥 ID
     * @return 新 DEK 版本号
     */
    @Override
    public int createNextFieldDek(String columnKeyId) {
        String id = normalizeKeyId(columnKeyId);
        synchronized (lockFor(id)) {
            // 读当前版本（锁内文件读，防并发竞争）
            int current = readFieldDekVersionLocked(id);
            int next = current + 1;
            // 生成新 DEK（256 位 AES）
            String newDekBase64 = VKAesCrypto.generateAesKeyBase64();
            // 用当前 KEK 包裹 DEK（wrapDek 内部 synchronized 可重入，same thread safe）
            String wrappedResult = wrapDek(columnKeyId, newDekBase64);
            // 写 DEK 文件：masterKey 二次加密保证存储安全
            writeText(baseDir.resolve(id + ".fdek.v" + next), encryptValue(wrappedResult));
            // 更新版本号文件（原子写，后写版本确保文件存在后才更新指针）
            writeText(baseDir.resolve(id + ".fdek.ver"), String.valueOf(next));
            return next;
        }
    }

    /**
     * 获取或创建 Blind Key，在 per-keyId 锁内完成。
     * 文件已存在则直接读取（Blind Key 不轮换），否则生成新密钥并持久化。
     *
     * @param columnKeyId  表级密钥 ID
     * @param blindSuffix Blind Key 文件名后缀（追加到 id 后，如 ".blind"）
     * @return {@code "{kekVersion}:{wrappedBlindKeyBase64}"}
     */
    @Override
    public String getOrCreateFieldBlindKey(String columnKeyId, String blindSuffix) {
        String id = normalizeKeyId(columnKeyId);
        synchronized (lockFor(id)) {
            // blindSuffix 追加到 id 后作为文件名（如 "users.blind"）
            Path file = baseDir.resolve(id + blindSuffix);
            if (Files.exists(file)) {
                // Blind Key 已存在：直接读取解密后返回
                return decryptValue(readText(file));
            }
            // 首次创建：生成新 Blind Key
            String newBlindKeyBase64 = VKAesCrypto.generateAesKeyBase64();
            // 用当前 KEK 包裹（wrapDek 可重入，safe）
            String wrappedResult = wrapDek(columnKeyId, newBlindKeyBase64);
            // 持久化（masterKey 二次加密）
            writeText(file, encryptValue(wrappedResult));
            return wrappedResult;
        }
    }

    /**
     * 在锁内读取 Field DEK 版本号（无需再次获取锁的内部方法）。
     *
     * @param id 规范化后的 columnKeyId
     * @return 版本号；文件不存在时返回 0
     */
    private int readFieldDekVersionLocked(String id) {
        Path verFile = baseDir.resolve(id + ".fdek.ver");
        if (!Files.exists(verFile)) {
            return 0;
        }
        try {
            return Integer.parseInt(readText(verFile).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 读取 keyId 的当前 KEK 版本号（来自 .kek.ver 文件）。
     *
     * @return 当前版本号；文件不存在时返回 0（表示 KEK 尚未创建）
     */
    private long currentKekVersion(String id) {
        Path verFile = baseDir.resolve(id + ".kek.ver");
        if (!Files.exists(verFile)) {
            return 0;
        }
        try {
            return Long.parseLong(readText(verFile).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 确保 KEK 已初始化：若当前版本为 0，自动创建 v1 KEK 并写入版本号文件。
     *
     * @return 当前（有效）KEK 版本号（>= 1）
     */
    private long ensureKekVersion(String id) {
        long version = currentKekVersion(id);
        if (version == 0) {
            // 首次使用，创建 v1 KEK
            String kek = VKAesCrypto.generateAesKeyBase64();
            writeText(baseDir.resolve(id + ".kek.v1"), encryptValue(kek));
            writeKekVersion(id, 1);
            version = 1;
        }
        return version;
    }

    /**
     * 写入 .kek.ver 版本号文件（纯文本整数）。
     *
     * @param id 规范化后的 keyId
     * @param v  要写入的版本号
     */
    private void writeKekVersion(String id, long v) {
        writeText(baseDir.resolve(id + ".kek.ver"), String.valueOf(v));
    }

    /**
     * 读取并解密指定版本的 KEK 文件（{id}.kek.v{v}）。
     * 调用方须保证文件存在（已在 wrapDek/unwrapDek 中校验）。
     *
     * @param id 规范化后的 keyId
     * @param v  KEK 版本号
     * @return 解密后的 KEK（Base64 格式）
     */
    private String readKek(String id, long v) {
        Path kekFile = baseDir.resolve(id + ".kek.v" + v);
        return decryptValue(readText(kekFile));
    }

    // ---------------------------------------------------------------- 工具方法

    /**
     * Perf3 fix：返回 keyId 对应的锁对象。
     * {@code computeIfAbsent} 保证同 keyId 同锁，不同 keyId 不同锁。
     */
    private Object lockFor(String keyId) {
        return keyLocks.computeIfAbsent(keyId, k -> new Object());
    }

    /**
     * 检查文件是否超过 TTL（以文件最后修改时间为密钥创建/轮换时间戳）。
     */
    private static boolean isFileExpired(Path file, long ttlSeconds) {
        if (!Files.exists(file)) {
            return false;
        }
        try {
            FileTime modTime = Files.getLastModifiedTime(file);
            long ageMs = System.currentTimeMillis() - modTime.toMillis();
            return ageMs > ttlSeconds * 1000L;
        } catch (IOException e) {
            return false;
        }
    }

    private String encryptValue(String value) {
        return VKAesCrypto.encrypt(value, config.getMasterKey());
    }

    private String decryptValue(String encryptedValue) {
        return VKAesCrypto.decrypt(encryptedValue, config.getMasterKey());
    }

    private String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VKSecurityException("Read key file failed: " + e.getMessage());
        }
    }

    private void writeText(Path file, String value) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, value, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new VKSecurityException("Write key file failed: " + e.getMessage());
        }
    }

    private static String normalizeKeyId(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            throw new VKSecurityException("keyId is blank");
        }
        String id = keyId.trim();
        if (!KEY_ID_PATTERN.matcher(id).matches()) {
            throw new VKSecurityException("keyId invalid: " + keyId);
        }
        return id;
    }
}
