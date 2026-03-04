package yueyang.vostok.office.ppt.internal;

import java.util.Locale;

/** 根据后缀名解析图片 Content-Type。 */
public final class VKPptContentTypeResolver {
    private VKPptContentTypeResolver() {
    }

    public static String extension(String fileName) {
        String v = fileName == null ? "" : fileName.trim();
        int idx = v.lastIndexOf('.');
        String ext = idx < 0 ? "bin" : v.substring(idx + 1).toLowerCase(Locale.ROOT);
        if (ext.isEmpty()) {
            ext = "bin";
        }
        return ext;
    }

    public static String contentTypeByFileName(String fileName) {
        String ext = extension(fileName);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
