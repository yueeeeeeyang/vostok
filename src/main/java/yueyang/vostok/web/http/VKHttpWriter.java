package yueyang.vostok.web.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class VKHttpWriter {
    private VKHttpWriter() {
    }

    public static byte[] writeContinue() {
        return "HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] write(VKResponse res, boolean keepAlive) {
        byte[] head = writeHead(res, keepAlive);
        byte[] body = res.body();
        int bodyLen = body == null ? 0 : body.length;

        byte[] out = new byte[head.length + bodyLen];
        System.arraycopy(head, 0, out, 0, head.length);
        if (bodyLen > 0) {
            System.arraycopy(body, 0, out, head.length, bodyLen);
        }
        return out;
    }

    public static byte[] writeHead(VKResponse res, boolean keepAlive) {
        int status = res.status();
        String reason = reason(status);
        long contentLength = resolveContentLength(res);

        StringBuilder sb = new StringBuilder(160);
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");

        boolean hasContentLength = false;
        boolean hasConnection = false;

        for (Map.Entry<String, String> e : res.headers().entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name == null || value == null) {
                continue;
            }
            String lower = name.toLowerCase();
            if ("content-length".equals(lower)) {
                hasContentLength = true;
            } else if ("connection".equals(lower)) {
                hasConnection = true;
            }
            sb.append(name).append(": ").append(value).append("\r\n");
        }
        for (String setCookie : res.setCookies()) {
            if (setCookie != null && !setCookie.isEmpty()) {
                sb.append("Set-Cookie: ").append(setCookie).append("\r\n");
            }
        }

        if (!hasContentLength) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }
        if (!hasConnection) {
            sb.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        }

        sb.append("\r\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static long resolveContentLength(VKResponse res) {
        if (res.isFile()) {
            return Math.max(0, res.fileLength());
        }
        byte[] body = res.body();
        return body == null ? 0 : body.length;
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 413 -> "Payload Too Large";
            case 429 -> "Too Many Requests";
            case 431 -> "Request Header Fields Too Large";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "OK";
        };
    }
}
