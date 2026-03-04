package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.security.VostokSecurity;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 写入 PPT 包中的 media 文件。 */
public final class VKPptMediaWriter {
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path packageRoot;
    private final Path baseDir;

    public VKPptMediaWriter(Path packageRoot, Path baseDir) {
        this.packageRoot = packageRoot;
        this.baseDir = baseDir;
    }

    public PreparedImage writeBytes(int imageIndex, String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PPT image bytes is empty");
        }

        String safeName = VKPptSecurityGuard.safeFileName(fileName, "image" + imageIndex + ".bin");
        String ext = VKPptContentTypeResolver.extension(safeName);
        String mediaPart = "ppt/media/image" + imageIndex + "." + ext;
        Path target = packageRoot.resolve(mediaPart).normalize();
        ensureTargetInPackage(target, mediaPart);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Write ppt image bytes failed: " + mediaPart, e);
        }

        return new PreparedImage(mediaPart, ext,
                VKPptContentTypeResolver.contentTypeByFileName(safeName), bytes.length);
    }

    public PreparedImage writeFile(int imageIndex, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PPT image file path is blank");
        }
        try {
            VostokSecurity.assertSafePath(filePath);
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Unsafe ppt image file path: " + filePath, e);
        }

        Path source = resolveAgainstBase(filePath);
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            throw new VKOfficeException(VKOfficeErrorCode.NOT_FOUND,
                    "PPT image file not found: " + filePath);
        }

        String ext = VKPptContentTypeResolver.extension(source.getFileName().toString());
        String mediaPart = "ppt/media/image" + imageIndex + "." + ext;
        Path target = packageRoot.resolve(mediaPart).normalize();
        ensureTargetInPackage(target, mediaPart);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = Files.newInputStream(source, StandardOpenOption.READ);
                 OutputStream out = Files.newOutputStream(target,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.TRUNCATE_EXISTING,
                         StandardOpenOption.WRITE)) {
                transfer(in, out);
            }
            long size = Files.size(target);
            return new PreparedImage(mediaPart, ext,
                    VKPptContentTypeResolver.contentTypeByFileName(source.getFileName().toString()), size);
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Copy ppt image file failed: " + source + " -> " + mediaPart, e);
        }
    }

    private Path resolveAgainstBase(String rawPath) {
        Path rel = Path.of(rawPath.trim());
        Path resolved = rel.isAbsolute() ? rel.toAbsolutePath().normalize() : baseDir.resolve(rel).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PPT image file path escapes file baseDir: " + rawPath);
        }
        return resolved;
    }

    private void ensureTargetInPackage(Path target, String mediaPart) {
        if (!target.startsWith(packageRoot.normalize())) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "PPT media path escapes package root: " + mediaPart);
        }
    }

    private void transfer(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }

    /** 已写入的图片信息。 */
    public record PreparedImage(String mediaPart,
                                String extension,
                                String contentType,
                                long size) {
    }
}
