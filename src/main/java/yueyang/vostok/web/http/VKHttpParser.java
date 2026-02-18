package yueyang.vostok.web.http;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class VKHttpParser {
    private final int maxHeaderBytes;
    private final int maxBodyBytes;

    public VKHttpParser(int maxHeaderBytes, int maxBodyBytes) {
        this.maxHeaderBytes = maxHeaderBytes;
        this.maxBodyBytes = maxBodyBytes;
    }

    public ParsedRequest parse(byte[] buf, int len, InetSocketAddress remote, boolean sentContinue) {
        HeaderInfo info = parseHeaders(buf, len);
        if (info == null) {
            if (len > maxHeaderBytes) {
                throw new VKHttpParseException(431, "Header too large");
            }
            return null;
        }

        int bodyStart = info.headerEnd + 4;
        if (info.chunked) {
            ChunkResult chunk = parseChunked(buf, len, bodyStart);
            if (chunk == null) {
                return null;
            }
            VKRequest req = new VKRequest(info.method, info.path, info.query, info.version, info.headers,
                    chunk.body, info.keepAlive, remote);
            return new ParsedRequest(req, chunk.consumed);
        }

        int contentLength = info.contentLength;
        if (contentLength > maxBodyBytes) {
            throw new VKHttpParseException(413, "Body too large");
        }
        int total = bodyStart + contentLength;
        if (len < total) {
            return null;
        }

        byte[] body = new byte[contentLength];
        if (contentLength > 0) {
            System.arraycopy(buf, bodyStart, body, 0, contentLength);
        }
        VKRequest req = new VKRequest(info.method, info.path, info.query, info.version, info.headers, body,
                info.keepAlive, remote);
        return new ParsedRequest(req, total);
    }

    public boolean shouldSendContinue(byte[] buf, int len) {
        HeaderInfo info = parseHeaders(buf, len);
        if (info == null) {
            return false;
        }
        return info.expectContinue && (info.contentLength > 0 || info.chunked);
    }

    private HeaderInfo parseHeaders(byte[] buf, int len) {
        int headerEnd = indexOfHeaderEnd(buf, len, 0);
        if (headerEnd < 0) {
            return null;
        }
        if (headerEnd > maxHeaderBytes) {
            throw new VKHttpParseException(431, "Header too large");
        }

        int lineEnd = indexOfCRLF(buf, len, 0);
        if (lineEnd <= 0) {
            throw new VKHttpParseException(400, "Bad request line");
        }

        int mStart = 0;
        int mEnd = indexOfByte(buf, mStart, lineEnd, (byte) ' ');
        if (mEnd < 0) {
            throw new VKHttpParseException(400, "Bad request line");
        }

        int pStart = skipSpaces(buf, mEnd + 1, lineEnd);
        int pEnd = indexOfByte(buf, pStart, lineEnd, (byte) ' ');
        if (pEnd < 0) {
            throw new VKHttpParseException(400, "Bad request line");
        }

        int vStart = skipSpaces(buf, pEnd + 1, lineEnd);
        if (vStart >= lineEnd) {
            throw new VKHttpParseException(400, "Bad request line");
        }

        String method = asciiString(buf, mStart, mEnd);
        String rawPath = asciiString(buf, pStart, pEnd);
        String version = asciiString(buf, vStart, lineEnd);

        String path = rawPath;
        String query = "";
        int q = rawPath.indexOf('?');
        if (q >= 0) {
            path = rawPath.substring(0, q);
            if (q + 1 < rawPath.length()) {
                query = rawPath.substring(q + 1);
            }
        }

        Map<String, String> headers = new HashMap<>();
        int contentLength = 0;
        boolean chunked = false;
        boolean expectContinue = false;

        int pos = lineEnd + 2;
        while (pos < headerEnd) {
            int end = indexOfCRLF(buf, len, pos);
            if (end < 0 || end > headerEnd) {
                throw new VKHttpParseException(400, "Bad headers");
            }
            if (end == pos) {
                break;
            }

            int colon = indexOfByte(buf, pos, end, (byte) ':');
            if (colon > pos) {
                int nameStart = pos;
                int nameEnd = trimRightAscii(buf, nameStart, colon);
                int valueStart = trimLeftAscii(buf, colon + 1, end);
                int valueEnd = trimRightAscii(buf, valueStart, end);

                String name = asciiLowerString(buf, nameStart, nameEnd);
                String value = asciiString(buf, valueStart, valueEnd);
                headers.put(name, value);

                if ("content-length".equals(name)) {
                    try {
                        contentLength = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new VKHttpParseException(400, "Invalid content-length");
                    }
                    if (contentLength < 0) {
                        throw new VKHttpParseException(400, "Invalid content-length");
                    }
                } else if ("transfer-encoding".equals(name)) {
                    chunked = containsIgnoreCase(value, "chunked");
                } else if ("expect".equals(name)) {
                    expectContinue = containsIgnoreCase(value, "100-continue");
                }
            }
            pos = end + 2;
        }

        boolean keepAlive = isKeepAlive(version, headers);
        return new HeaderInfo(method, path, query, version, headers, contentLength, headerEnd, keepAlive, chunked,
                expectContinue);
    }

    private ChunkResult parseChunked(byte[] buf, int len, int bodyStart) {
        int pos = bodyStart;
        ChunkAccumulator acc = new ChunkAccumulator();
        int total = 0;
        while (true) {
            int lineEnd = indexOfCRLF(buf, len, pos);
            if (lineEnd < 0) {
                acc.recycle();
                return null;
            }
            int semi = indexOfByte(buf, pos, lineEnd, (byte) ';');
            int sizeEnd = semi >= 0 ? semi : lineEnd;
            String hex = asciiString(buf, trimLeftAscii(buf, pos, sizeEnd), trimRightAscii(buf, pos, sizeEnd));
            if (hex.isEmpty()) {
                acc.recycle();
                throw new VKHttpParseException(400, "Invalid chunk size");
            }

            int chunkSize;
            try {
                chunkSize = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                acc.recycle();
                throw new VKHttpParseException(400, "Invalid chunk size");
            }
            if (chunkSize < 0) {
                acc.recycle();
                throw new VKHttpParseException(400, "Invalid chunk size");
            }

            pos = lineEnd + 2;
            if (chunkSize == 0) {
                int consumed = parseChunkTrailer(buf, len, pos);
                if (consumed < 0) {
                    acc.recycle();
                    return null;
                }
                return new ChunkResult(acc.toByteArrayAndRecycle(), consumed);
            }

            if (chunkSize > maxBodyBytes || total + chunkSize > maxBodyBytes) {
                acc.recycle();
                throw new VKHttpParseException(413, "Body too large");
            }
            if (len < pos + chunkSize + 2) {
                acc.recycle();
                return null;
            }
            acc.write(buf, pos, chunkSize);
            total += chunkSize;

            if (buf[pos + chunkSize] != '\r' || buf[pos + chunkSize + 1] != '\n') {
                acc.recycle();
                throw new VKHttpParseException(400, "Invalid chunk ending");
            }
            pos += chunkSize + 2;
        }
    }

    private int parseChunkTrailer(byte[] buf, int len, int pos) {
        if (len < pos + 2) {
            return -1;
        }
        if (buf[pos] == '\r' && buf[pos + 1] == '\n') {
            return pos + 2;
        }
        int cursor = pos;
        while (true) {
            int lineEnd = indexOfCRLF(buf, len, cursor);
            if (lineEnd < 0) {
                return -1;
            }
            if (lineEnd == cursor) {
                return cursor + 2;
            }
            cursor = lineEnd + 2;
        }
    }

    private boolean isKeepAlive(String version, Map<String, String> headers) {
        String conn = headers.get("connection");
        if ("HTTP/1.0".equalsIgnoreCase(version)) {
            return conn != null && conn.equalsIgnoreCase("keep-alive");
        }
        if (conn == null) {
            return true;
        }
        return !conn.equalsIgnoreCase("close");
    }

    private boolean containsIgnoreCase(String source, String target) {
        return source != null && source.toLowerCase().contains(target);
    }

    private int indexOfHeaderEnd(byte[] buf, int len, int start) {
        for (int i = start; i + 3 < len; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int indexOfCRLF(byte[] buf, int len, int start) {
        for (int i = start; i + 1 < len; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int indexOfByte(byte[] buf, int start, int end, byte b) {
        for (int i = start; i < end; i++) {
            if (buf[i] == b) {
                return i;
            }
        }
        return -1;
    }

    private int skipSpaces(byte[] buf, int start, int end) {
        int i = start;
        while (i < end && buf[i] == ' ') {
            i++;
        }
        return i;
    }

    private int trimLeftAscii(byte[] buf, int start, int end) {
        int i = start;
        while (i < end) {
            byte c = buf[i];
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private int trimRightAscii(byte[] buf, int start, int end) {
        int i = end;
        while (i > start) {
            byte c = buf[i - 1];
            if (c == ' ' || c == '\t') {
                i--;
            } else {
                break;
            }
        }
        return i;
    }

    private String asciiString(byte[] buf, int start, int end) {
        if (start >= end) {
            return "";
        }
        return new String(buf, start, end - start, StandardCharsets.US_ASCII);
    }

    private String asciiLowerString(byte[] buf, int start, int end) {
        if (start >= end) {
            return "";
        }
        byte[] out = new byte[end - start];
        for (int i = 0; i < out.length; i++) {
            byte c = buf[start + i];
            if (c >= 'A' && c <= 'Z') {
                out[i] = (byte) (c + 32);
            } else {
                out[i] = c;
            }
        }
        return new String(out, StandardCharsets.US_ASCII);
    }

    private static final class ChunkAccumulator {
        private static final int BLOCK_SIZE = 8 * 1024;
        private static final int MAX_BLOCK_POOL = 64;
        private static final ThreadLocal<java.util.ArrayDeque<byte[]>> BLOCK_POOL =
                ThreadLocal.withInitial(java.util.ArrayDeque::new);

        private final java.util.ArrayList<byte[]> blocks = new java.util.ArrayList<>();
        private byte[] current;
        private int currentPos;
        private int total;

        void write(byte[] src, int off, int len) {
            int p = off;
            int remain = len;
            while (remain > 0) {
                ensureBlock();
                int writable = Math.min(remain, BLOCK_SIZE - currentPos);
                System.arraycopy(src, p, current, currentPos, writable);
                currentPos += writable;
                total += writable;
                p += writable;
                remain -= writable;
            }
        }

        byte[] toByteArrayAndRecycle() {
            byte[] out = new byte[total];
            int copied = 0;
            int rest = total;
            for (int i = 0; i < blocks.size() && rest > 0; i++) {
                byte[] b = blocks.get(i);
                int n = Math.min(rest, BLOCK_SIZE);
                System.arraycopy(b, 0, out, copied, n);
                copied += n;
                rest -= n;
            }
            recycle();
            return out;
        }

        void recycle() {
            java.util.ArrayDeque<byte[]> pool = BLOCK_POOL.get();
            for (byte[] block : blocks) {
                if (pool.size() >= MAX_BLOCK_POOL) {
                    break;
                }
                pool.addLast(block);
            }
            blocks.clear();
            current = null;
            currentPos = 0;
            total = 0;
        }

        private void ensureBlock() {
            if (current != null && currentPos < BLOCK_SIZE) {
                return;
            }
            java.util.ArrayDeque<byte[]> pool = BLOCK_POOL.get();
            current = pool.pollFirst();
            if (current == null) {
                current = new byte[BLOCK_SIZE];
            }
            currentPos = 0;
            blocks.add(current);
        }
    }

    private record HeaderInfo(String method, String path, String query, String version,
                              Map<String, String> headers, int contentLength, int headerEnd,
                              boolean keepAlive, boolean chunked, boolean expectContinue) {
    }

    public static final class ParsedRequest {
        private final VKRequest request;
        private final int consumed;

        public ParsedRequest(VKRequest request, int consumed) {
            this.request = request;
            this.consumed = consumed;
        }

        public VKRequest request() {
            return request;
        }

        public int consumed() {
            return consumed;
        }
    }

    private record ChunkResult(byte[] body, int consumed) {
    }
}
