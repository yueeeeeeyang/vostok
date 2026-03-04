package yueyang.vostok.webmvc;

import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKGet;

@VKApi("/scan")
public class ScanDemoApi {
    @VKGet("/ping")
    public String ping() {
        return "pong";
    }
}
