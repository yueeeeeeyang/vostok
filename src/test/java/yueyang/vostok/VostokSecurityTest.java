package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yueyang.vostok.security.VKSecurityConfig;
import yueyang.vostok.security.VKSecurityRiskLevel;
import yueyang.vostok.security.crypto.VKRsaKeyPair;
import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.file.VKFileType;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;
import yueyang.vostok.security.rule.VKSecurityContext;
import yueyang.vostok.security.rule.VKSecurityFinding;
import yueyang.vostok.security.rule.VKSecurityRule;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class VostokSecurityTest {
    @AfterEach
    void tearDown() {
        Vostok.Security.close();
        Vostok.Security.clearCustomRules();
    }

    @Test
    void testSafeSqlByDefault() {
        var result = Vostok.Security.checkSql("SELECT id, user_name FROM t_user WHERE id = ?", 1L);
        assertTrue(result.isSafe());
        assertEquals(VKSecurityRiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getReasons().isEmpty());
    }

    @Test
    void testDetectInjectionPattern() {
        var result = Vostok.Security.checkSql("SELECT * FROM t_user WHERE name = '' OR 1=1 --");
        assertFalse(result.isSafe());
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
        assertFalse(result.getMatchedRules().isEmpty());
    }

    @Test
    void testRiskFunctionPattern() {
        var result = Vostok.Security.checkSql("SELECT pg_sleep(3)");
        assertFalse(result.isSafe());
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
    }

    @Test
    void testMultiStatementPolicy() {
        var blocked = Vostok.Security.checkSql("SELECT 1; DROP TABLE t_user");
        assertFalse(blocked.isSafe());

        Vostok.Security.reinit(new VKSecurityConfig().allowMultiStatement(true));
        var allowed = Vostok.Security.checkSql("SELECT 1; ");
        assertTrue(allowed.isSafe());
    }

    @Test
    void testPlaceholderMismatch() {
        var result = Vostok.Security.checkSql("SELECT * FROM t_user WHERE id = ? AND age = ?", 1);
        assertFalse(result.isSafe());
        assertTrue(result.getMatchedRules().contains("sql-placeholder-arity"));
    }

    @Test
    void testAssertUnsafeSqlThrows() {
        assertThrows(VKSecurityException.class,
                () -> Vostok.Security.assertSafeSql("SELECT * FROM t_user WHERE id = 1 OR 1=1"));
    }

    @Test
    void testCustomRule() {
        Vostok.Security.registerRule(new VKSecurityRule() {
            @Override
            public String name() {
                return "custom-forbid-user-table";
            }

            @Override
            public VKSecurityFinding apply(VKSecurityContext context) {
                if (context.getScannedSql().contains("t_user")) {
                    return new VKSecurityFinding(name(), VKSecurityRiskLevel.MEDIUM, 6,
                            "custom rule blocked t_user table");
                }
                return null;
            }
        });

        var result = Vostok.Security.checkSql("SELECT * FROM t_user WHERE id = ?", 1L);
        assertFalse(result.isSafe());
        assertTrue(result.getMatchedRules().contains("custom-forbid-user-table"));
    }

    @Test
    void testXssDetection() {
        var bad = Vostok.Security.checkXss("<script>alert('x')</script>");
        assertFalse(bad.isSafe());
        assertEquals(VKSecurityRiskLevel.HIGH, bad.getRiskLevel());

        var ok = Vostok.Security.checkXss("hello world");
        assertTrue(ok.isSafe());
    }

    @Test
    void testCommandInjectionDetection() {
        var bad = Vostok.Security.checkCommandInjection("ls; rm -rf /");
        assertFalse(bad.isSafe());

        var ok = Vostok.Security.checkCommandInjection("ls -la /tmp");
        assertTrue(ok.isSafe());
    }

    @Test
    void testPathTraversalDetection() {
        var bad = Vostok.Security.checkPathTraversal("../../etc/passwd");
        assertFalse(bad.isSafe());

        var encodedBad = Vostok.Security.checkPathTraversal("..%2f..%2fsecret.txt");
        assertFalse(encodedBad.isSafe());

        var ok = Vostok.Security.checkPathTraversal("uploads/avatar.png");
        assertTrue(ok.isSafe());
    }

    @Test
    void testSensitiveResponseCheckAndMask() {
        String payload = "{\"phone\":\"13800138000\",\"email\":\"a@b.com\"}";
        var check = Vostok.Security.checkSensitiveResponse(payload);
        assertFalse(check.isSafe());

        String masked = Vostok.Security.maskSensitiveResponse(payload);
        assertTrue(masked.contains("138****8000"));
        assertTrue(masked.contains("***@***"));
    }

    @Test
    void testFileMagicDetection() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00};
        assertEquals(VKFileType.PNG, Vostok.Security.detectFileType(png));

        var allowed = Vostok.Security.checkFileMagic(png, VKFileType.PNG, VKFileType.JPEG);
        assertTrue(allowed.isSafe());

        var blocked = Vostok.Security.checkFileMagic(png, VKFileType.PDF);
        assertFalse(blocked.isSafe());
    }

    @Test
    void testExecutableScriptUploadDetection() {
        byte[] sh = "#!/bin/bash\necho hi".getBytes(StandardCharsets.UTF_8);
        var badByExt = Vostok.Security.checkExecutableScriptUpload("run.sh", "echo hi".getBytes(StandardCharsets.UTF_8));
        assertFalse(badByExt.isSafe());

        var badByMagic = Vostok.Security.checkExecutableScriptUpload("note.txt", sh);
        assertFalse(badByMagic.isSafe());

        var ok = Vostok.Security.checkExecutableScriptUpload("photo.png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        assertTrue(ok.isSafe());
    }

    @Test
    void testAesEncryptDecrypt() {
        String key = Vostok.Security.generateAesKey();
        String cipher = Vostok.Security.encrypt("hello-aes", key);
        String plain = Vostok.Security.decrypt(cipher, key);
        assertEquals("hello-aes", plain);
    }

    @Test
    void testAesEncryptDecryptByPassphrase() {
        String cipher = Vostok.Security.encrypt("hello-passphrase", "demo-secret");
        String plain = Vostok.Security.decrypt(cipher, "demo-secret");
        assertEquals("hello-passphrase", plain);
    }

    @Test
    void testAesDecryptWithWrongSecretThrows() {
        String cipher = Vostok.Security.encrypt("hello", "secret-a");
        assertThrows(VKSecurityException.class, () -> Vostok.Security.decrypt(cipher, "secret-b"));
    }

    @Test
    void testRsaEncryptDecrypt() {
        VKRsaKeyPair pair = Vostok.Security.generateRsaKeyPair();
        String cipher = Vostok.Security.encryptByPublicKey("hello-rsa", pair.getPublicKeyPem());
        String plain = Vostok.Security.decryptByPrivateKey(cipher, pair.getPrivateKeyPem());
        assertEquals("hello-rsa", plain);
    }

    @Test
    void testRsaSignVerify() {
        VKRsaKeyPair pair = Vostok.Security.generateRsaKeyPair();
        String data = "{\"id\":1,\"name\":\"tom\"}";
        String sign = Vostok.Security.sign(data, pair.getPrivateKeyPem());
        assertTrue(Vostok.Security.verify(data, sign, pair.getPublicKeyPem()));
        assertFalse(Vostok.Security.verify(data + "-tampered", sign, pair.getPublicKeyPem()));
    }

    @Test
    void testHashAndHmac() {
        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", Vostok.Security.sha256("abc"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Vostok.Security.sha256Hex("abc"));

        String hmac1 = Vostok.Security.hmacSha256("hello", "key");
        String hmac2 = Vostok.Security.hmacSha256("hello", "key");
        String hmac3 = Vostok.Security.hmacSha256("hello2", "key");
        assertEquals(hmac1, hmac2);
        assertNotEquals(hmac1, hmac3);
    }

    @Test
    void testKeyStoreGetOrCreateAesKeyPersistence() throws Exception {
        var dir = Files.createTempDirectory("vostok-keystore-aes");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-123");
        Vostok.Security.initKeyStore(cfg);

        String k1 = Vostok.Security.getOrCreateAesKey("order-data");
        String k2 = Vostok.Security.getOrCreateAesKey("order-data");
        assertEquals(k1, k2);

        Vostok.Security.close();
        Vostok.Security.initKeyStore(cfg);
        String k3 = Vostok.Security.getOrCreateAesKey("order-data");
        assertEquals(k1, k3);
    }

    @Test
    void testKeyStoreGetOrCreateRsaKeyPairPersistence() throws Exception {
        var dir = Files.createTempDirectory("vostok-keystore-rsa");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-rsa");
        Vostok.Security.initKeyStore(cfg);

        VKRsaKeyPair p1 = Vostok.Security.getOrCreateRsaKeyPair("order-rsa");
        VKRsaKeyPair p2 = Vostok.Security.getOrCreateRsaKeyPair("order-rsa");
        assertEquals(p1.getPublicKeyPem(), p2.getPublicKeyPem());
        assertEquals(p1.getPrivateKeyPem(), p2.getPrivateKeyPem());
    }

    @Test
    void testEncryptDecryptWithKeyIdAcrossRestart() throws Exception {
        var dir = Files.createTempDirectory("vostok-keystore-restart");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-restart");
        Vostok.Security.initKeyStore(cfg);

        String payload = Vostok.Security.encryptWithKeyId("hello-persist", "biz-aes");
        String plain1 = Vostok.Security.decryptWithKeyId(payload);
        assertEquals("hello-persist", plain1);

        Vostok.Security.close();
        Vostok.Security.initKeyStore(cfg);
        String plain2 = Vostok.Security.decryptWithKeyId(payload);
        assertEquals("hello-persist", plain2);
    }

    @Test
    void testDecryptWithWrongMasterKeyFails() throws Exception {
        var dir = Files.createTempDirectory("vostok-keystore-master");
        VKKeyStoreConfig cfg1 = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-ok");
        Vostok.Security.initKeyStore(cfg1);
        String payload = Vostok.Security.encryptWithKeyId("hello-master", "master-key");

        VKKeyStoreConfig cfg2 = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-wrong");
        Vostok.Security.initKeyStore(cfg2);

        assertThrows(VKSecurityException.class, () -> Vostok.Security.decryptWithKeyId(payload));
    }

    @Test
    void testRotateAesKey() throws Exception {
        var dir = Files.createTempDirectory("vostok-keystore-rotate");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-rotate");
        Vostok.Security.initKeyStore(cfg);

        String oldKey = Vostok.Security.getOrCreateAesKey("rotate-aes");
        Vostok.Security.rotateAesKey("rotate-aes");
        String newKey = Vostok.Security.getOrCreateAesKey("rotate-aes");
        assertNotEquals(oldKey, newKey);
    }

    @Test
    void testRotateRsaKeyPair() throws Exception {
        var dir = Files.createTempDirectory("vostok-keystore-rotate-rsa");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-rotate-rsa");
        Vostok.Security.initKeyStore(cfg);

        VKRsaKeyPair oldPair = Vostok.Security.getOrCreateRsaKeyPair("rotate-rsa");
        Vostok.Security.rotateRsaKeyPair("rotate-rsa");
        VKRsaKeyPair newPair = Vostok.Security.getOrCreateRsaKeyPair("rotate-rsa");

        assertNotEquals(oldPair.getPublicKeyPem(), newPair.getPublicKeyPem());
        assertNotEquals(oldPair.getPrivateKeyPem(), newPair.getPrivateKeyPem());
    }

    @Test
    void testDecryptWithKeyIdPayloadFormatValidation() {
        assertThrows(VKSecurityException.class, () -> Vostok.Security.decryptWithKeyId("bad-payload"));
    }
}
