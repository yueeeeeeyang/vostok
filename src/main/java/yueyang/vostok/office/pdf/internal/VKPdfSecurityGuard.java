package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.security.VKSecurityCheckResult;
import yueyang.vostok.security.VostokSecurity;
import yueyang.vostok.security.file.VKFileType;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** PDF 安全校验封装。 */
public final class VKPdfSecurityGuard {
    private static final int MAGIC_READ_BYTES = 8192;

    private VKPdfSecurityGuard() {
    }

    public static void assertSafePath(String rawPath) {
        try {
            VostokSecurity.assertSafePath(rawPath);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Unsafe pdf path: " + rawPath, e);
        }
    }

    public static void assertPdfMagic(Path sourcePath) {
        byte[] head = readHead(sourcePath, MAGIC_READ_BYTES);
        VKSecurityCheckResult magic = VostokSecurity.checkFileMagic(head, VKFileType.PDF);
        if (!magic.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PDF file magic check failed: " + magic.getReasons());
        }
        VKSecurityCheckResult script = VostokSecurity.checkExecutableScriptUpload(sourcePath.getFileName().toString(), head);
        if (!script.isSafe()) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PDF file script check failed: " + script.getReasons());
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
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Read pdf header failed: " + path, e);
        }
    }
}
