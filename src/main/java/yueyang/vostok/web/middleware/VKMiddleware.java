package yueyang.vostok.web.middleware;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

@FunctionalInterface
public interface VKMiddleware {
    void handle(VKRequest req, VKResponse res, VKChain chain);
}
