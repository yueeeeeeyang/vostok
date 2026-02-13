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
        int status = res.status();
        String reason = reason(status);
        byte[] body = res.body();
        int contentLength = body == null ? 0 : body.length;

        StringBuilder sb = new StringBuilder(128);
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

        if (!hasContentLength) {
            sb.append("Content-Length: ").append(contentLength).append("\r\n");
        }

        if (!hasConnection) {
            sb.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        }

        sb.append("\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.US_ASCII);

        byte[] out = new byte[head.length + contentLength];
        System.arraycopy(head, 0, out, 0, head.length);
        if (contentLength > 0) {
            System.arraycopy(body, 0, out, head.length, contentLength);
        }
        return out;
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 413 -> "Payload Too Large";
            case 431 -> "Request Header Fields Too Large";
            case 503 -> "Service Unavailable";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }
}
