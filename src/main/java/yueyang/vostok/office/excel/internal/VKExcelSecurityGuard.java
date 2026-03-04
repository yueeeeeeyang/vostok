package yueyang.vostok.office.excel.internal;

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

/** Excel 读写安全校验封装。 */
public final class VKExcelSecurityGuard {
    private static final int MAGIC_READ_BYTES = 8192;

    private VKExcelSecurityGuard() {
    }

    public static void assertSafePath(String rawPath) {
        try {
            VostokSecurity.assertSafePath(rawPath);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Unsafe excel path: " + rawPath, e);
        }
    }

    public static void assertZipMagic(Path sourcePath) {
        byte[] head = readHead(sourcePath, MAGIC_READ_BYTES);
        VKSecurityCheckResult magic = VostokSecurity.checkFileMagic(head, VKFileType.ZIP);
        if (!magic.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Excel file magic check failed: " + magic.getReasons());
        }
        VKSecurityCheckResult script = VostokSecurity.checkExecutableScriptUpload(sourcePath.getFileName().toString(), head);
        if (!script.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Excel file script check failed: " + script.getReasons());
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
                    "Excel XML XXE check failed: " + xxe.getReasons());
        }
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
                    "Read excel header failed: " + path, e);
        }
    }
}
