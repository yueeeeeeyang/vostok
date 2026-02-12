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

    public ParsedRequest parse(byte[] buf, int len, InetSocketAddress remote) {
        int headerEnd = indexOfHeaderEnd(buf, len);
        if (headerEnd < 0) {
            if (len > maxHeaderBytes) {
                throw new VKHttpParseException(431, "Header too large");
            }
            return null;
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

        int contentLength = 0;
        String cl = headers.get("content-length");
        if (cl != null && !cl.isEmpty()) {
            try {
                contentLength = Integer.parseInt(cl);
            } catch (NumberFormatException e) {
                throw new VKHttpParseException(400, "Invalid content-length");
            }
        }

        if (contentLength > maxBodyBytes) {
            throw new VKHttpParseException(413, "Body too large");
        }

        int totalLen = headerEnd + 4 + contentLength;
        if (len < totalLen) {
            return null;
        }

        byte[] body = new byte[contentLength];
        if (contentLength > 0) {
            System.arraycopy(buf, headerEnd + 4, body, 0, contentLength);
        }

        boolean keepAlive = isKeepAlive(version, headers);

        VKRequest req = new VKRequest(method, path, query, version, headers, body, keepAlive, remote);
        return new ParsedRequest(req, totalLen);
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
}
