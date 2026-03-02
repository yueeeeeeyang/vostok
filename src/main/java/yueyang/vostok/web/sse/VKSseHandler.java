package yueyang.vostok.web.sse;

import yueyang.vostok.web.http.VKRequest;

/**
 * Server-Sent Events 处理器接口。
 *
 * 用于注册 SSE 端点：
 * <pre>
 *   VostokWeb.sse("/events", (req, emitter) -> {
 *       emitters.add(emitter);
 *       emitter.send("connected");
 *   });
 * </pre>
 *
 * handle 方法在 worker 线程中执行。emitter 可以保存到外部集合，
 * 在任意线程中调用 emitter.send() 向客户端推送事件，调用 emitter.close() 关闭连接。
 */
@FunctionalInterface
public interface VKSseHandler {
    /**
     * 处理 SSE 连接建立。
     *
     * @param req     HTTP 请求对象，包含路径参数、查询参数、请求头等
     * @param emitter SSE 发射器，用于向客户端推送事件和关闭连接
     */
    void handle(VKRequest req, VKSseEmitter emitter);
}
