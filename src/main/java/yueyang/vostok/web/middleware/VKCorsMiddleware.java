package yueyang.vostok.web.middleware;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.util.List;

/**
 * CORS（跨域资源共享）中间件。
 *
 * 处理流程：
 * 1. 无 Origin 头 → 非跨域请求，直接透传到后续链路
 * 2. OPTIONS 方法（preflight）→ 添加 CORS 头后返回 204，不调用后续链路
 * 3. 其他方法 → 添加 CORS 头后继续调用后续链路
 *
 * 注意：allowOrigins 包含 "*" 时，返回 "*"；
 * 否则检查请求 Origin 是否在白名单中，在则返回该 Origin，不在则返回白名单第一项。
 */
public final class VKCorsMiddleware implements VKMiddleware {
    private final VKCorsConfig config;

    public VKCorsMiddleware() {
        this(new VKCorsConfig());
    }

    public VKCorsMiddleware(VKCorsConfig config) {
        this.config = config == null ? new VKCorsConfig() : config;
    }

    @Override
    public void handle(VKRequest req, VKResponse res, VKChain chain) {
        String origin = req.header("origin");
        // 无 Origin 头表示非跨域请求，直接透传
        if (origin == null) {
            chain.next(req, res);
            return;
        }

        // 添加 CORS 响应头
        applyHeaders(res, origin);

        // OPTIONS preflight 短路返回，不调用后续 handler
        if ("OPTIONS".equalsIgnoreCase(req.method())) {
            res.status(204);
            return;
        }

        // 普通跨域请求继续处理
        chain.next(req, res);
    }

    /**
     * 向响应添加所有 CORS 相关头部。
     */
    private void applyHeaders(VKResponse res, String origin) {
        String allowedOrigin = resolveOrigin(origin);
        res.header("Access-Control-Allow-Origin", allowedOrigin);

        if (config.isAllowCredentials()) {
            res.header("Access-Control-Allow-Credentials", "true");
        }

        String methods = config.getAllowMethods();
        if (methods != null && !methods.isEmpty()) {
            res.header("Access-Control-Allow-Methods", methods);
        }

        String headers = config.getAllowHeaders();
        if (headers != null && !headers.isEmpty()) {
            res.header("Access-Control-Allow-Headers", headers);
        }

        if (config.getMaxAge() > 0) {
            res.header("Access-Control-Max-Age", String.valueOf(config.getMaxAge()));
        }

        String expose = config.getExposeHeaders();
        if (expose != null && !expose.isEmpty()) {
            res.header("Access-Control-Expose-Headers", expose);
        }
    }

    /**
     * 根据配置的 allowOrigins 决定返回哪个 Access-Control-Allow-Origin 值。
     * - 含 "*" 则直接返回 "*"
     * - 请求 Origin 在白名单则返回该 Origin（用于支持凭证模式）
     * - 否则返回白名单第一项（作为兜底）
     */
    private String resolveOrigin(String requestOrigin) {
        List<String> allowed = config.getAllowOrigins();
        if (allowed.contains("*")) {
            return "*";
        }
        if (requestOrigin != null && allowed.contains(requestOrigin)) {
            return requestOrigin;
        }
        return allowed.isEmpty() ? "*" : allowed.get(0);
    }
}
