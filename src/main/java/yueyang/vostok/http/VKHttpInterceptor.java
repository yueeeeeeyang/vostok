package yueyang.vostok.http;

/**
 * HTTP 请求/响应拦截器接口。
 * 拦截器可以在请求发出前修改请求，也可以在响应返回后包装响应。
 * <p>
 * 执行顺序：全局拦截器（VKHttpConfig）→ 客户端拦截器（VKHttpClientConfig）→ 实际 HTTP 执行（含重试）。
 * <p>
 * 使用示例：
 * <pre>
 *   VostokHttp.addInterceptor(chain -> {
 *       VKHttpRequest req = chain.request().toBuilder().header("X-Trace", UUID.randomUUID().toString()).build();
 *       return chain.proceed(req);
 *   });
 * </pre>
 */
public interface VKHttpInterceptor {

    /**
     * 拦截 HTTP 调用。
     * 实现者必须调用 {@link VKHttpInterceptorChain#proceed()} 或
     * {@link VKHttpInterceptorChain#proceed(VKHttpRequest)} 继续链式执行，否则请求不会发送。
     *
     * @param chain 拦截器链，持有当前请求并提供 proceed() 方法
     * @return HTTP 响应（可以是原始响应，也可以是拦截器包装后的响应）
     */
    VKHttpResponse intercept(VKHttpInterceptorChain chain);
}
