package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VostokSecurity;
import yueyang.vostok.security.file.VKFileType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** PPT 读写安全校验封装。 */
public final class VKPptSecurityGuard {
    private static final int MAGIC_READ_BYTES = 8192;

    private VKPptSecurityGuard() {
    }

    public static void assertSafePath(String rawPath) {
        try {
            VostokSecurity.assertSafePath(rawPath);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Unsafe ppt path: " + rawPath, e);
        }
    }

    public static void assertZipMagic(Path sourcePath) {
        byte[] head = readHead(sourcePath, MAGIC_READ_BYTES);
        VKSecurityCheckResult magic = VostokSecurity.checkFileMagic(head, VKFileType.ZIP);
        if (!magic.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PPT file magic check failed: " + magic.getReasons());
        }
        VKSecurityCheckResult script = VostokSecurity.checkExecutableScriptUpload(sourcePath.getFileName().toString(), head);
        if (!script.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PPT file script check failed: " + script.getReasons());
        }
    }

    public static void assertSafeXmlSample(Path xmlPath, int sampleBytes) {
        if (xmlPath == null || !Files.exists(xmlPath) || !Files.isRegularFile(xmlPath)) {
            return;
        }
        int read = Math.max(1024, sampleBytes);
        byte[] head = readHead(xmlPath, read);
        String sample = new String(head, StandardCharsets.UTF_8);
        VKSecurityCheckResult xxe = VostokSecurity.checkXxe(sample);
        if (!xxe.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PPT XML XXE check failed: " + xxe.getReasons());
        }
    }

    /** 仅保留安全文件名，防止路径穿越。 */
    public static String safeFileName(String fileName, String fallback) {
        String v = fileName == null ? "" : fileName.trim();
        if (v.isEmpty()) {
            return fallback;
        }
        v = v.replace('\\', '/');
        int idx = v.lastIndexOf('/');
        String base = idx >= 0 ? v.substring(idx + 1) : v;
        if (base.isBlank() || ".".equals(base) || "..".equals(base)) {
            return fallback;
        }
        return base;
    }

    private static byte[] readHead(Path path, int maxBytes) {
        int limit = Math.max(1, maxBytes);
        byte[] buffer = new byte[limit];
        try (InputStream in = Files.newInputStream(path)) {
            int n = in.read(buffer);
            if (n <= 0) {
                return new byte[0];
            }
            if (n == limit) {
                return buffer;
            }
            byte[] out = new byte[n];
            System.arraycopy(buffer, 0, out, 0, n);
            return out;
        } catch (IOException e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Read ppt header failed: " + path, e);
        }
    }
}
