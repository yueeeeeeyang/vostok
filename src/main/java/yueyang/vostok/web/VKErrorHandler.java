package yueyang.vostok.web;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

@FunctionalInterface
public interface VKErrorHandler {
    void handle(Throwable error, VKRequest req, VKResponse res);
}
