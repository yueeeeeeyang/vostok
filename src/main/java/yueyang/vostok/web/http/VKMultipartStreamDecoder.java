package yueyang.vostok.web.http;

import yueyang.vostok.web.VKWebConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VKMultipartStreamDecoder {
    private enum State {
        START_BOUNDARY,
        PART_HEADERS,
        PART_BODY,
        END
    }

    private final VKWebConfig cfg;
    private final byte[] startBoundary;
    private final byte[] bodyBoundary;
    private byte[] pending = new byte[4096];
    private int pendingLen;
    private State state = State.START_BOUNDARY;

    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, List<VKUploadedFile>> files = new HashMap<>();
    private final List<VKUploadedFile> allFiles = new ArrayList<>();
    private long totalBytes;
    private int parts;

    private String currentFieldName;
    private String currentFileName;
    private String currentContentType;
    private PartSink currentSink;

    public VKMultipartStreamDecoder(String boundary, VKWebConfig cfg) {
        this.cfg = cfg;
        this.startBoundary = ("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII);
        this.bodyBoundary = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
    }

    public void feed(byte[] src, int off, int len) {
        if (state == State.END || len <= 0) {
            return;
        }
        ensurePendingCapacity(pendingLen + len);
        System.arraycopy(src, off, pending, pendingLen, len);
        pendingLen += len;
        consume();
    }

    public void finish() {
        consume();
        if (state != State.END) {
            throw new VKMultipartParseException(400, "Malformed multipart stream");
        }
    }

    public VKMultipartData result() {
        return new VKMultipartData(new HashMap<>(fields), new HashMap<>(files), new ArrayList<>(allFiles));
    }

    public void abort() {
        if (currentSink != null) {
            currentSink.abort();
            currentSink = null;
        }
        for (VKUploadedFile file : allFiles) {
            file.cleanup();
        }
        allFiles.clear();
        files.clear();
    }

    private void consume() {
        while (true) {
            if (state == State.START_BOUNDARY) {
                if (pendingLen < startBoundary.length) {
                    return;
                }
                if (!startsWith(pending, pendingLen, startBoundary)) {
                    throw new VKMultipartParseException(400, "Malformed multipart boundary");
                }
                discard(startBoundary.length);
                state = State.PART_HEADERS;
                continue;
            }
            if (state == State.PART_HEADERS) {
                int idx = indexOf(pending, pendingLen, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
                if (idx < 0) {
                    return;
                }
                parsePartHeaders(pending, 0, idx);
                discard(idx + 4);
                state = State.PART_BODY;
                continue;
            }
            if (state == State.PART_BODY) {
                int idx = indexOf(pending, pendingLen, bodyBoundary, 0);
                if (idx < 0) {
                    int keep = bodyBoundary.length + 4;
                    int safe = pendingLen - keep;
                    if (safe > 0) {
                        writePartBody(pending, 0, safe);
                        discard(safe);
                    }
                    return;
                }
                writePartBody(pending, 0, idx);
                int cursor = idx + bodyBoundary.length;
                if (pendingLen < cursor + 2) {
                    return;
                }
                boolean isFinal = pending[cursor] == '-' && pending[cursor + 1] == '-';
                if (isFinal) {
                    int consume = cursor + 2;
                    if (pendingLen >= consume + 2 && pending[consume] == '\r' && pending[consume + 1] == '\n') {
                        consume += 2;
                    }
                    finishPart();
                    discard(consume);
                    state = State.END;
                    return;
                }
                if (pending[cursor] != '\r' || pending[cursor + 1] != '\n') {
                    throw new VKMultipartParseException(400, "Malformed multipart section");
                }
                finishPart();
                discard(cursor + 2);
                state = State.PART_HEADERS;
                continue;
            }
            return;
        }
    }

    private void parsePartHeaders(byte[] buf, int start, int end) {
        Map<String, String> hs = new HashMap<>();
        int pos = start;
        while (pos < end) {
            int lineEnd = indexOf(buf, end, "\r\n".getBytes(StandardCharsets.US_ASCII), pos);
            if (lineEnd < 0 || lineEnd > end) {
                break;
            }
            readHeaderLine(buf, pos, lineEnd, hs);
            pos = lineEnd + 2;
        }
        if (pos < end) {
            readHeaderLine(buf, pos, end, hs);
        }
        String cd = hs.get("content-disposition");
        if (cd == null) {
            throw new VKMultipartParseException(400, "Missing content-disposition");
        }
        String name = null;
        String fileName = null;
        String[] segs = cd.split(";");
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
                fileName = sanitizeFileName(v);
            }
        }
        if (name == null || name.isEmpty()) {
            throw new VKMultipartParseException(400, "Missing multipart field name");
        }
        parts++;
        if (parts > cfg.getMultipartMaxParts()) {
            throw new VKMultipartParseException(413, "Multipart parts exceed limit");
        }
        currentFieldName = name;
        currentFileName = fileName;
        currentContentType = hs.get("content-type");
        currentSink = new PartSink(cfg, fileName != null);
    }

    private void readHeaderLine(byte[] buf, int start, int end, Map<String, String> hs) {
        if (start >= end) {
            return;
        }
        int colon = -1;
        for (int i = start; i < end; i++) {
            if (buf[i] == ':') {
                colon = i;
                break;
            }
        }
        if (colon <= start) {
            return;
        }
        String name = new String(buf, start, colon - start, StandardCharsets.US_ASCII).trim().toLowerCase(Locale.ROOT);
        String value = new String(buf, colon + 1, end - colon - 1, StandardCharsets.UTF_8).trim();
        hs.put(name, value);
    }

    private void writePartBody(byte[] buf, int off, int len) {
        if (len <= 0) {
            return;
        }
        totalBytes += len;
        if (totalBytes > cfg.getMultipartMaxTotalBytes()) {
            throw new VKMultipartParseException(413, "Multipart body too large");
        }
        currentSink.write(buf, off, len);
    }

    private void finishPart() {
        VKUploadedFile file = currentSink.finish(currentFieldName, currentFileName, currentContentType);
        if (file == null) {
            String text = currentSink.text();
            fields.put(currentFieldName, text);
        } else {
            files.computeIfAbsent(currentFieldName, k -> new ArrayList<>()).add(file);
            allFiles.add(file);
        }
        currentSink = null;
        currentFieldName = null;
        currentFileName = null;
        currentContentType = null;
    }

    private String sanitizeFileName(String input) {
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

    private boolean startsWith(byte[] src, int srcLen, byte[] prefix) {
        if (srcLen < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (src[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private int indexOf(byte[] src, int srcLen, byte[] target, int from) {
        if (target.length == 0) {
            return from;
        }
        outer:
        for (int i = Math.max(0, from); i <= srcLen - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (src[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private void ensurePendingCapacity(int cap) {
        if (cap <= pending.length) {
            return;
        }
        int n = pending.length;
        while (n < cap) {
            n <<= 1;
        }
        byte[] next = new byte[n];
        System.arraycopy(pending, 0, next, 0, pendingLen);
        pending = next;
    }

    private void discard(int n) {
        if (n <= 0) {
            return;
        }
        if (n >= pendingLen) {
            pendingLen = 0;
            return;
        }
        System.arraycopy(pending, n, pending, 0, pendingLen - n);
        pendingLen -= n;
    }

    private static final class PartSink {
        private final VKWebConfig cfg;
        private final boolean filePart;
        private final ByteArrayOutputStream memory = new ByteArrayOutputStream(1024);
        private Path tempFile;
        private java.io.OutputStream fileOut;
        private long size;

        PartSink(VKWebConfig cfg, boolean filePart) {
            this.cfg = cfg;
            this.filePart = filePart;
        }

        void write(byte[] src, int off, int len) {
            size += len;
            if (filePart && size > cfg.getMultipartMaxFileSizeBytes()) {
                throw new VKMultipartParseException(413, "Multipart file too large");
            }
            if (!filePart && size > cfg.getMultipartInMemoryThresholdBytes()) {
                throw new VKMultipartParseException(413, "Multipart field too large");
            }

            try {
                if (filePart && (tempFile != null || memory.size() + len > cfg.getMultipartInMemoryThresholdBytes())) {
                    ensureFileOut();
                    if (memory.size() > 0) {
                        memory.writeTo(fileOut);
                        memory.reset();
                    }
                    fileOut.write(src, off, len);
                    return;
                }
                memory.write(src, off, len);
            } catch (IOException e) {
                throw new VKMultipartParseException(500, "Failed to write multipart data");
            }
        }

        VKUploadedFile finish(String fieldName, String fileName, String contentType) {
            try {
                if (fileOut != null) {
                    fileOut.flush();
                    fileOut.close();
                    fileOut = null;
                }
            } catch (IOException e) {
                throw new VKMultipartParseException(500, "Failed to close multipart file");
            }
            if (!filePart) {
                return null;
            }
            if (tempFile != null) {
                return new VKUploadedFile(fieldName, fileName, contentType, null, tempFile, size);
            }
            byte[] bytes = memory.toByteArray();
            return new VKUploadedFile(fieldName, fileName, contentType, bytes, null, bytes.length);
        }

        String text() {
            return memory.toString(StandardCharsets.UTF_8);
        }

        void abort() {
            try {
                if (fileOut != null) {
                    fileOut.close();
                }
            } catch (IOException ignore) {
            }
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignore) {
                }
            }
        }

        private void ensureFileOut() throws IOException {
            if (fileOut != null) {
                return;
            }
            Path dir = Path.of(cfg.getMultipartTempDir());
            Files.createDirectories(dir);
            tempFile = Files.createTempFile(dir, "vk-upload-", ".tmp");
            fileOut = Files.newOutputStream(tempFile);
        }
    }
}
