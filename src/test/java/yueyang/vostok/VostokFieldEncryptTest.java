package yueyang.vostok;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.security.VostokSecurity;
import yueyang.vostok.security.crypto.VKAesCrypto;
import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.field.*;
import yueyang.vostok.security.keystore.VKKeyStoreConfig;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库字段级加解密（vkf3）集成测试。
 *
 * <p>覆盖场景：基础加解密、Session API、类型安全、自描述解密、DEK 轮换、
 * reEncrypt、Blind Index、认证标签校验、并发、缓存失效、配置项。
 */
public class VostokFieldEncryptTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 每个测试用独立的临时目录，确保 keyStore 隔离
        VKKeyStoreConfig ksConfig = new VKKeyStoreConfig()
                .baseDir(tempDir.toString())
                .masterKey(VKAesCrypto.generateAesKeyBase64());
        VostokSecurity.initKeyStore(ksConfig);
        VostokSecurity.configureFieldEncrypt(new VKFieldEncryptConfig());
    }

    @AfterEach
    void tearDown() {
        VostokSecurity.invalidateAllDekCache();
        VostokSecurity.close();
    }

    // ================================================================ 基础加解密

    @Test
    void testEncryptDecryptString() {
        String plain = "hello world";
        String cipher = VostokSecurity.encryptField(plain, "users");
        assertNotNull(cipher);
        assertNotEquals(plain, cipher);
        String decrypted = VostokSecurity.decryptField(cipher);
        assertEquals(plain, decrypted);
    }

    @Test
    void testEncryptDecryptNullPassthrough() {
        // NULL_PASSTHROUGH：null 直接返回 null
        assertNull(VostokSecurity.encryptField(null, "users"));
        assertNull(VostokSecurity.decryptField(null));
    }

    @Test
    void testRejectNullPolicy() {
        VostokSecurity.configureFieldEncrypt(new VKFieldEncryptConfig()
                .nullPolicy(VKNullPolicy.REJECT));
        try (VKFieldSession session = VostokSecurity.fieldSession("orders")) {
            assertThrows(VKSecurityException.class, () -> session.encrypt(null));
            assertThrows(VKSecurityException.class, () -> session.blindIndex(null));
        }
    }

    @Test
    void testEncryptProducesDifferentCiphersEachTime() {
        // AES-GCM 每次随机 IV，相同明文产生不同密文
        String plain = "same input";
        String c1 = VostokSecurity.encryptField(plain, "users");
        String c2 = VostokSecurity.encryptField(plain, "users");
        assertNotEquals(c1, c2, "Same plaintext must produce different ciphertext due to random IV");
        // 但两者都能解密出原文
        assertEquals(plain, VostokSecurity.decryptField(c1));
        assertEquals(plain, VostokSecurity.decryptField(c2));
    }

    // ================================================================ Session API

    @Test
    void testFieldSessionEncryptDecrypt() {
        String plain = "sensitive email";
        try (VKFieldSession session = VostokSecurity.fieldSession("users")) {
            String cipher = session.encrypt(plain);
            assertNotNull(cipher);
            String decrypted = session.decrypt(cipher);
            assertEquals(plain, decrypted);
        }
    }

    @Test
    void testFieldSessionBlindIndex() {
        try (VKFieldSession session = VostokSecurity.fieldSession("users")) {
            String blind1 = session.blindIndex("alice@example.com");
            String blind2 = session.blindIndex("alice@example.com");
            String blind3 = session.blindIndex("bob@example.com");
            // 相同输入 → 相同 blind index（确定性）
            assertEquals(blind1, blind2);
            // 不同输入 → 不同 blind index
            assertNotEquals(blind1, blind3);
            // 长度为 64 字符（HMAC-SHA256 十六进制）
            assertEquals(64, blind1.length());
        }
    }

    @Test
    void testFieldSessionClosedThrows() {
        VKFieldSession session = VostokSecurity.fieldSession("users");
        session.close();
        assertThrows(VKSecurityException.class, () -> session.encrypt("x"),
                "Calling encrypt after close should throw");
        assertThrows(VKSecurityException.class, () -> session.decrypt("x"),
                "Calling decrypt after close should throw");
        assertThrows(VKSecurityException.class, () -> session.blindIndex("x"),
                "Calling blindIndex after close should throw");
    }

    @Test
    void testFieldSessionGetColumnKeyId() {
        try (VKFieldSession session = VostokSecurity.fieldSession("products")) {
            assertEquals("products", session.getColumnKeyId());
        }
    }

    // ================================================================ 类型安全加解密

    @Test
    void testEncryptTypedAllTypes() {
        VostokSecurity.registerColumnKey("typed-table");

        // STRING
        String strCipher = VostokSecurity.encryptTyped("hello", "typed-table", VKFieldType.STRING);
        assertEquals("hello", VostokSecurity.decryptTyped(strCipher, VKFieldType.STRING));

        // INTEGER
        String intCipher = VostokSecurity.encryptTyped(42, "typed-table", VKFieldType.INTEGER);
        assertEquals(42, VostokSecurity.decryptTyped(intCipher, VKFieldType.INTEGER));

        // LONG
        String longCipher = VostokSecurity.encryptTyped(9876543210L, "typed-table", VKFieldType.LONG);
        assertEquals(9876543210L, VostokSecurity.decryptTyped(longCipher, VKFieldType.LONG));

        // DOUBLE
        String dblCipher = VostokSecurity.encryptTyped(3.14159, "typed-table", VKFieldType.DOUBLE);
        assertEquals(3.14159, (Double) VostokSecurity.decryptTyped(dblCipher, VKFieldType.DOUBLE), 1e-10);

        // BIG_DECIMAL
        BigDecimal bd = new BigDecimal("12345.6789");
        String bdCipher = VostokSecurity.encryptTyped(bd, "typed-table", VKFieldType.BIG_DECIMAL);
        assertEquals(bd, VostokSecurity.decryptTyped(bdCipher, VKFieldType.BIG_DECIMAL));

        // LOCAL_DATE
        LocalDate date = LocalDate.of(2024, 6, 15);
        String dateCipher = VostokSecurity.encryptTyped(date, "typed-table", VKFieldType.LOCAL_DATE);
        assertEquals(date, VostokSecurity.decryptTyped(dateCipher, VKFieldType.LOCAL_DATE));

        // LOCAL_DATE_TIME
        LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        String dtCipher = VostokSecurity.encryptTyped(dt, "typed-table", VKFieldType.LOCAL_DATE_TIME);
        assertEquals(dt, VostokSecurity.decryptTyped(dtCipher, VKFieldType.LOCAL_DATE_TIME));

        // BOOLEAN
        String boolCipher = VostokSecurity.encryptTyped(true, "typed-table", VKFieldType.BOOLEAN);
        assertEquals(true, VostokSecurity.decryptTyped(boolCipher, VKFieldType.BOOLEAN));
        String boolCipher2 = VostokSecurity.encryptTyped(false, "typed-table", VKFieldType.BOOLEAN);
        assertEquals(false, VostokSecurity.decryptTyped(boolCipher2, VKFieldType.BOOLEAN));

        // BYTES
        byte[] bytes = new byte[]{0x00, (byte) 0xFF, 0x42, (byte) 0x80};
        String bytesCipher = VostokSecurity.encryptTyped(bytes, "typed-table", VKFieldType.BYTES);
        assertArrayEquals(bytes, (byte[]) VostokSecurity.decryptTyped(bytesCipher, VKFieldType.BYTES));
    }

    @Test
    void testEncryptTypedNullReturnsNull() {
        VostokSecurity.registerColumnKey("typed-table");
        assertNull(VostokSecurity.encryptTyped(null, "typed-table", VKFieldType.STRING));
        assertNull(VostokSecurity.decryptTyped(null, VKFieldType.STRING));
    }

    // ================================================================ 自描述解密

    @Test
    void testDecryptFieldSelfDescribing() {
        // 加密时注册 columnKeyId，解密时通过 keyIdHash 自动定位
        String cipher = VostokSecurity.encryptField("secret", "accounts");
        // decryptField 不需要传入 columnKeyId
        String plain = VostokSecurity.decryptField(cipher);
        assertEquals("secret", plain);
    }

    @Test
    void testDecryptFieldUnknownHashThrows() {
        // 创建密文（columnKeyId 注册到 hashRegistry）
        String cipher = VostokSecurity.encryptField("value", "knownTable");
        // 清除 hashRegistry（使 keyIdHash 变成未知）
        VostokSecurity.invalidateAllDekCache();
        // 此时 decryptField 找不到 columnKeyId，应抛异常
        assertThrows(VKSecurityException.class, () -> VostokSecurity.decryptField(cipher));
    }

    @Test
    void testDecryptFieldCrossTableThrows() {
        // 用 tableA 加密
        try (VKFieldSession sessionA = VostokSecurity.fieldSession("tableA")) {
            String cipherA = sessionA.encrypt("value-a");
            // 用 tableB 的 session 解密 tableA 的密文 → 跨表防护应抛异常
            try (VKFieldSession sessionB = VostokSecurity.fieldSession("tableB")) {
                assertThrows(VKSecurityException.class, () -> sessionB.decrypt(cipherA),
                        "Cross-table decrypt should be rejected");
            }
        }
    }

    // ================================================================ DEK 轮换

    @Test
    void testRotateDekOldCipherStillDecryptable() {
        String plain = "old data";
        // 用 v1 DEK 加密
        String oldCipher = VostokSecurity.encryptField(plain, "records");
        // 轮换 DEK → v2
        VostokSecurity.rotateDek("records");
        // 旧密文仍然可以解密（使用 v1 DEK）
        String decrypted = VostokSecurity.decryptField(oldCipher);
        assertEquals(plain, decrypted);
    }

    @Test
    void testRotateDekNewCipherUsesNewVersion() {
        // 创建 v1 DEK
        VostokSecurity.encryptField("init", "records");
        // 轮换 → v2
        VostokSecurity.rotateDek("records");
        // 新密文使用 v2 DEK
        String newCipher = VostokSecurity.encryptField("new data", "records");
        // 解析 dekVersion：bytes[5..8]
        byte[] raw = Base64.getDecoder().decode(newCipher);
        int dekVersion = VKFieldCrypto.parseDekVersion(raw);
        assertEquals(2, dekVersion, "After rotateDek, new cipher should use DEK version 2");
        // 仍然可以解密
        assertEquals("new data", VostokSecurity.decryptField(newCipher));
    }

    // ================================================================ reEncrypt

    @Test
    void testReEncryptFieldsAfterRotate() {
        // 加密 3 条记录（v1 DEK）
        List<String> originals = List.of("alice", "bob", "carol");
        List<String> oldCiphers = new ArrayList<>();
        for (String s : originals) {
            oldCiphers.add(VostokSecurity.encryptField(s, "users"));
        }
        // 验证都用 v1
        for (String c : oldCiphers) {
            byte[] raw = Base64.getDecoder().decode(c);
            assertEquals(1, VKFieldCrypto.parseDekVersion(raw));
        }
        // 轮换 DEK
        VostokSecurity.rotateDek("users");
        // 批量重加密
        List<String> newCiphers = VostokSecurity.reEncryptFields(oldCiphers, "users");
        assertEquals(3, newCiphers.size());
        // 新密文都用 v2
        for (String c : newCiphers) {
            byte[] raw = Base64.getDecoder().decode(c);
            assertEquals(2, VKFieldCrypto.parseDekVersion(raw));
        }
        // 新密文可以正常解密
        for (int i = 0; i < originals.size(); i++) {
            assertEquals(originals.get(i), VostokSecurity.decryptField(newCiphers.get(i)));
        }
    }

    @Test
    void testReEncryptFieldsFailFast() {
        VostokSecurity.encryptField("init", "users");
        List<String> mixed = new ArrayList<>();
        mixed.add(VostokSecurity.encryptField("valid", "users"));
        mixed.add("not-valid-base64!!!"); // 非法密文
        mixed.add(VostokSecurity.encryptField("another", "users"));
        // 第二个元素非法，fail-fast 应抛异常
        assertThrows(VKSecurityException.class,
                () -> VostokSecurity.reEncryptFields(mixed, "users"));
    }

    @Test
    void testReEncryptNullElementPreserved() {
        VostokSecurity.encryptField("init", "users");
        List<String> withNull = new ArrayList<>();
        withNull.add(VostokSecurity.encryptField("value", "users"));
        withNull.add(null);
        withNull.add(VostokSecurity.encryptField("value2", "users"));
        List<String> result = VostokSecurity.reEncryptFields(withNull, "users");
        assertEquals(3, result.size());
        assertNull(result.get(1), "null element should be preserved as null");
        assertEquals("value", VostokSecurity.decryptField(result.get(0)));
        assertEquals("value2", VostokSecurity.decryptField(result.get(2)));
    }

    // ================================================================ Blind Index

    @Test
    void testBlindIndexDeterministic() {
        String b1 = VostokSecurity.blindIndex("john@example.com", "users");
        String b2 = VostokSecurity.blindIndex("john@example.com", "users");
        assertEquals(b1, b2, "Blind index must be deterministic");
        assertEquals(64, b1.length(), "HMAC-SHA256 hex must be 64 chars");
    }

    @Test
    void testBlindIndexNullHandling() {
        // 默认 NULL_PASSTHROUGH：null 返回 null
        assertNull(VostokSecurity.blindIndex(null, "users"));
    }

    @Test
    void testBlindIndexDifferentForDifferentInputs() {
        String b1 = VostokSecurity.blindIndex("alice", "users");
        String b2 = VostokSecurity.blindIndex("bob", "users");
        assertNotEquals(b1, b2);
    }

    @Test
    void testBlindIndexDifferentForDifferentTables() {
        // 不同表有不同 Blind Key，相同输入产生不同 index
        String b1 = VostokSecurity.blindIndex("alice", "users");
        String b2 = VostokSecurity.blindIndex("alice", "accounts");
        assertNotEquals(b1, b2,
                "Different tables must use different Blind Keys");
    }

    // ================================================================ 认证标签校验

    @Test
    void testTamperedCipherThrows() {
        String cipher = VostokSecurity.encryptField("important data", "users");
        byte[] raw = Base64.getDecoder().decode(cipher);
        // 篡改密文最后一个字节（认证标签区域）
        raw[raw.length - 1] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(raw);
        // GCM 认证标签验证失败，应抛异常
        assertThrows(VKSecurityException.class,
                () -> VostokSecurity.decryptField(tampered),
                "Tampered ciphertext should fail GCM authentication");
    }

    @Test
    void testTamperedVersionByteFails() {
        String cipher = VostokSecurity.encryptField("data", "users");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[0] = 0x01; // 改成错误版本号
        String bad = Base64.getEncoder().encodeToString(raw);
        assertThrows(VKSecurityException.class,
                () -> VostokSecurity.decryptField(bad),
                "Wrong version byte should be rejected");
    }

    // ================================================================ 并发测试

    @Test
    void testConcurrentEncryptDecrypt() throws InterruptedException {
        int threadCount = 20;
        int opsPerThread = 50;
        VostokSecurity.registerColumnKey("concurrent-table");

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        String plain = "thread-" + threadId + "-op-" + i;
                        String cipher = VostokSecurity.encryptField(plain, "concurrent-table");
                        String decrypted = VostokSecurity.decryptField(cipher);
                        if (!plain.equals(decrypted)) {
                            firstError.compareAndSet(null,
                                    new AssertionError("Decryption mismatch: expected=" + plain + " got=" + decrypted));
                            return;
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    firstError.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertNull(firstError.get(), "No errors expected in concurrent test, but got: " +
                (firstError.get() != null ? firstError.get().getMessage() : ""));
        assertEquals(threadCount * opsPerThread, successCount.get(),
                "All operations should succeed");
    }

    @Test
    void testConcurrentBlindIndex() throws InterruptedException {
        int threadCount = 10;
        VostokSecurity.registerColumnKey("blind-table");
        // 预先计算基准 blind index
        String expected = VostokSecurity.blindIndex("test-value", "blind-table");

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicReference<Throwable> firstError = new AtomicReference<>(null);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        String blind = VostokSecurity.blindIndex("test-value", "blind-table");
                        if (!expected.equals(blind)) {
                            firstError.compareAndSet(null,
                                    new AssertionError("Blind index not deterministic under concurrency"));
                            return;
                        }
                    }
                } catch (Throwable e) {
                    firstError.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertNull(firstError.get(), "Concurrent blind index should be stable");
    }

    // ================================================================ 缓存失效

    @Test
    void testInvalidateDekCache() {
        String plain = "sensitive";
        String cipher = VostokSecurity.encryptField(plain, "users");
        // 失效后重新加载仍可解密
        VostokSecurity.invalidateDekCache("users");
        // 需要重新注册（invalidate 会清除 hashRegistry）
        VostokSecurity.registerColumnKey("users");
        assertEquals(plain, VostokSecurity.decryptField(cipher));
    }

    @Test
    void testInvalidateAllDekCache() {
        String c1 = VostokSecurity.encryptField("a", "tableX");
        String c2 = VostokSecurity.encryptField("b", "tableY");
        VostokSecurity.invalidateAllDekCache();
        // 重新注册后仍可解密
        VostokSecurity.registerColumnKey("tableX");
        VostokSecurity.registerColumnKey("tableY");
        assertEquals("a", VostokSecurity.decryptField(c1));
        assertEquals("b", VostokSecurity.decryptField(c2));
    }

    // ================================================================ 配置测试

    @Test
    void testCacheTtlZeroDisablesCache() {
        // TTL=0 禁用缓存，每次访问都从 keyStore 加载（测试可正常工作即可）
        VostokSecurity.configureFieldEncrypt(new VKFieldEncryptConfig().dekCacheTtlSeconds(0));
        String cipher = VostokSecurity.encryptField("value", "no-cache-table");
        assertNotNull(cipher);
        assertEquals("value", VostokSecurity.decryptField(cipher));
        // 多次调用（每次都从 keyStore 加载）应一致
        assertEquals("value", VostokSecurity.decryptField(cipher));
    }

    @Test
    void testBlindKeyIdSuffix() {
        // 自定义 blindSuffix
        VostokSecurity.configureFieldEncrypt(
                new VKFieldEncryptConfig().blindKeyIdSuffix(".myblind"));
        // 用自定义 suffix 计算 blind index
        String b1 = VostokSecurity.blindIndex("alice", "custom-suffix-table");
        String b2 = VostokSecurity.blindIndex("alice", "custom-suffix-table");
        assertEquals(b1, b2, "Blind index must be deterministic with custom suffix");
        assertEquals(64, b1.length());

        // 切换回默认 suffix：不同 suffix 对应不同文件，blind index 应不同
        VostokSecurity.configureFieldEncrypt(
                new VKFieldEncryptConfig().blindKeyIdSuffix(".blind"));
        String bDefault = VostokSecurity.blindIndex("alice", "custom-suffix-table");
        assertNotEquals(b1, bDefault,
                "Different blind suffix should produce different blind index");
    }

    // ================================================================ Session reEncrypt

    @Test
    void testSessionReEncrypt() {
        try (VKFieldSession session = VostokSecurity.fieldSession("items")) {
            String oldCipher = session.encrypt("item data");
            // reEncrypt：用当前 DEK 解密再加密（初始版本相同，但 IV 随机，新密文不同）
            String newCipher = session.reEncrypt(oldCipher);
            assertNotEquals(oldCipher, newCipher,
                    "reEncrypt should produce different ciphertext (random IV)");
            // 新密文仍然解密正确
            assertEquals("item data", session.decrypt(newCipher));
        }
    }

    @Test
    void testSessionReEncryptBytes() {
        // 测试二进制内容 reEncrypt（通过 encryptTyped BYTES）
        VostokSecurity.registerColumnKey("binary-table");
        byte[] original = new byte[]{0x00, (byte) 0xFF, 0x42, (byte) 0x80, 0x01};
        String cipher = VostokSecurity.encryptTyped(original, "binary-table", VKFieldType.BYTES);
        VostokSecurity.rotateDek("binary-table");

        List<String> reEncrypted = VostokSecurity.reEncryptFields(List.of(cipher), "binary-table");
        assertEquals(1, reEncrypted.size());
        byte[] result = (byte[]) VostokSecurity.decryptTyped(reEncrypted.get(0), VKFieldType.BYTES);
        assertArrayEquals(original, result, "Binary data must survive reEncrypt without corruption");
    }

    // ================================================================ vkf3 格式校验

    @Test
    void testVkf3FormatVersion() {
        String cipher = VostokSecurity.encryptField("test", "users");
        byte[] raw = Base64.getDecoder().decode(cipher);
        // byte[0] = 0x03
        assertEquals(0x03, raw[0] & 0xFF, "vkf3 version byte must be 0x03");
        // 最小长度 37 字节
        assertTrue(raw.length >= VKFieldCrypto.MIN_CIPHER_BYTES);
    }

    @Test
    void testVkf3FormatKeyIdHash() {
        String columnKeyId = "my-table";
        int expectedHash = VKFieldCrypto.computeKeyIdHash(columnKeyId);
        String cipher = VostokSecurity.encryptField("data", columnKeyId);
        byte[] raw = Base64.getDecoder().decode(cipher);
        int parsedHash = VKFieldCrypto.parseKeyIdHash(raw);
        assertEquals(expectedHash, parsedHash, "keyIdHash in cipher must match computeKeyIdHash");
    }

    // ================================================================ 多 DEK 版本兼容解密

    @Test
    void testMultipleDekVersionsAllDecryptable() {
        List<String> ciphers = new ArrayList<>();
        List<String> plains = new ArrayList<>();
        // v1
        plains.add("v1-data");
        ciphers.add(VostokSecurity.encryptField("v1-data", "multi"));
        // 轮换到 v2
        VostokSecurity.rotateDek("multi");
        plains.add("v2-data");
        ciphers.add(VostokSecurity.encryptField("v2-data", "multi"));
        // 轮换到 v3
        VostokSecurity.rotateDek("multi");
        plains.add("v3-data");
        ciphers.add(VostokSecurity.encryptField("v3-data", "multi"));

        // 全部都能解密
        for (int i = 0; i < ciphers.size(); i++) {
            assertEquals(plains.get(i), VostokSecurity.decryptField(ciphers.get(i)),
                    "DEK version " + (i + 1) + " cipher must be decryptable after rotation");
        }
    }

    // ================================================================ 空字符串加解密

    @Test
    void testEncryptDecryptEmptyString() {
        String plain = "";
        String cipher = VostokSecurity.encryptField(plain, "users");
        assertNotNull(cipher);
        assertEquals(plain, VostokSecurity.decryptField(cipher));
    }

    // ================================================================ 大数据量加解密

    @Test
    void testEncryptDecryptLongString() {
        String plain = "A".repeat(10000);
        String cipher = VostokSecurity.encryptField(plain, "users");
        assertEquals(plain, VostokSecurity.decryptField(cipher));
    }
}
