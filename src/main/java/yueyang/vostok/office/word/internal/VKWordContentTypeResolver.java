package yueyang.vostok.office.word.internal;

import java.util.Locale;
import java.util.Map;

/** 根据后缀名解析图片 Content-Type。 */
public final class VKWordContentTypeResolver {
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("svg", "image/svg+xml")
    );

    private VKWordContentTypeResolver() {
    }

    public static String contentTypeByFileName(String fileName) {
        String ext = extension(fileName);
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    public static String extension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "bin";
        }
        String v = fileName.trim();
        int slash = Math.max(v.lastIndexOf('/'), v.lastIndexOf('\\'));
        if (slash >= 0) {
            v = v.substring(slash + 1);
        }
        int dot = v.lastIndexOf('.');
        if (dot < 0 || dot == v.length() - 1) {
            return "bin";
        }
        return v.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
