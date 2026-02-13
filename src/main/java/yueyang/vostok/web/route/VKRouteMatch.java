package yueyang.vostok.web.route;

import yueyang.vostok.web.VKHandler;

import java.util.Map;

public final class VKRouteMatch {
    private final VKHandler handler;
    private final Map<String, String> params;

    public VKRouteMatch(VKHandler handler, Map<String, String> params) {
        this.handler = handler;
        this.params = params;
    }

    public VKHandler handler() {
        return handler;
    }

    public Map<String, String> params() {
        return params;
    }
}
