package yueyang.vostok.web.middleware;

import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

import java.util.List;

public final class VKChain {
    private final List<VKMiddleware> middlewares;
    private final VKHandler handler;
    private int index = 0;

    public VKChain(List<VKMiddleware> middlewares, VKHandler handler) {
        this.middlewares = middlewares;
        this.handler = handler;
    }

    public void next(VKRequest req, VKResponse res) {
        if (index < middlewares.size()) {
            VKMiddleware mw = middlewares.get(index++);
            mw.handle(req, res, this);
            return;
        }
        handler.handle(req, res);
    }
}
