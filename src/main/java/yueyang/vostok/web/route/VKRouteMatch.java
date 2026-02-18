package yueyang.vostok.web.route;

import yueyang.vostok.web.VKHandler;

import java.util.Map;

public final class VKRouteMatch {
    private final VKHandler handler;
    private final Map<String, String> params;
    private final String routePattern;

    public VKRouteMatch(VKHandler handler, Map<String, String> params, String routePattern) {
        this.handler = handler;
        this.params = params;
        this.routePattern = routePattern;
    }

    public VKHandler handler() {
        return handler;
    }

    public Map<String, String> params() {
        return params;
    }

    public String routePattern() {
        return routePattern;
    }
}
