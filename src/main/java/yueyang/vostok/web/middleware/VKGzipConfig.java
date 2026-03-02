package yueyang.vostok.web.middleware;

import java.util.List;

/**
 * Gzip 压缩中间件配置类。
 *
 * 控制哪些响应会被压缩：
 * - minBytes：响应体最小字节数（默认 256），低于此阈值不压缩
 * - compressibleTypes：可压缩的 Content-Type 前缀列表
 */
public final class VKGzipConfig {
    private int minBytes = 256;
    private List<String> compressibleTypes = List.of("text/", "application/json", "application/xml");

    /**
     * 获取触发压缩的最小字节数阈值。
     */
    public int getMinBytes() {
        return minBytes;
    }

    /**
     * 设置触发压缩的最小字节数阈值，必须 >= 0。
     */
    public VKGzipConfig minBytes(int minBytes) {
        this.minBytes = Math.max(0, minBytes);
        return this;
    }

    /**
     * 获取可压缩的 Content-Type 类型列表（匹配前缀或包含）。
     */
    public List<String> getCompressibleTypes() {
        return compressibleTypes;
    }

    /**
     * 设置可压缩的 Content-Type 类型列表。
     */
    public VKGzipConfig compressibleTypes(List<String> types) {
        this.compressibleTypes = types == null ? List.of() : List.copyOf(types);
        return this;
    }
}
