package yueyang.vostok.web.http;

import yueyang.vostok.web.VKWebConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VKMultipartParser {
    private VKMultipartParser() {
    }

    public static VKMultipartData parse(VKRequest req, VKWebConfig cfg) {
        String contentType = req.header("content-type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            return VKMultipartData.EMPTY;
        }
        String boundary = findBoundary(contentType);
        if (boundary == null || boundary.isEmpty()) {
            throw new VKMultipartParseException(400, "Invalid multipart boundary");
        }

        byte[] body = req.body();
        if (body.length > cfg.getMultipartMaxTotalBytes()) {
            throw new VKMultipartParseException(413, "Multipart body too large");
        }

        byte[] marker = ("--" + boundary).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] nextMarker = ("\r\n--" + boundary).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int pos = 0;
        int parts = 0;
        Map<String, String> fields = new HashMap<>();
        Map<String, List<VKUploadedFile>> files = new HashMap<>();
        List<VKUploadedFile> all = new ArrayList<>();

        int first = indexOf(body, marker, 0);
        if (first < 0) {
            throw new VKMultipartParseException(400, "Malformed multipart content");
        }
        pos = first;

        while (pos < body.length) {
            if (!matches(body, pos, marker)) {
                throw new VKMultipartParseException(400, "Malformed multipart boundary");
            }
            pos += marker.length;
            if (startsWith(body, pos, "--")) {
                break;
            }
            if (!startsWith(body, pos, "\r\n")) {
                throw new VKMultipartParseException(400, "Malformed multipart section");
            }
            pos += 2;

            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII), pos);
            if (headerEnd < 0) {
                throw new VKMultipartParseException(400, "Malformed multipart headers");
            }
            Map<String, String> partHeaders = parsePartHeaders(body, pos, headerEnd);
            pos = headerEnd + 4;

            int next = indexOf(body, nextMarker, pos);
            if (next < 0) {
                throw new VKMultipartParseException(400, "Malformed multipart body");
            }

            int partLen = next - pos;
            parts++;
            if (parts > cfg.getMultipartMaxParts()) {
                throw new VKMultipartParseException(413, "Multipart parts exceed limit");
            }

            PartMeta meta = parseContentDisposition(partHeaders.get("content-disposition"));
            if (meta == null || meta.name == null || meta.name.isEmpty()) {
                throw new VKMultipartParseException(400, "Missing multipart field name");
            }

            if (meta.fileName == null) {
                fields.put(meta.name, new String(body, pos, Math.max(partLen, 0), java.nio.charset.StandardCharsets.UTF_8));
            } else {
                if (partLen > cfg.getMultipartMaxFileSizeBytes()) {
                    throw new VKMultipartParseException(413, "Multipart file too large");
                }
                VKUploadedFile file = buildUploadedFile(cfg, meta, partHeaders.get("content-type"), body, pos, partLen);
                files.computeIfAbsent(meta.name, k -> new ArrayList<>()).add(file);
                all.add(file);
            }
            pos = next + 2;
        }
        return new VKMultipartData(fields, files, all);
    }

    private static VKUploadedFile buildUploadedFile(VKWebConfig cfg,
                                                    PartMeta meta,
                                                    String contentType,
                                                    byte[] body,
                                                    int off,
                                                    int len) {
        String safeFileName = sanitizeFileName(meta.fileName);
        if (len <= cfg.getMultipartInMemoryThresholdBytes()) {
            byte[] inMemory = new byte[len];
            if (len > 0) {
                System.arraycopy(body, off, inMemory, 0, len);
            }
            return new VKUploadedFile(meta.name, safeFileName, contentType, inMemory, null, len);
        }
        try {
            Path dir = Path.of(cfg.getMultipartTempDir());
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "vk-upload-", ".tmp");
            if (len > 0) {
                Files.write(tmp, java.util.Arrays.copyOfRange(body, off, off + len));
            }
            return new VKUploadedFile(meta.name, safeFileName, contentType, null, tmp, len);
        } catch (IOException e) {
            throw new VKMultipartParseException(500, "Failed to save multipart file");
        }
    }

    private static String sanitizeFileName(String input) {
        if (input == null) {
            return null;
        }
        String out = input.replace('\\', '/');
        int idx = out.lastIndexOf('/');
        if (idx >= 0) {
            out = out.substring(idx + 1);
        }
        return out.replace("\r", "").replace("\n", "");
    }

    private static String findBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String p : parts) {
            String s = p.trim();
            if (s.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                String v = s.substring("boundary=".length()).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return null;
    }

    private static Map<String, String> parsePartHeaders(byte[] buf, int start, int end) {
        Map<String, String> out = new HashMap<>();
        int pos = start;
        while (pos < end) {
            int lineEnd = indexOf(buf, "\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII), pos);
            if (lineEnd < 0 || lineEnd > end) {
                break;
            }
            int colon = -1;
            for (int i = pos; i < lineEnd; i++) {
                if (buf[i] == ':') {
                    colon = i;
                    break;
                }
            }
            if (colon > pos) {
                String name = new String(buf, pos, colon - pos, java.nio.charset.StandardCharsets.US_ASCII)
                        .toLowerCase(Locale.ROOT).trim();
                String value = new String(buf, colon + 1, lineEnd - colon - 1, java.nio.charset.StandardCharsets.UTF_8)
                        .trim();
                out.put(name, value);
            }
            pos = lineEnd + 2;
        }
        return out;
    }

    private static PartMeta parseContentDisposition(String value) {
        if (value == null) {
            return null;
        }
        String[] segs = value.split(";");
        String name = null;
        String fileName = null;
        for (String seg : segs) {
            String s = seg.trim();
            int eq = s.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = s.substring(eq + 1).trim();
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
            if ("name".equals(k)) {
                name = v;
            } else if ("filename".equals(k)) {
                fileName = v;
            }
        }
        return new PartMeta(name, fileName);
    }

    private static boolean startsWith(byte[] src, int pos, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (pos + bytes.length > src.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (src[pos + i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(byte[] src, int pos, byte[] target) {
        if (pos < 0 || pos + target.length > src.length) {
            return false;
        }
        for (int i = 0; i < target.length; i++) {
            if (src[pos + i] != target[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] src, byte[] target, int from) {
        if (target.length == 0) {
            return from;
        }
        outer:
        for (int i = Math.max(0, from); i <= src.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (src[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private record PartMeta(String name, String fileName) {
    }

    public static final class VKMultipartData {
        static final VKMultipartData EMPTY = new VKMultipartData(Map.of(), Map.of(), List.of());
        final Map<String, String> fields;
        final Map<String, List<VKUploadedFile>> files;
        final List<VKUploadedFile> all;

        VKMultipartData(Map<String, String> fields, Map<String, List<VKUploadedFile>> files, List<VKUploadedFile> all) {
            this.fields = fields;
            this.files = files;
            this.all = all;
        }
    }
}
