package yueyang.vostok.security;

import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.crypto.VKAesCrypto;
import yueyang.vostok.security.crypto.VKHashCrypto;
import yueyang.vostok.security.crypto.VKRsaCrypto;
import yueyang.vostok.security.crypto.VKRsaKeyPair;
import yueyang.vostok.security.file.VKFileSecurityScanner;
import yueyang.vostok.security.file.VKFileType;
import yueyang.vostok.security.keystore.LocalFileKeyStore;
import yueyang.vostok.security.keystore.VKKeyStore;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;
import yueyang.vostok.security.path.VKPathTraversalScanner;
import yueyang.vostok.security.response.VKResponseSecurityScanner;
import yueyang.vostok.security.rule.VKSecurityRule;
import yueyang.vostok.security.sql.VKSqlCheckResult;
import yueyang.vostok.security.sql.VKSqlSecurityScanner;
import yueyang.vostok.security.xss.VKXssScanner;
import yueyang.vostok.security.command.VKCommandInjectionScanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VostokSecurity {
    private static final Object LOCK = new Object();
    private static final Object KEY_STORE_LOCK = new Object();
    private static final List<VKSecurityRule> CUSTOM_RULES = new CopyOnWriteArrayList<>();

    private static volatile boolean initialized;
    private static volatile VKSecurityConfig config = new VKSecurityConfig();
    private static volatile VKSqlSecurityScanner scanner;
    private static volatile VKKeyStoreConfig keyStoreConfig = new VKKeyStoreConfig();
    private static volatile VKKeyStore keyStore;

    protected VostokSecurity() {
    }

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

    public static VKSqlCheckResult checkSql(String sql) {
        return current().checkSql(sql, new Object[0]);
    }

    public static VKSqlCheckResult checkSql(String sql, Object... params) {
        return current().checkSql(sql, params);
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

    public static VKSecurityCheckResult checkXss(String input) {
        return VKXssScanner.check(input);
    }

    public static void assertSafeXss(String input) {
        assertSafe("Unsafe XSS payload detected", checkXss(input));
    }

    public static VKSecurityCheckResult checkCommandInjection(String input) {
        return VKCommandInjectionScanner.check(input);
    }

    public static void assertSafeCommand(String input) {
        assertSafe("Unsafe command payload detected", checkCommandInjection(input));
    }

    public static VKSecurityCheckResult checkPathTraversal(String inputPath) {
        return VKPathTraversalScanner.check(inputPath);
    }

    public static void assertSafePath(String inputPath) {
        assertSafe("Unsafe path traversal payload detected", checkPathTraversal(inputPath));
    }

    public static VKSecurityCheckResult checkSensitiveResponse(String payload) {
        return VKResponseSecurityScanner.check(payload);
    }

    public static String maskSensitiveResponse(String payload) {
        return VKResponseSecurityScanner.mask(payload);
    }

    public static VKFileType detectFileType(byte[] content) {
        return VKFileSecurityScanner.detectType(content);
    }

    public static VKSecurityCheckResult checkFileMagic(byte[] content, VKFileType... allowed) {
        return VKFileSecurityScanner.checkMagicAllowed(content, allowed);
    }

    public static VKSecurityCheckResult checkExecutableScriptUpload(String fileName, byte[] content) {
        return VKFileSecurityScanner.checkExecutableScriptUpload(fileName, content);
    }

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

    public static String encryptWithKeyId(String plainText, String keyId) {
        String key = getOrCreateAesKey(keyId);
        String cipher = encrypt(plainText, key);
        return "vk1:aes:" + keyId + ":" + cipher;
    }

    public static String decryptWithKeyId(String cipherPayload) {
        if (cipherPayload == null || cipherPayload.isBlank()) {
            throw new VKSecurityException("Cipher payload is blank");
        }
        String[] parts = cipherPayload.split(":", 4);
        if (parts.length != 4 || !"vk1".equals(parts[0]) || !"aes".equals(parts[1])) {
            throw new VKSecurityException("Cipher payload format invalid");
        }
        String keyId = parts[2];
        String cipher = parts[3];
        String key = getOrCreateAesKey(keyId);
        return decrypt(cipher, key);
    }

    private static void assertSafe(String message, VKSecurityCheckResult result) {
        if (!result.isSafe()) {
            throw new VKSecurityException(message + ": " + String.join("; ", result.getReasons()));
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
