package yueyang.vostok.security.crypto;

import yueyang.vostok.security.exception.VKSecurityException;
import yueyang.vostok.security.keystore.VKKeyStore;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 文件加解密核心工具类，基于 AES-256-GCM + DEK/KEK 双层密钥（Key Wrapping）。
 *
 * <p>设计目标：
 * <ul>
 *   <li><b>二进制安全</b>：直接操作字节流，不经过 String/UTF-8 转换，适用于任何类型文件（图片、PDF、ZIP 等）</li>
 *   <li><b>流式加解密</b>：加解密均按 {@value #CHUNK_SIZE} 字节分块，内存消耗 O(分块大小)，不受文件大小限制</li>
 *   <li><b>完整性保护</b>：AES-GCM 内置 128 位认证标签，每个分块独立验证，任意分块被篡改均可检测</li>
 *   <li><b>密钥隔离</b>：每次加密随机生成一次性 DEK，被当前 KEK 包裹后写入文件头，
 *       KEK 轮换无需重加密历史文件</li>
 * </ul>
 *
 * <p>vkf2 文件格式（v2，当前默认）：
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  4 bytes  Magic: 'V','K','F','C'                                │
 * │  1 byte   版本号: 0x02                                          │
 * │  1 byte   keyId 字节长度（uint8，max 255）                      │
 * │  N bytes  keyId（UTF-8 编码）                                   │
 * │  8 bytes  kekVersion（int64 big-endian）                        │
 * │  2 bytes  wrappedDek 字节长度（uint16 big-endian）              │
 * │  M bytes  wrappedDek（Base64 字符串的 UTF-8 字节）              │
 * ├──────────────────────────────────────────────────────────────────┤
 * │  循环分块（直到结束标记）：                                      │
 * │    4 bytes  密文长度 L（uint32，含 16 字节 GCM 认证标签）       │
 * │   12 bytes  分块 GCM IV（SecureRandom 生成，每块唯一）          │
 * │    L bytes  AES-256-GCM 密文 + 认证标签                         │
 * │  4 bytes  结束标记: 0x00000000（密文长度为 0）                  │
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>v1 遗留格式（仅用于向后兼容解密，不再用于加密）：
 * <pre>
 * 头部字段相同（版本号 0x01），头部之后紧跟 12 字节全局 GCM IV，
 * 然后是完整密文（末尾含 16 字节 GCM 认证标签）。
 * 解密时需将全部密文读入内存，不建议用于大文件。
 * </pre>
 *
 * <p>流式解密安全保证：每个分块在 GCM 认证标签验证通过后才写出明文，
 * 任意分块标签失败均立即抛出 {@link VKSecurityException}，
 * 由 {@code decryptFile} 的临时文件机制保证不向最终目标写入任何字节。
 *
 * @see yueyang.vostok.security.VostokSecurity#encryptFile
 * @see yueyang.vostok.security.VostokSecurity#decryptFile
 */
public final class VKFileCrypto {

    /** vkf1/vkf2 格式魔数，用于识别加密文件类型 */
    static final byte[] MAGIC = {'V', 'K', 'F', 'C'};

    /** 当前格式版本号（v2 分块流式格式，加密时使用） */
    static final byte VERSION = 2;

    /** v1 遗留格式版本号，仅用于向后兼容解密 */
    private static final byte VERSION_V1_LEGACY = 1;

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    /** GCM 认证标签字节长度（128 bit = 16 bytes） */
    private static final int GCM_TAG_BYTES = GCM_TAG_BITS / 8;

    /**
     * 加解密分块大小：1 MB。
     *
     * <p>加密时每次从输入流读取最多 1 MB 明文作为一个分块；
     * 解密时每个分块的密文最大为 {@code CHUNK_SIZE + GCM_TAG_BYTES}，
     * 堆内存峰值约为 {@code 2 × CHUNK_SIZE}（密文缓冲 + 明文缓冲）。
     */
    private static final int CHUNK_SIZE = 1024 * 1024; // 1 MB

    private VKFileCrypto() {
    }

    /**
     * 流式加密：从 {@code in} 读取明文，以 vkf2 分块格式写入 {@code out}。
     *
     * <p>加密流程：
     * <ol>
     *   <li>随机生成一次性 DEK（Data Encryption Key）</li>
     *   <li>用 {@code keyId} 当前 KEK 包裹 DEK，得到 {@code "{kekVersion}:{wrappedDek}"}</li>
     *   <li>写入 vkf2 文件头</li>
     *   <li>循环读取 {@value #CHUNK_SIZE} 字节明文分块：为每块生成独立随机 IV，
     *       {@code doFinal()} 加密并追加 GCM 认证标签，写入 [密文长度][IV][密文+标签]</li>
     *   <li>写入 4 字节零值结束标记</li>
     * </ol>
     *
     * <p>注意：{@code in} 和 {@code out} 均不会被此方法关闭，由调用方管理生命周期。
     *
     * @param in       明文输入流
     * @param out      密文输出流（将写入 vkf2 格式）
     * @param keyId    KEK 标识符（UTF-8 编码长度 ≤ 255 字节）
     * @param keyStore 密钥存储（需支持 Key Wrapping）
     * @throws IOException         读写 IO 异常
     * @throws VKSecurityException keyId 非法、密钥操作失败或加密异常
     */
    public static void encrypt(InputStream in, OutputStream out, String keyId,
                               VKKeyStore keyStore) throws IOException {
        if (keyId == null || keyId.isBlank()) {
            throw new VKSecurityException("keyId must not be blank");
        }
        byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);
        if (keyIdBytes.length > 255) {
            throw new VKSecurityException("keyId too long (max 255 UTF-8 bytes): " + keyId);
        }

        // 随机生成一次性 DEK，用于实际加密文件内容；DEK 不持久化，仅被 KEK 包裹后随文件头携带
        String dekBase64 = VKAesCrypto.generateAesKeyBase64();

        // 用当前 KEK 包裹 DEK，返回格式 "{kekVersion}:{wrappedDekBase64}"
        String versionedWrapped = keyStore.wrapDek(keyId, dekBase64);
        int colonIdx = versionedWrapped.indexOf(':');
        long kekVersion = Long.parseLong(versionedWrapped.substring(0, colonIdx));
        String wrappedDek = versionedWrapped.substring(colonIdx + 1);
        byte[] wrappedDekBytes = wrappedDek.getBytes(StandardCharsets.UTF_8);
        if (wrappedDekBytes.length > 0xFFFF) {
            throw new VKSecurityException("wrappedDek exceeds max length (65535 bytes)");
        }

        // 写入 vkf2 文件头；DataOutputStream 保证所有多字节字段为 big-endian
        // v2 头部无全局 GCM IV（IV 改为每分块独立生成，携带在分块体内）
        DataOutputStream dos = new DataOutputStream(out);
        dos.write(MAGIC);                       // 4 bytes: 魔数 "VKFC"
        dos.write(VERSION);                     // 1 byte:  版本号 0x02
        dos.write(keyIdBytes.length);           // 1 byte:  keyId 字节长度（uint8）
        dos.write(keyIdBytes);                  // N bytes: keyId（UTF-8）
        dos.writeLong(kekVersion);              // 8 bytes: KEK 版本号（big-endian int64）
        dos.writeShort(wrappedDekBytes.length); // 2 bytes: wrappedDek 字节长度（big-endian uint16）
        dos.write(wrappedDekBytes);             // M bytes: 被 KEK 加密后的 DEK（Base64）
        dos.flush();

        // 分块流式 AES-256-GCM 加密；每块独立 IV + doFinal，内存占用 O(CHUNK_SIZE)
        try {
            byte[] dekRaw = Base64.getDecoder().decode(dekBase64);
            SecureRandom rng = new SecureRandom();
            SecretKeySpec keySpec = new SecretKeySpec(dekRaw, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            byte[] buf = new byte[CHUNK_SIZE];
            int n;
            // readNBytes 保证尽量填满缓冲区（仅在 EOF 时返回 < CHUNK_SIZE），使分块大小稳定
            while ((n = in.readNBytes(buf, 0, CHUNK_SIZE)) > 0) {
                // 每个分块使用独立随机 IV；GCM 要求同一 DEK 下 IV 绝不重复
                byte[] chunkIv = new byte[GCM_IV_BYTES];
                rng.nextBytes(chunkIv);

                cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, chunkIv));
                // doFinal 对整个分块加密并在末尾追加 16 字节 GCM 认证标签
                byte[] chunkCipher = cipher.doFinal(buf, 0, n);

                dos.writeInt(chunkCipher.length);   // 4 bytes: 密文长度（明文长度 + 16）
                dos.write(chunkIv);                  // 12 bytes: 分块随机 IV
                dos.write(chunkCipher);              // N+16 bytes: 密文 + GCM 认证标签
            }
            dos.writeInt(0);  // 4 bytes: 结束标记，解密端以此识别分块流结尾
            dos.flush();
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("File encrypt failed: " + e.getMessage());
        }
    }

    /**
     * 解密：从 {@code in} 读取 vkf1/vkf2 格式密文，解密后写入 {@code out}。
     *
     * <p>解密流程：
     * <ol>
     *   <li>验证文件头魔数与版本号（支持 v1/v2 两种格式）</li>
     *   <li>提取 keyId、kekVersion、wrappedDek</li>
     *   <li>用历史 KEK（kekVersion 版本）解包 DEK</li>
     *   <li>v1：将全部密文读入内存，一次性 {@code doFinal()} 验证标签后输出明文</li>
     *   <li>v2：逐分块读取、验证 GCM 标签、写出明文，内存占用 O(分块大小)</li>
     * </ol>
     *
     * <p>注意：{@code in} 和 {@code out} 均不会被此方法关闭，由调用方管理生命周期。
     * 文件级别的原子写保护（防止部分明文写入目标文件）由 {@code decryptFile} 的临时文件机制提供。
     *
     * @param in       密文输入流（vkf1 或 vkf2 格式）
     * @param out      明文输出流
     * @param keyStore 密钥存储（需支持 Key Wrapping）
     * @throws IOException         IO 异常或文件格式无效（魔数/版本不匹配、头部截断）
     * @throws VKSecurityException 密钥不存在、认证标签验证失败（密文被篡改）
     */
    public static void decrypt(InputStream in, OutputStream out, VKKeyStore keyStore)
            throws IOException {
        DataInputStream dis = new DataInputStream(in);

        // 验证魔数，防止误解析非 vkf1/vkf2 文件或不完整文件
        byte[] magic = new byte[4];
        dis.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new VKSecurityException("Invalid file format: not a vkf1 encrypted file");
        }

        // 读取版本号，决定后续解密路径（v1 遗留全文读入 / v2 分块流式）
        int version = dis.readUnsignedByte();
        if (version != VERSION_V1_LEGACY && version != VERSION) {
            throw new VKSecurityException("Unsupported vkf1 file version: " + version);
        }

        // 读取公共头部字段（v1、v2 格式相同）
        int keyIdLen = dis.readUnsignedByte();
        byte[] keyIdBytes = new byte[keyIdLen];
        dis.readFully(keyIdBytes);
        String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);

        // 读取 KEK 版本号（8 字节 big-endian int64），用于查找历史 KEK
        long kekVersion = dis.readLong();

        // 读取 wrappedDek（2 字节长度前缀 + 内容）
        int wrappedDekLen = dis.readUnsignedShort();
        byte[] wrappedDekBytes = new byte[wrappedDekLen];
        dis.readFully(wrappedDekBytes);
        String wrappedDek = new String(wrappedDekBytes, StandardCharsets.UTF_8);

        // 用历史 KEK（kekVersion 指定版本）解包 DEK；支持跨 KEK 轮换解密
        String dekBase64 = keyStore.unwrapDek(keyId, kekVersion, wrappedDek);
        byte[] dekRaw = Base64.getDecoder().decode(dekBase64);

        // 根据版本号分发到对应解密实现
        if (version == VERSION_V1_LEGACY) {
            decryptLegacyV1(dis, out, dekRaw);
        } else {
            decryptChunkedV2(dis, out, dekRaw);
        }
    }

    /**
     * v1 遗留解密：读取全局 IV，将全部密文读入内存，一次性 doFinal 验证 GCM 标签后输出明文。
     *
     * <p>仅用于向后兼容旧格式文件，新文件均以 v2 分块格式加密。
     * 大文件（数 GB）需关注 JVM 堆内存，至少需要约等于密文大小的可用内存。
     *
     * @throws VKSecurityException GCM 认证标签验证失败（密文被篡改）
     * @throws IOException         IO 异常（文件截断、读取失败）
     */
    private static void decryptLegacyV1(DataInputStream dis, OutputStream out,
                                         byte[] dekRaw) throws IOException {
        // v1 头部在 wrappedDek 之后携带 12 字节全局 GCM IV
        byte[] iv = new byte[GCM_IV_BYTES];
        dis.readFully(iv);

        // 将剩余字节（AES-GCM 密文 + 16 字节认证标签）全部读入内存，一次性 doFinal 完成标签验证
        byte[] ciphertext = dis.readAllBytes();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(dekRaw, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            // doFinal 同时完成解密 + GCM 认证标签验证；标签不匹配时抛出 AEADBadTagException
            byte[] plaintext = cipher.doFinal(ciphertext);
            out.write(plaintext);
        } catch (VKSecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new VKSecurityException("File decrypt failed: " + e.getMessage());
        }
    }

    /**
     * v2 分块流式解密：逐块读取 → GCM 认证标签验证 → 写出明文，内存占用 O({@value #CHUNK_SIZE})。
     *
     * <p>v2 分块体格式（循环读取，直到结束标记）：
     * <pre>
     * [4 bytes]  密文长度 L（uint32，含 16 字节 GCM 认证标签）；0 为结束标记
     * [12 bytes] 分块随机 IV（SecureRandom 生成，每块唯一）
     * [L bytes]  AES-256-GCM 密文 + 认证标签
     * </pre>
     *
     * <p>任意分块 GCM 标签验证失败（密文被篡改）、文件截断（EOFException 等 IOException）
     * 或分块长度非法，均立即抛出 {@link VKSecurityException}，不写出任何字节。
     * 调用方 {@code decryptFile} 通过临时文件机制保证目标文件不受污染。
     *
     * <p>注意：{@code out.write()} 抛出的 {@link IOException}（如磁盘满）会正常向上传播，
     * 不被包装为 {@link VKSecurityException}，由调用方统一处理。
     *
     * @throws VKSecurityException 分块认证失败、文件结构损坏或截断
     * @throws IOException         向 out 写出明文时发生 IO 错误
     */
    private static void decryptChunkedV2(DataInputStream dis, OutputStream out,
                                          byte[] dekRaw) throws IOException {
        SecretKeySpec keySpec = new SecretKeySpec(dekRaw, "AES");
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (Exception e) {
            throw new VKSecurityException("Failed to init AES/GCM cipher: " + e.getMessage());
        }

        while (true) {
            // 读取分块密文长度；0 为结束标记；EOF 表示文件截断
            int chunkLen;
            try {
                chunkLen = dis.readInt();
            } catch (IOException e) {
                throw new VKSecurityException(
                        "File decrypt failed: unexpected end of stream reading chunk header");
            }
            if (chunkLen == 0) break;  // 正常结束标记

            // 合法密文长度范围：[1字节明文+16字节标签, CHUNK_SIZE字节明文+16字节标签]
            if (chunkLen < GCM_TAG_BYTES + 1 || chunkLen > CHUNK_SIZE + GCM_TAG_BYTES) {
                throw new VKSecurityException(
                        "Invalid chunk ciphertext length in vkf2 stream: " + chunkLen);
            }

            // 读取分块 IV 和密文；文件截断时抛出 EOFException，包装为安全异常
            byte[] chunkIv = new byte[GCM_IV_BYTES];
            byte[] chunkCipher = new byte[chunkLen];
            try {
                dis.readFully(chunkIv);
                dis.readFully(chunkCipher);
            } catch (IOException e) {
                throw new VKSecurityException(
                        "File decrypt failed: unexpected end of stream in chunk data");
            }

            // 每分块独立 GCM 验证；AEADBadTagException 表明该分块被篡改，立即终止
            byte[] plaintext;
            try {
                cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, chunkIv));
                plaintext = cipher.doFinal(chunkCipher);
            } catch (VKSecurityException e) {
                throw e;
            } catch (Exception e) {
                throw new VKSecurityException("File decrypt failed: " + e.getMessage());
            }

            // 认证通过后方可写出明文；out.write 的 IOException 正常传播
            out.write(plaintext);
        }
    }
}
