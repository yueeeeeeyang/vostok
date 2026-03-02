package yueyang.vostok.http;

/**
 * HTTP 拦截器链接口。
 * 每个拦截器持有一个链引用，通过调用 {@link #proceed()} 或 {@link #proceed(VKHttpRequest)} 将控制权传递给下一个拦截器或实际执行。
 */
public interface VKHttpInterceptorChain {

    /**
     * 返回当前拦截器链中的请求对象（可能已被上游拦截器修改）。
     *
     * @return 当前 HTTP 请求
     */
    VKHttpRequest request();

    /**
     * 使用当前请求继续执行拦截器链（直到实际 HTTP 调用）。
     *
     * @return HTTP 响应
     */
    VKHttpResponse proceed();

    /**
     * 使用新的请求继续执行拦截器链。
     * 允许拦截器修改请求（如添加 Header、更改 URL 等）后再传递给下游。
     *
     * @param request 替换后的新 HTTP 请求
     * @return HTTP 响应
     */
    VKHttpResponse proceed(VKHttpRequest request);
}
