package yueyang.vostok.web.spi;

import yueyang.vostok.web.VKErrorHandler;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.http.VKMultipartParseException;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.middleware.VKChain;
import yueyang.vostok.web.route.VKRouteMatch;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 共享 HTTP 执行链。
 *
 * 这里聚合与具体网络实现无关的逻辑：traceId、路由匹配、中间件、限流、错误处理和上传清理。
 * 自定义引擎只要构造 VKRequest 并调用 dispatcher，即可获得与内建引擎一致的 Web 语义。
 */
public final class VKWebHttpDispatcher {
    private static final AtomicLong TRACE_SEQ = new AtomicLong();

    private final VKWebRuntimeSupport runtime;

    public VKWebHttpDispatcher(VKWebRuntimeSupport runtime) {
        this.runtime = runtime;
    }

    public VKWebDispatchResult dispatch(VKRequest req) {
        return dispatch(req, new VKResponse());
    }

    public VKWebDispatchResult dispatch(VKRequest req, VKResponse res) {
        long startNs = System.nanoTime();
        boolean error = false;
        try {
            ensureTraceId(req, res);
            VKRouteMatch match = runtime.router().match(req.method(), req.path());
            VKHandler finalHandler;
            if (match == null || match.handler() == null) {
                finalHandler = (r, s) -> s.status(404).text("Not Found");
            } else {
                req.setParams(match.params());
                if (!runtime.tryRateLimit(req, match, res)) {
                    return finish(req, res, startNs, false);
                }
                finalHandler = match.handler();
            }

            List<yueyang.vostok.web.middleware.VKMiddleware> middlewares = runtime.middlewares();
            if (middlewares.isEmpty()) {
                finalHandler.handle(req, res);
            } else {
                new VKChain(middlewares, finalHandler).next(req, res);
            }
        } catch (VKMultipartParseException e) {
            res.status(e.status()).text(e.getMessage());
            error = true;
        } catch (Throwable t) {
            error = true;
            handleError(runtime.errorHandler(), t, req, res);
        }
        return finish(req, res, startNs, error);
    }

    /**
     * 统一生成并回填 traceId，供普通 HTTP 与自定义引擎复用。
     */
    public static void ensureTraceId(VKRequest req, VKResponse res) {
        String tid = req.traceId();
        if (tid == null || tid.isEmpty()) {
            tid = req.header("x-trace-id");
        }
        if (tid == null || tid.isEmpty()) {
            tid = Long.toHexString(System.nanoTime()) + "-" + TRACE_SEQ.incrementAndGet();
        }
        req.setTraceId(tid);
        if (res.headers().get("X-Trace-Id") == null) {
            res.header("X-Trace-Id", tid);
        }
    }

    private VKWebDispatchResult finish(VKRequest req, VKResponse res, long startNs, boolean error) {
        if (res.headers().get("X-Trace-Id") == null && req.traceId() != null) {
            res.header("X-Trace-Id", req.traceId());
        }
        req.cleanupUploads();
        long costNs = System.nanoTime() - startNs;
        return new VKWebDispatchResult(res, costNs, error || res.status() >= 500);
    }

    private void handleError(VKErrorHandler errorHandler, Throwable error, VKRequest req, VKResponse res) {
        try {
            errorHandler.handle(error, req, res);
        } catch (Throwable ignore) {
            res.status(500).text("Internal Server Error");
        }
    }
}
