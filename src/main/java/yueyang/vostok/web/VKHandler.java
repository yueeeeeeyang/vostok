package yueyang.vostok.web;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

@FunctionalInterface
public interface VKHandler {
    void handle(VKRequest req, VKResponse res);
}
