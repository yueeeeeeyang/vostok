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
            ChunkResult cr = parseChunked(buf, len, bodyStart);
            if (cr == null) {
                return null;
            }
            VKRequest req = new VKRequest(info.method, info.path, info.query, info.version, info.headers, cr.body, info.keepAlive, remote);
            return new ParsedRequest(req, cr.consumed);
        }

        int contentLength = info.contentLength;
        if (contentLength > maxBodyBytes) {
            throw new VKHttpParseException(413, "Body too large");
        }
        int totalLen = bodyStart + contentLength;
        if (len < totalLen) {
            return null;
        }
        byte[] body = new byte[contentLength];
        if (contentLength > 0) {
            System.arraycopy(buf, bodyStart, body, 0, contentLength);
        }
        VKRequest req = new VKRequest(info.method, info.path, info.query, info.version, info.headers, body, info.keepAlive, remote);
        return new ParsedRequest(req, totalLen);
    }

    public boolean shouldSendContinue(byte[] buf, int len) {
        HeaderInfo info = parseHeaders(buf, len);
        if (info == null) {
            return false;
        }
        if (!info.expectContinue) {
            return false;
        }
        if (info.contentLength > 0 || info.chunked) {
            return true;
        }
        return false;
    }

    private HeaderInfo parseHeaders(byte[] buf, int len) {
        int headerEnd = indexOfHeaderEnd(buf, len);
        if (headerEnd < 0) {
            return null;
        }
        if (headerEnd > maxHeaderBytes) {
            throw new VKHttpParseException(431, "Header too large");
        }
        String headerText = new String(buf, 0, headerEnd, StandardCharsets.US_ASCII);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0) {
            throw new VKHttpParseException(400, "Bad request");
        }
        String[] start = lines[0].split(" ");
        if (start.length < 3) {
            throw new VKHttpParseException(400, "Bad request line");
        }
        String method = start[0].trim();
        String rawPath = start[1].trim();
        String version = start[2].trim();

        String path = rawPath;
        String query = "";
        int qIdx = rawPath.indexOf('?');
        if (qIdx >= 0) {
            path = rawPath.substring(0, qIdx);
            if (qIdx + 1 < rawPath.length()) {
                query = rawPath.substring(qIdx + 1);
            }
        }

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String name = line.substring(0, idx).trim().toLowerCase();
            String value = line.substring(idx + 1).trim();
            headers.put(name, value);
        }

        boolean chunked = false;
        String te = headers.get("transfer-encoding");
        if (te != null && te.toLowerCase().contains("chunked")) {
            chunked = true;
        }

        int contentLength = 0;
        String cl = headers.get("content-length");
        if (cl != null && !cl.isEmpty()) {
            try {
                contentLength = Integer.parseInt(cl);
            } catch (NumberFormatException e) {
                throw new VKHttpParseException(400, "Invalid content-length");
            }
        }

        boolean keepAlive = isKeepAlive(version, headers);
        boolean expectContinue = false;
        String expect = headers.get("expect");
        if (expect != null && expect.toLowerCase().contains("100-continue")) {
            expectContinue = true;
        }

        return new HeaderInfo(method, path, query, version, headers, contentLength, headerEnd, keepAlive, chunked, expectContinue);
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

    private int indexOfHeaderEnd(byte[] buf, int len) {
        for (int i = 0; i + 3 < len; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private ChunkResult parseChunked(byte[] buf, int len, int bodyStart) {
        int pos = bodyStart;
        int total = 0;
        byte[] out = new byte[0];
        while (true) {
            int lineEnd = indexOfLineEnd(buf, len, pos);
            if (lineEnd < 0) {
                return null;
            }
            String line = new String(buf, pos, lineEnd - pos, StandardCharsets.US_ASCII).trim();
            int sem = line.indexOf(';');
            if (sem >= 0) {
                line = line.substring(0, sem);
            }
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(line.trim(), 16);
            } catch (NumberFormatException e) {
                throw new VKHttpParseException(400, "Invalid chunk size");
            }
            pos = lineEnd + 2; // skip CRLF
            if (chunkSize == 0) {
                // need trailing CRLF
                if (len < pos + 2) {
                    return null;
                }
                pos += 2;
                return new ChunkResult(out, pos);
            }
            if (chunkSize > maxBodyBytes || total + chunkSize > maxBodyBytes) {
                throw new VKHttpParseException(413, "Body too large");
            }
            if (len < pos + chunkSize + 2) {
                return null;
            }
            byte[] newOut = new byte[total + chunkSize];
            System.arraycopy(out, 0, newOut, 0, total);
            System.arraycopy(buf, pos, newOut, total, chunkSize);
            out = newOut;
            total += chunkSize;
            pos += chunkSize + 2; // skip data + CRLF
        }
    }

    private int indexOfLineEnd(byte[] buf, int len, int start) {
        for (int i = start; i + 1 < len; i++) {
            if (buf[i] == '\r' && buf[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
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
