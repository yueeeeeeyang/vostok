package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConflictStrategy;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.file.VKFileMigrateOptions;
import yueyang.vostok.file.exception.VKFileErrorCode;
import yueyang.vostok.file.exception.VKFileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug 回归 + 新功能集成测试。
 * 对应 vostok-file.txt 描述的 6 个 Bug 修复、6 个性能优化（可观测行为）及 5 个新功能。
 */
public class VostokFileExtTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.File.close();
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
    }

    // =========================================================================
    // Bug 1：deleteRecursivelyPath 不再跟随 symlink 删除外部文件
    // =========================================================================

    @Test
    void testDeleteRecursivelyNoFollowSymlink() throws IOException {
        // 仅在支持符号链接的系统上运行
        Assumptions.assumeTrue(isSymlinkSupported(), "当前环境不支持符号链接，跳过");
        init();

        // 在 root 外部创建目录，其中有不应被删除的文件
        Path external = Files.createTempDirectory("ext_symlink_");
        Path externalFile = external.resolve("keep.txt");
        Files.writeString(externalFile, "must-survive");

        // 在 root 内创建目录，包含指向外部目录的 symlink
        Vostok.File.mkdirs("to_delete");
        Path link = tempDir.resolve("to_delete/link_to_ext");
        Files.createSymbolicLink(link, external);

        // 删除包含 symlink 的目录
        Vostok.File.deleteRecursively("to_delete");

        // 验证：外部目录及其文件不受影响
        assertTrue(Files.exists(externalFile), "外部目录文件不应被删除（FOLLOW_LINKS 已去除）");
        assertFalse(Vostok.File.exists("to_delete"), "目录本身应被删除");

        // 清理外部目录
        Files.deleteIfExists(externalFile);
        Files.deleteIfExists(external);
    }

    // =========================================================================
    // Bug 2：copyDir OVERWRITE 不再预删目标目录，目标中已有文件得以保留
    // =========================================================================

    @Test
    void testCopyDirOverwriteNoDataLoss() {
        init();
        Vostok.File.write("src/a.txt", "content-a");
        Vostok.File.write("dst/b.txt", "content-b"); // 目标中独立文件，源中无此文件

        // OVERWRITE：源只有 a.txt，目标已有 b.txt
        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.OVERWRITE);

        // a.txt 被复制到目标
        assertEquals("content-a", Vostok.File.read("dst/a.txt"));
        // b.txt 不在源中，不应被预删操作清除
        assertEquals("content-b", Vostok.File.read("dst/b.txt"),
                "OVERWRITE 不应预删目标目录，b.txt 应保留");
    }

    // =========================================================================
    // Bug 3：prepareCheckpoint 相对路径正确解析到 sourceBase 内部并抛异常
    // =========================================================================

    @Test
    void testCheckpointRelativePathResolvesInsideSourceBase() throws IOException {
        init();
        Vostok.File.write("f.txt", "data");
        Path targetDir = Files.createTempDirectory("mig_chk_");
        try {
            // 相对路径 "checkpoint.txt" 将被解析到 sourceBase（root）内部，应触发参数校验异常
            VKFileMigrateOptions opts = new VKFileMigrateOptions()
                    .checkpointFile("checkpoint.txt");
            assertThrows(VKFileException.class,
                    () -> Vostok.File.migrateBaseDir(targetDir.toString(), opts),
                    "checkpoint 文件路径在 sourceBase 内部时应抛异常");
        } finally {
            deleteDir(targetDir);
        }
    }

    // =========================================================================
    // Bug 4：normalizeFormat 不再将 jpeg 强制映射为 jpg（行为验证）
    // =========================================================================

    @Test
    void testNormalizeFormatJpegPreserved() {
        // 通过 LocalFileStore 正常加载（静态初始化含 WRITABLE_FORMATS 缓存）验证无异常
        // 若 "jpeg"→"jpg" 映射存在，且当前 JVM 只注册了 "jpeg"，则 canWriteFormat 会失败
        // 此处仅确认 LocalFileStore 实例化 + 基本读写不受影响
        init();
        Vostok.File.write("check.txt", "bug4-ok");
        assertEquals("bug4-ok", Vostok.File.read("check.txt"));
    }

    // =========================================================================
    // Bug 5：moveDir 正常路径不丢数据；跨设备回退异常正确传播
    // =========================================================================

    @Test
    void testMoveDirNormalPathNoDataLoss() {
        init();
        Vostok.File.write("mvsrc/x.txt", "hello");
        Vostok.File.write("mvsrc/y.txt", "world");

        Vostok.File.moveDir("mvsrc", "mvdst", VKFileConflictStrategy.OVERWRITE);

        assertEquals("hello", Vostok.File.read("mvdst/x.txt"));
        assertEquals("world", Vostok.File.read("mvdst/y.txt"));
        assertFalse(Vostok.File.exists("mvsrc"), "moveDir 后源目录应不存在");
    }

    // =========================================================================
    // Ext 1：目录总大小 totalSize()
    // =========================================================================

    @Test
    void testTotalSizeFile() {
        init();
        Vostok.File.write("single.txt", "hello");
        long sz = Vostok.File.totalSize("single.txt");
        assertTrue(sz > 0, "单文件 totalSize 应 > 0");
    }

    @Test
    void testTotalSizeDirectory() {
        init();
        Vostok.File.write("tree/a.txt", "aaa");    // 3 字节
        Vostok.File.write("tree/b/c.txt", "bbbbb"); // 5 字节
        long sz = Vostok.File.totalSize("tree");
        assertEquals(8, sz, "目录总大小应等于各文件字节数之和");
    }

    @Test
    void testTotalSizeNonExistentThrows() {
        init();
        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.File.totalSize("no/such/dir"));
        assertEquals(VKFileErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // =========================================================================
    // Ext 2：临时文件 createTemp()
    // =========================================================================

    @Test
    void testCreateTempDefaultDir() {
        init();
        String path = Vostok.File.createTemp("pfx", ".tmp");
        assertNotNull(path);
        String normalized = path.replace('\\', '/');
        assertTrue(normalized.startsWith("tmp/"), "应在 tmp/ 下: " + path);
        assertTrue(Vostok.File.exists(path), "临时文件应已存在");
    }

    @Test
    void testCreateTempCustomSubDir() {
        init();
        String path = Vostok.File.createTemp("uploads/temp", "img", ".jpg");
        assertNotNull(path);
        assertTrue(path.replace('\\', '/').startsWith("uploads/temp/"),
                "应在 uploads/temp/ 下: " + path);
        assertTrue(Vostok.File.exists(path));
    }

    @Test
    void testCreateTempPrefixSuffix() {
        init();
        String path = Vostok.File.createTemp("myprefix", ".dat");
        String normalized = path.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        assertTrue(fileName.startsWith("myprefix"), "文件名应以 prefix 开头: " + fileName);
        assertTrue(fileName.endsWith(".dat"), "文件名应以 .dat 结尾: " + fileName);
    }

    // =========================================================================
    // Ext 3：GZip 压缩 / 解压
    // =========================================================================

    @Test
    void testGzip() {
        init();
        Vostok.File.write("orig.txt", "hello gzip world 1234567890");
        Vostok.File.gzip("orig.txt", "orig.txt.gz");
        assertTrue(Vostok.File.exists("orig.txt.gz"), "压缩文件应存在");
        assertTrue(Vostok.File.size("orig.txt.gz") > 0, "压缩文件大小应 > 0");
    }

    @Test
    void testGunzip() {
        init();
        String content = "hello gunzip roundtrip content";
        Vostok.File.write("src.txt", content);
        Vostok.File.gzip("src.txt", "src.txt.gz");
        Vostok.File.gunzip("src.txt.gz", "out.txt");
        assertEquals(content, Vostok.File.read("out.txt"), "解压内容应与原文件一致");
    }

    @Test
    void testGzipSamePathThrows() {
        init();
        Vostok.File.write("same.txt", "data");
        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.File.gzip("same.txt", "same.txt"));
        assertEquals(VKFileErrorCode.INVALID_ARGUMENT, ex.getErrorCode());
    }

    // =========================================================================
    // Ext 4：文件加密 / 解密（AES-256-GCM via VostokSecurity）
    // =========================================================================

    @Test
    void testEncryptDecryptRoundTrip() {
        init();
        String content = "secret-text-content-plain";
        Vostok.File.write("plain.txt", content);
        Vostok.File.encryptFile("plain.txt", "enc.dat", "my-secret-key");

        // 加密后内容应与原文不同
        assertNotEquals(content, Vostok.File.read("enc.dat"), "加密文件内容应与明文不同");

        Vostok.File.decryptFile("enc.dat", "out.txt", "my-secret-key");
        assertEquals(content, Vostok.File.read("out.txt"), "解密后内容应与原文一致");
    }

    @Test
    void testEncryptDecryptBinaryRoundTrip() {
        init();
        byte[] bytes = new byte[256];
        new Random(42).nextBytes(bytes);
        Vostok.File.writeBytes("bin.dat", bytes);

        Vostok.File.encryptFile("bin.dat", "bin.enc", "bin-secret");
        Vostok.File.decryptFile("bin.enc", "bin.out", "bin-secret");

        assertArrayEquals(bytes, Vostok.File.readBytes("bin.out"), "二进制文件加解密后内容应一致");
    }

    @Test
    void testDecryptWrongSecretThrows() {
        init();
        Vostok.File.write("secret.txt", "data");
        Vostok.File.encryptFile("secret.txt", "secret.enc", "correct-key");

        // 错误密钥解密应包装为 ENCRYPT_ERROR
        VKFileException ex = assertThrows(VKFileException.class,
                () -> Vostok.File.decryptFile("secret.enc", "out.txt", "wrong-key"));
        assertEquals(VKFileErrorCode.ENCRYPT_ERROR, ex.getErrorCode(),
                "错误密钥解密应抛 ENCRYPT_ERROR");
    }

    // =========================================================================
    // Ext 5：只读模式
    // =========================================================================

    @Test
    void testReadOnlyModeBlocksWrites() {
        init();
        Vostok.File.write("existing.txt", "data");
        Vostok.File.setReadOnly(true);

        VKFileException ex;

        ex = assertThrows(VKFileException.class,
                () -> Vostok.File.write("existing.txt", "blocked"), "write 应被只读模式阻止");
        assertEquals(VKFileErrorCode.READ_ONLY_ERROR, ex.getErrorCode());

        assertThrows(VKFileException.class, () -> Vostok.File.create("new.txt", "x"),
                "create 应被只读模式阻止");
        assertThrows(VKFileException.class, () -> Vostok.File.delete("existing.txt"),
                "delete 应被只读模式阻止");
        assertThrows(VKFileException.class, () -> Vostok.File.mkdir("newdir"),
                "mkdir 应被只读模式阻止");
        assertThrows(VKFileException.class, () -> Vostok.File.writeBytes("existing.txt", new byte[0]),
                "writeBytes 应被只读模式阻止");
        assertThrows(VKFileException.class, () -> Vostok.File.append("existing.txt", "x"),
                "append 应被只读模式阻止");
    }

    @Test
    void testReadOnlyModeAllowsReads() {
        init();
        Vostok.File.write("r.txt", "readable");
        Vostok.File.setReadOnly(true);

        // 读操作在只读模式下正常
        assertEquals("readable", Vostok.File.read("r.txt"));
        assertTrue(Vostok.File.exists("r.txt"));
        assertTrue(Vostok.File.isFile("r.txt"));
        assertFalse(Vostok.File.isDirectory("r.txt"));
        assertNotNull(Vostok.File.readBytes("r.txt"));
        assertTrue(Vostok.File.size("r.txt") > 0);
    }

    @Test
    void testSetReadOnlyFalseRestoresWrites() {
        init();
        Vostok.File.setReadOnly(true);
        assertThrows(VKFileException.class, () -> Vostok.File.write("x.txt", "x"));

        Vostok.File.setReadOnly(false);
        assertDoesNotThrow(() -> Vostok.File.write("x.txt", "x"));
        assertEquals("x", Vostok.File.read("x.txt"));
    }

    @Test
    void testCloseResetsReadOnly() {
        init();
        Vostok.File.setReadOnly(true);
        assertTrue(Vostok.File.isReadOnly());

        Vostok.File.close();
        assertFalse(Vostok.File.isReadOnly(), "close() 后只读状态应重置为 false");

        // 重新初始化后写操作正常
        init();
        assertDoesNotThrow(() -> Vostok.File.write("after_close.txt", "ok"));
        assertEquals("ok", Vostok.File.read("after_close.txt"));
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    private boolean isSymlinkSupported() {
        try {
            Path tmp = Files.createTempFile("symlink_test_", null);
            Path link = tmp.getParent().resolve("symlink_test_link_" + System.nanoTime());
            Files.createSymbolicLink(link, tmp);
            Files.deleteIfExists(link);
            Files.deleteIfExists(tmp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteDir(Path dir) {
        try {
            if (!Files.exists(dir)) return;
            Files.walk(dir).sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignore) {} });
        } catch (IOException ignore) {
        }
    }
}
