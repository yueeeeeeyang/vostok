package yueyang.vostok.security;

import yueyang.vostok.Vostok;
import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.crypto.VKAesCrypto;
import yueyang.vostok.security.crypto.VKHashCrypto;
import yueyang.vostok.security.crypto.VKRsaCrypto;
import yueyang.vostok.security.crypto.VKRsaKeyPair;
import yueyang.vostok.security.crlf.VKCrlfScanner;
import yueyang.vostok.security.file.VKFileSecurityScanner;
import yueyang.vostok.security.file.VKFileType;
import yueyang.vostok.security.keystore.LocalFileKeyStore;
import yueyang.vostok.security.keystore.VKKeyStore;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;
import yueyang.vostok.security.nosql.VKNoSqlInjectionScanner;
import yueyang.vostok.security.path.VKPathTraversalScanner;
import yueyang.vostok.security.response.VKResponseSecurityScanner;
import yueyang.vostok.security.rule.VKSecurityRule;
import yueyang.vostok.security.sql.VKSqlCheckResult;
import yueyang.vostok.security.sql.VKSqlSecurityScanner;
import yueyang.vostok.security.ssrf.VKSsrfScanner;
import yueyang.vostok.security.xss.VKXssSanitizer;
import yueyang.vostok.security.xss.VKXssScanner;
import yueyang.vostok.security.command.VKCommandInjectionScanner;
import yueyang.vostok.security.xml.VKXxeScanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 安全模块公共 API 门面，所有方法均为静态。
 *
 * <p>功能概览：
 * <ul>
 *   <li>SQL 注入检测（含自定义规则）</li>
 *   <li>XSS 检测 + 净化（Ext1）</li>
 *   <li>命令注入检测</li>
 *   <li>路径遍历检测</li>
 *   <li>SSRF 检测（Ext2）</li>
 *   <li>XXE 注入检测（Ext3）</li>
 *   <li>CRLF 注入检测（Ext4）</li>
 *   <li>响应敏感数据检测与脱敏 + 自定义敏感字段（Ext6）</li>
 *   <li>NoSQL 注入检测（Ext8）</li>
 *   <li>密钥存储（含 TTL 自动轮换，Ext5）</li>
 *   <li>AES/RSA 加解密、哈希/HMAC</li>
 *   <li>安全审计日志（Ext7）：unsafe 结果可选写入 Vostok.Log</li>
 * </ul>
 */
public class VostokSecurity {
    private static final Object LOCK = new Object();
    private static final Object KEY_STORE_LOCK = new Object();
    private static final CopyOnWriteArrayList<VKSecurityRule> CUSTOM_RULES = new CopyOnWriteArrayList<>();

    private static volatile boolean initialized;
    private static volatile VKSecurityConfig config = new VKSecurityConfig();
    private static volatile VKSqlSecurityScanner scanner;
    private static volatile VKKeyStoreConfig keyStoreConfig = new VKKeyStoreConfig();
    private static volatile VKKeyStore keyStore;

    protected VostokSecurity() {
    }

    // ---------------------------------------------------------------- 生命周期

    public static void init() {
        init(new VKSecurityConfig());
    }

    public static void init(VKSecurityConfig newConfig) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            config = (newConfig == null ? new VKSecurityConfig() : newConfig.copy());
            scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            initialized = true;
        }
    }

    public static void reinit(VKSecurityConfig newConfig) {
        synchronized (LOCK) {
            config = (newConfig == null ? new VKSecurityConfig() : newConfig.copy());
            scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            initialized = true;
        }
    }

    public static boolean started() {
        return initialized;
    }

    public static void close() {
        synchronized (LOCK) {
            scanner = null;
            initialized = false;
        }
    }

    public static VKSecurityConfig config() {
        return config.copy();
    }

    // ---------------------------------------------------------------- 规则管理

    public static void registerRule(VKSecurityRule rule) {
        if (rule == null) {
            return;
        }
        CUSTOM_RULES.add(rule);
        synchronized (LOCK) {
            if (initialized) {
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            }
        }
    }

    /**
     * Perf4：批量注册自定义规则，一次性重建 scanner，避免逐条注册触发 N 次重建。
     *
     * @param rules 规则列表；null/空元素自动忽略
     */
    public static void registerRules(List<VKSecurityRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (VKSecurityRule rule : rules) {
            if (rule != null) {
                CUSTOM_RULES.add(rule);
            }
        }
        // 批量完成后只重建一次 scanner
        synchronized (LOCK) {
            if (initialized) {
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            }
        }
    }

    public static void clearCustomRules() {
        CUSTOM_RULES.clear();
        synchronized (LOCK) {
            if (initialized) {
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
            }
        }
    }

    public static List<String> listRules() {
        return current().ruleNames();
    }

    // ---------------------------------------------------------------- SQL 注入检测

    public static VKSqlCheckResult checkSql(String sql) {
        VKSqlCheckResult r = current().checkSql(sql, new Object[0]);
        auditSql(r);
        return r;
    }

    public static VKSqlCheckResult checkSql(String sql, Object... params) {
        VKSqlCheckResult r = current().checkSql(sql, params);
        auditSql(r);
        return r;
    }

    public static boolean isSafeSql(String sql) {
        return checkSql(sql).isSafe();
    }

    public static void assertSafeSql(String sql) {
        VKSqlCheckResult result = checkSql(sql);
        if (!result.isSafe()) {
            throw new VKSecurityException("Unsafe SQL detected: " + String.join("; ", result.getReasons()));
        }
    }

    // ---------------------------------------------------------------- XSS 检测 + 净化（Ext1）

    public static VKSecurityCheckResult checkXss(String input) {
        VKSecurityCheckResult r = VKXssScanner.check(input);
        audit("xss", r);
        return r;
    }

    public static void assertSafeXss(String input) {
        assertSafe("Unsafe XSS payload detected", checkXss(input));
    }

    /**
     * Ext1：对输入进行 HTML 实体编码，使 XSS 载荷失效。
     * 与 {@link #checkXss} 配合使用：check 后决策是拒绝（assertSafe）还是净化（sanitize）。
     *
     * @param input 原始用户输入
     * @return 经 HTML 实体编码的安全字符串
     */
    public static String sanitizeXss(String input) {
        return VKXssSanitizer.sanitize(input);
    }

    // ---------------------------------------------------------------- 命令注入检测

    public static VKSecurityCheckResult checkCommandInjection(String input) {
        VKSecurityCheckResult r = VKCommandInjectionScanner.check(input);
        audit("command", r);
        return r;
    }

    public static void assertSafeCommand(String input) {
        assertSafe("Unsafe command payload detected", checkCommandInjection(input));
    }

    // ---------------------------------------------------------------- 路径遍历检测

    public static VKSecurityCheckResult checkPathTraversal(String inputPath) {
        VKSecurityCheckResult r = VKPathTraversalScanner.check(inputPath);
        audit("path", r);
        return r;
    }

    public static void assertSafePath(String inputPath) {
        assertSafe("Unsafe path traversal payload detected", checkPathTraversal(inputPath));
    }

    // ---------------------------------------------------------------- SSRF 检测（Ext2）

    /**
     * Ext2：检测 URL 是否存在 SSRF（服务端请求伪造）风险。
     * 检测私有 IP、localhost、云元数据地址及危险协议（file://、gopher:// 等）。
     */
    public static VKSecurityCheckResult checkSsrf(String url) {
        VKSecurityCheckResult r = VKSsrfScanner.check(url);
        audit("ssrf", r);
        return r;
    }

    public static void assertSafeSsrf(String url) {
        assertSafe("Unsafe SSRF payload detected", checkSsrf(url));
    }

    // ---------------------------------------------------------------- XXE 注入检测（Ext3）

    /**
     * Ext3：检测 XML 输入是否存在 XXE（XML 外部实体注入）风险。
     * 检测外部实体引用、SYSTEM/PUBLIC 关键字、DOCTYPE 内部子集等特征。
     */
    public static VKSecurityCheckResult checkXxe(String xmlInput) {
        VKSecurityCheckResult r = VKXxeScanner.check(xmlInput);
        audit("xxe", r);
        return r;
    }

    public static void assertSafeXxe(String xmlInput) {
        assertSafe("Unsafe XXE payload detected", checkXxe(xmlInput));
    }

    // ---------------------------------------------------------------- CRLF 注入检测（Ext4）

    /**
     * Ext4：检测 HTTP 响应头字段值中是否存在 CRLF 注入。
     * 检测原始 CR/LF 字符及其 URL 编码形式（含双重编码）。
     */
    public static VKSecurityCheckResult checkCrlf(String headerValue) {
        VKSecurityCheckResult r = VKCrlfScanner.check(headerValue);
        audit("crlf", r);
        return r;
    }

    public static void assertSafeCrlf(String headerValue) {
        assertSafe("Unsafe CRLF payload detected", checkCrlf(headerValue));
    }

    // ---------------------------------------------------------------- 响应敏感数据检测（+ Ext6）

    public static VKSecurityCheckResult checkSensitiveResponse(String payload) {
        VKSecurityCheckResult r = VKResponseSecurityScanner.check(payload);
        audit("sensitive-response", r);
        return r;
    }

    public static String maskSensitiveResponse(String payload) {
        return VKResponseSecurityScanner.mask(payload);
    }

    /**
     * Ext6：注册自定义敏感字段正则，命中时触发 MEDIUM 级告警。
     * 支持业务方扩展（如内部工号、合同编号等），无需修改框架代码。
     *
     * @param regex 正则表达式；编译失败时静默忽略
     */
    public static void registerSensitivePattern(String regex) {
        VKResponseSecurityScanner.addSensitivePattern(regex);
    }

    /** Ext6：清除所有自定义敏感字段正则 */
    public static void clearSensitivePatterns() {
        VKResponseSecurityScanner.clearSensitivePatterns();
    }

    // ---------------------------------------------------------------- NoSQL 注入检测（Ext8）

    /**
     * Ext8：检测输入是否存在 NoSQL 注入风险（主要针对 MongoDB）。
     * 检测 $where JS 注入、JSON 对象中操作符键、$gt/$ne 等操作符。
     */
    public static VKSecurityCheckResult checkNoSqlInjection(String input) {
        VKSecurityCheckResult r = VKNoSqlInjectionScanner.check(input);
        audit("nosql", r);
        return r;
    }

    public static void assertSafeNoSqlInjection(String input) {
        assertSafe("Unsafe NoSQL injection payload detected", checkNoSqlInjection(input));
    }

    // ---------------------------------------------------------------- 文件上传检测

    public static VKFileType detectFileType(byte[] content) {
        return VKFileSecurityScanner.detectType(content);
    }

    public static VKSecurityCheckResult checkFileMagic(byte[] content, VKFileType... allowed) {
        return VKFileSecurityScanner.checkMagicAllowed(content, allowed);
    }

    public static VKSecurityCheckResult checkExecutableScriptUpload(String fileName, byte[] content) {
        return VKFileSecurityScanner.checkExecutableScriptUpload(fileName, content);
    }

    // ---------------------------------------------------------------- 加解密

    public static String generateAesKey() {
        return VKAesCrypto.generateAesKeyBase64();
    }

    public static String encrypt(String plainText, String secret) {
        return VKAesCrypto.encrypt(plainText, secret);
    }

    public static String decrypt(String cipherText, String secret) {
        return VKAesCrypto.decrypt(cipherText, secret);
    }

    public static VKRsaKeyPair generateRsaKeyPair() {
        return VKRsaCrypto.generateRsaKeyPair();
    }

    public static String encryptByPublicKey(String plainText, String publicKeyPem) {
        return VKRsaCrypto.encryptByPublicKey(plainText, publicKeyPem);
    }

    public static String decryptByPrivateKey(String cipherText, String privateKeyPem) {
        return VKRsaCrypto.decryptByPrivateKey(cipherText, privateKeyPem);
    }

    public static String sign(String text, String privateKeyPem) {
        return VKRsaCrypto.sign(text, privateKeyPem);
    }

    public static boolean verify(String text, String signature, String publicKeyPem) {
        return VKRsaCrypto.verify(text, signature, publicKeyPem);
    }

    public static String sha256(String text) {
        return VKHashCrypto.sha256Base64(text);
    }

    public static String sha256Hex(String text) {
        return VKHashCrypto.sha256Hex(text);
    }

    public static String hmacSha256(String text, String secret) {
        return VKHashCrypto.hmacSha256Base64(text, secret);
    }

    // ---------------------------------------------------------------- 密钥存储（+ Ext5 TTL）

    public static void initKeyStore(VKKeyStoreConfig newConfig) {
        synchronized (KEY_STORE_LOCK) {
            keyStoreConfig = newConfig == null ? new VKKeyStoreConfig() : newConfig.copy();
            keyStore = new LocalFileKeyStore(keyStoreConfig);
        }
    }

    public static String getOrCreateAesKey(String keyId) {
        return currentKeyStore().getOrCreateAesKey(keyId);
    }

    public static VKRsaKeyPair getOrCreateRsaKeyPair(String keyId) {
        return currentKeyStore().getOrCreateRsaKeyPair(keyId);
    }

    public static void rotateAesKey(String keyId) {
        currentKeyStore().rotateAesKey(keyId);
    }

    public static void rotateRsaKeyPair(String keyId) {
        currentKeyStore().rotateRsaKeyPair(keyId);
    }

    /**
     * Ext5：检查 AES 密钥是否超过 TTL（距最后写入/轮换时间）。
     * {@code ttlSeconds <= 0} 时永不过期。
     */
    public static boolean isExpiredAesKey(String keyId, long ttlSeconds) {
        return currentKeyStore().isExpiredAesKey(keyId, ttlSeconds);
    }

    /**
     * Ext5：若 AES 密钥已超过 TTL，自动轮换并返回新密钥；否则返回现有密钥（或新建）。
     */
    public static String getOrCreateAesKey(String keyId, long ttlSeconds) {
        return currentKeyStore().getOrCreateAesKey(keyId, ttlSeconds);
    }

    /**
     * Ext5：检查 RSA 密钥对是否超过 TTL。
     */
    public static boolean isExpiredRsaKeyPair(String keyId, long ttlSeconds) {
        return currentKeyStore().isExpiredRsaKeyPair(keyId, ttlSeconds);
    }

    /**
     * Ext5：若 RSA 密钥对已超过 TTL，自动轮换并返回新密钥对；否则返回现有密钥对。
     */
    public static VKRsaKeyPair getOrCreateRsaKeyPair(String keyId, long ttlSeconds) {
        return currentKeyStore().getOrCreateRsaKeyPair(keyId, ttlSeconds);
    }

    /**
     * 使用 Key Wrapping（双层密钥）加密数据，输出 vk2 格式 payload。
     *
     * <p>流程：
     * <ol>
     *   <li>随机生成一次性 DEK（Data Encryption Key）</li>
     *   <li>用 DEK 加密原文，得到 cipher</li>
     *   <li>用 keyId 对应的当前 KEK 包裹 DEK，得到 "{kekVersion}:{wrappedDek}"</li>
     *   <li>组装 payload：{@code vk2:dek:{keyId}:{kekVersion}:{wrappedDek}:{cipher}}</li>
     * </ol>
     *
     * @param plainText 原文
     * @param keyId     KEK 标识符（[A-Za-z0-9._-]+）
     * @return vk2 格式密文 payload
     */
    public static String encryptWithKeyId(String plainText, String keyId) {
        // 每次加密随机生成一次性 DEK，DEK 不存储，仅被 KEK 包裹后随密文一同携带
        String dek = VKAesCrypto.generateAesKeyBase64();
        // 用 DEK 加密原始数据
        String cipher = encrypt(plainText, dek);
        // 用 keyId 的当前 KEK 包裹 DEK，返回 "{kekVersion}:{wrappedDek}"
        String versionedWrapped = currentKeyStore().wrapDek(keyId, dek);
        int idx = versionedWrapped.indexOf(':');
        String kekVersion = versionedWrapped.substring(0, idx);
        String wrappedDek = versionedWrapped.substring(idx + 1);
        // 格式：vk2:dek:{keyId}:{kekVersion}:{wrappedDek}:{cipher}
        return "vk2:dek:" + keyId + ":" + kekVersion + ":" + wrappedDek + ":" + cipher;
    }

    /**
     * 解密 encryptWithKeyId 产生的密文 payload，同时支持 vk1（旧格式）和 vk2（新格式）。
     *
     * <p>vk1 格式（向后兼容）：{@code vk1:aes:{keyId}:{cipher}}
     * <br>vk2 格式（Key Wrapping）：{@code vk2:dek:{keyId}:{kekVersion}:{wrappedDek}:{cipher}}
     *
     * @param cipherPayload 密文 payload
     * @return 原文
     * @throws VKSecurityException 若 payload 格式无效或解密失败
     */
    public static String decryptWithKeyId(String cipherPayload) {
        if (cipherPayload == null || cipherPayload.isBlank()) {
            throw new VKSecurityException("Cipher payload is blank");
        }
        if (cipherPayload.startsWith("vk1:aes:")) {
            // 旧格式：vk1:aes:{keyId}:{cipher}（向后兼容，直接用 keystore AES key 解密）
            String[] parts = cipherPayload.split(":", 4);
            if (parts.length != 4) {
                throw new VKSecurityException("Cipher payload format invalid");
            }
            String keyId = parts[2];
            String cipher = parts[3];
            String key = getOrCreateAesKey(keyId);
            return decrypt(cipher, key);
        } else if (cipherPayload.startsWith("vk2:dek:")) {
            // 新格式：vk2:dek:{keyId}:{kekVersion}:{wrappedDek}:{cipher}
            // split 限制最多 6 段，cipher 本身可能含冒号（Base64 不含冒号，但安全起见限制分割数）
            String[] parts = cipherPayload.split(":", 6);
            if (parts.length != 6) {
                throw new VKSecurityException("Cipher payload format invalid");
            }
            String keyId = parts[2];
            long kekVersion;
            try {
                kekVersion = Long.parseLong(parts[3]);
            } catch (NumberFormatException e) {
                throw new VKSecurityException("Cipher payload format invalid: bad kekVersion");
            }
            String wrappedDek = parts[4];
            String cipher = parts[5];
            // 用历史 KEK（kekVersion 对应版本）解包 DEK，再用 DEK 解密数据
            String dek = currentKeyStore().unwrapDek(keyId, kekVersion, wrappedDek);
            return decrypt(cipher, dek);
        } else {
            throw new VKSecurityException("Cipher payload format invalid");
        }
    }

    /**
     * 轮换指定 keyId 的 KEK（Key Encryption Key）。
     * 旧版本 KEK 保留，历史密文仍可通过对应版本解密；
     * 新加密操作自动使用最新版本 KEK。
     *
     * @param keyId KEK 标识符
     */
    public static void rotateKek(String keyId) {
        currentKeyStore().rotateKek(keyId);
    }

    // ---------------------------------------------------------------- 内部工具

    private static void assertSafe(String message, VKSecurityCheckResult result) {
        if (!result.isSafe()) {
            throw new VKSecurityException(message + ": " + String.join("; ", result.getReasons()));
        }
    }

    /**
     * Ext7：安全扫描审计日志。
     * 当 {@code config.isAuditLog()} 为 true 且结果不安全时，通过 Vostok.Log.warn 输出告警日志。
     * 异常静默处理，不影响主流程。
     */
    private static void audit(String checkType, VKSecurityCheckResult result) {
        if (result.isSafe()) {
            return;
        }
        try {
            if (config.isAuditLog()) {
                Vostok.Log.warn("Vostok.Security audit [{}] UNSAFE risk={} score={} rules={}",
                        checkType, result.getRiskLevel(), result.getScore(),
                        String.join(",", result.getMatchedRules()));
            }
        } catch (Throwable ignore) {
            // 审计日志失败不影响业务流程
        }
    }

    /** SQL 检测审计日志（VKSqlCheckResult 类型适配） */
    private static void auditSql(VKSqlCheckResult result) {
        if (result.isSafe()) {
            return;
        }
        try {
            if (config.isAuditLog()) {
                Vostok.Log.warn("Vostok.Security audit [sql] UNSAFE risk={} score={} rules={}",
                        result.getRiskLevel(), result.getScore(),
                        String.join(",", result.getMatchedRules()));
            }
        } catch (Throwable ignore) {
            // 审计日志失败不影响业务流程
        }
    }

    private static VKSqlSecurityScanner current() {
        VKSqlSecurityScanner s = scanner;
        if (s != null) {
            return s;
        }
        synchronized (LOCK) {
            if (scanner == null) {
                config = config == null ? new VKSecurityConfig() : config.copy();
                scanner = new VKSqlSecurityScanner(config, CUSTOM_RULES);
                initialized = true;
            }
            return scanner;
        }
    }

    private static VKKeyStore currentKeyStore() {
        VKKeyStore s = keyStore;
        if (s != null) {
            return s;
        }
        synchronized (KEY_STORE_LOCK) {
            if (keyStore == null) {
                keyStoreConfig = keyStoreConfig == null ? new VKKeyStoreConfig() : keyStoreConfig.copy();
                keyStore = new LocalFileKeyStore(keyStoreConfig);
            }
            return keyStore;
        }
    }
}
