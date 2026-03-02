package yueyang.vostok.web.middleware;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip 压缩响应中间件。
 *
 * 工作流程（post-process 模式）：
 * 1. 先调用 chain.next() 让后续中间件和 handler 处理请求
 * 2. 检查响应是否满足压缩条件（客户端支持 gzip、Content-Type 可压缩、body 超过阈值）
 * 3. 满足条件时压缩 body，并添加 Content-Encoding: gzip 和 Vary: Accept-Encoding 头
 *
 * 以下响应不压缩：SSE 流、文件响应。
 */
public final class VKGzipMiddleware implements VKMiddleware {
    private final VKGzipConfig config;

    public VKGzipMiddleware() {
        this(new VKGzipConfig());
    }

    public VKGzipMiddleware(VKGzipConfig config) {
        this.config = config == null ? new VKGzipConfig() : config;
    }

    @Override
    public void handle(VKRequest req, VKResponse res, VKChain chain) {
        // 先执行后续链路，再决定是否压缩
        chain.next(req, res);

        // 检查客户端是否接受 gzip
        String ae = req.header("accept-encoding");
        if (ae == null || !ae.contains("gzip")) {
            return;
        }

        // SSE 和文件响应不压缩
        if (res.isSse() || res.isFile()) {
            return;
        }

        // 检查 Content-Type 是否可压缩
        String ct = res.headers().get("Content-Type");
        if (!isCompressible(ct)) {
            return;
        }

        // 响应体超过阈值才压缩
        byte[] body = res.body();
        if (body == null || body.length < config.getMinBytes()) {
            return;
        }

        // 执行 gzip 压缩
        try {
            byte[] compressed = gzip(body);
            res.body(compressed)
               .header("Content-Encoding", "gzip")
               .header("Vary", "Accept-Encoding");
        } catch (Exception ignore) {
            // 压缩失败时发送原始未压缩内容
        }
    }

    /**
     * 判断 Content-Type 是否在可压缩列表中（前缀或包含匹配）。
     */
    private boolean isCompressible(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        for (String type : config.getCompressibleTypes()) {
            if (lower.contains(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对 data 进行 gzip 压缩，返回压缩后的字节数组。
     */
    private byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2 + 64);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }
}
