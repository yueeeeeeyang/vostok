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

    /**
     * 为 SSE 响应写出 HTTP 头部（不含 Content-Length，因为 SSE 是无界流）。
     * 连接保持 keep-alive，响应头写完后连接切换为 SSE 协议。
     *
     * @param res       SSE 响应对象（已设置 Content-Type 等必要头）
     * @param keepAlive 是否保持连接（SSE 通常为 true）
     * @return HTTP 头部字节数组，以空行结尾
     */
    public static byte[] writeSseHead(VKResponse res, boolean keepAlive) {
        int status = res.status();
        String reason = reason(status);

        StringBuilder sb = new StringBuilder(200);
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");

        boolean hasConnection = false;
        for (Map.Entry<String, String> e : res.headers().entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (name == null || value == null) {
                continue;
            }
            // SSE 不写 Content-Length，保持连接为流模式
            if ("content-length".equalsIgnoreCase(name)) {
                continue;
            }
            if ("connection".equalsIgnoreCase(name)) {
                hasConnection = true;
            }
            sb.append(name).append(": ").append(value).append("\r\n");
        }
        if (!hasConnection) {
            sb.append("Connection: keep-alive\r\n");
        }
        sb.append("\r\n");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 206 -> "Partial Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 431 -> "Request Header Fields Too Large";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Unknown";
        };
    }
}
