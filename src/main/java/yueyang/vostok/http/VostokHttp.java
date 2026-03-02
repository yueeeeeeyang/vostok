package yueyang.vostok.http;

import yueyang.vostok.http.core.VKHttpRuntime;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;


public class VostokHttp {
    private static final VKHttpRuntime RUNTIME = VKHttpRuntime.getInstance();

    protected VostokHttp() {
    }

    public static void init() {
        RUNTIME.init(new VKHttpConfig());
    }

    public static void init(VKHttpConfig config) {
        RUNTIME.init(config);
    }

    public static void reinit(VKHttpConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKHttpConfig config() {
        return RUNTIME.config();
    }

    public static void close() {
        RUNTIME.close();
    }

    public static void registerClient(String name, VKHttpClientConfig config) {
        RUNTIME.registerClient(name, config);
    }

    public static void withClient(String name, Runnable action) {
        RUNTIME.withClient(name, action);
    }

    public static <T> T withClient(String name, Supplier<T> supplier) {
        return RUNTIME.withClient(name, supplier);
    }

    public static Set<String> clientNames() {
        return RUNTIME.clientNames();
    }

    public static String currentClientName() {
        return RUNTIME.currentClientName();
    }

    public static VKHttpRequestBuilder request() {
        return new VKHttpRequestBuilder();
    }

    public static VKHttpRequestBuilder get(String urlOrPath) {
        return request().get(urlOrPath);
    }

    public static VKHttpRequestBuilder post(String urlOrPath) {
        return request().post(urlOrPath);
    }

    public static VKHttpRequestBuilder put(String urlOrPath) {
        return request().put(urlOrPath);
    }

    public static VKHttpRequestBuilder patch(String urlOrPath) {
        return request().patch(urlOrPath);
    }

    public static VKHttpRequestBuilder delete(String urlOrPath) {
        return request().delete(urlOrPath);
    }

    public static VKHttpRequestBuilder head(String urlOrPath) {
        return request().head(urlOrPath);
    }

    public static VKHttpResponse execute(VKHttpRequest request) {
        return RUNTIME.execute(request);
    }

    public static <T> T executeJson(VKHttpRequest request, Class<T> type) {
        return RUNTIME.executeJson(request, type);
    }

    public static CompletableFuture<VKHttpResponse> executeAsync(VKHttpRequest request) {
        return RUNTIME.executeAsync(request);
    }

    public static <T> CompletableFuture<T> executeJsonAsync(VKHttpRequest request, Class<T> type) {
        return RUNTIME.executeJsonAsync(request, type);
    }

    public static VKHttpStreamSession openSse(VKHttpRequest request, VKHttpSseListener listener) {
        return RUNTIME.openSse(request, listener);
    }

    public static void executeSse(VKHttpRequest request, VKHttpSseListener listener) {
        RUNTIME.executeSse(request, listener);
    }

    public static CompletableFuture<Void> executeSseAsync(VKHttpRequest request, VKHttpSseListener listener) {
        return RUNTIME.executeSseAsync(request, listener);
    }

    public static VKHttpStreamSession openStream(VKHttpRequest request, VKHttpChunkListener listener) {
        return RUNTIME.openStream(request, listener);
    }

    public static void executeStream(VKHttpRequest request, VKHttpChunkListener listener) {
        RUNTIME.executeStream(request, listener);
    }

    public static CompletableFuture<Void> executeStreamAsync(VKHttpRequest request, VKHttpChunkListener listener) {
        return RUNTIME.executeStreamAsync(request, listener);
    }

    public static VKHttpMetrics metrics() {
        return RUNTIME.metrics();
    }

    /** 扩展5：返回指定命名客户端的独立 Metrics 快照。 */
    public static VKHttpMetrics metrics(String clientName) {
        return RUNTIME.metrics(clientName);
    }

    public static void resetMetrics() {
        RUNTIME.resetMetrics();
    }

    public static String urlEncode(String value) {
        return VKHttpRuntime.urlEncode(value);
    }

    // -----------------------------------------------------------------------
    // 扩展1：拦截器管理
    // -----------------------------------------------------------------------

    /** 添加全局拦截器（对所有客户端生效）。 */
    public static void addInterceptor(VKHttpInterceptor interceptor) {
        RUNTIME.addInterceptor(interceptor);
    }

    /** 添加客户端级拦截器（仅对指定命名客户端生效）。 */
    public static void addInterceptor(String client, VKHttpInterceptor interceptor) {
        RUNTIME.addInterceptor(client, interceptor);
    }

    // -----------------------------------------------------------------------
    // 扩展4：WebSocket 支持
    // -----------------------------------------------------------------------

    /** 建立 WebSocket 连接，返回会话对象。连接建立过程阻塞直到完成或超时。 */
    public static VKHttpWebSocketSession websocket(VKHttpRequest request, VKHttpWebSocketListener listener) {
        return RUNTIME.websocket(request, listener);
    }
}
