package yueyang.vostok.web.middleware;

import java.util.List;

/**
 * CORS（跨域资源共享）中间件配置类。
 *
 * 字段说明：
 * - allowOrigins：允许的 Origin 列表，包含 "*" 表示允许所有来源
 * - allowMethods：允许的 HTTP 方法（用于 preflight 响应）
 * - allowHeaders：允许的请求头（用于 preflight 响应）
 * - exposeHeaders：允许浏览器访问的响应头
 * - allowCredentials：是否允许携带凭证（Cookie、Authorization 等）
 * - maxAge：preflight 结果缓存时间（秒）
 */
public final class VKCorsConfig {
    private List<String> allowOrigins = List.of("*");
    private String allowMethods = "GET, POST, PUT, DELETE, OPTIONS, PATCH";
    private String allowHeaders = "Content-Type, Authorization, X-Trace-Id";
    private String exposeHeaders = "";
    private boolean allowCredentials = false;
    private int maxAge = 86400;

    public List<String> getAllowOrigins() {
        return allowOrigins;
    }

    public VKCorsConfig allowOrigins(List<String> allowOrigins) {
        this.allowOrigins = allowOrigins == null ? List.of("*") : List.copyOf(allowOrigins);
        return this;
    }

    public VKCorsConfig allowOrigins(String... origins) {
        this.allowOrigins = origins == null || origins.length == 0 ? List.of("*") : List.of(origins);
        return this;
    }

    public String getAllowMethods() {
        return allowMethods;
    }

    public VKCorsConfig allowMethods(String allowMethods) {
        this.allowMethods = allowMethods == null ? "" : allowMethods;
        return this;
    }

    public String getAllowHeaders() {
        return allowHeaders;
    }

    public VKCorsConfig allowHeaders(String allowHeaders) {
        this.allowHeaders = allowHeaders == null ? "" : allowHeaders;
        return this;
    }

    public String getExposeHeaders() {
        return exposeHeaders;
    }

    public VKCorsConfig exposeHeaders(String exposeHeaders) {
        this.exposeHeaders = exposeHeaders == null ? "" : exposeHeaders;
        return this;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public VKCorsConfig allowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
        return this;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public VKCorsConfig maxAge(int maxAge) {
        this.maxAge = Math.max(0, maxAge);
        return this;
    }
}
