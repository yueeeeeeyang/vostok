package yueyang.vostok.web.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.log.VKLogConfig;
import yueyang.vostok.log.VKLogSinkConfig;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.rate.VKRateLimiter;
import yueyang.vostok.web.route.VKRouteMatch;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

final class VKWebLogSupport {
    private static final String ACCESS_LOGGER = "web-access";
    private static final String RATELIMIT_LOGGER = "web-ratelimit";
    private static final AtomicBoolean REGISTER_WARNED = new AtomicBoolean(false);

    private VKWebLogSupport() {
    }

    static void ensureLoggersReady() {
        if (!Vostok.Log.initialized()) {
            VKLogConfig cfg = VKLogConfig.defaults()
                    .registerLogger(ACCESS_LOGGER, new VKLogSinkConfig().filePrefix("access"))
                    .registerLogger(RATELIMIT_LOGGER, new VKLogSinkConfig().filePrefix("ratelimit"));
            Vostok.Log.init(cfg);
            return;
        }
        tryRegisterLogger(ACCESS_LOGGER, "access");
        tryRegisterLogger(RATELIMIT_LOGGER, "ratelimit");
    }

    static void accessInfo(String msg) {
        try {
            Vostok.Log.logger(ACCESS_LOGGER).info(msg);
        } catch (Throwable e) {
            Vostok.Log.info(msg);
        }
    }

    static void accessWarn(String msg) {
        try {
            Vostok.Log.logger(ACCESS_LOGGER).warn(msg);
        } catch (Throwable e) {
            Vostok.Log.warn(msg);
        }
    }

    static void logRateLimit(VKRequest req, VKRouteMatch match, String scope, VKRateLimiter.Decision decision) {
        String method = req == null ? "-" : req.method();
        String path = req == null ? "-" : req.path();
        String route = match == null || match.routePattern() == null ? "-" : match.routePattern();
        String traceId = req == null || req.traceId() == null ? "-" : req.traceId();
        String ip = "-";
        if (req != null && req.remoteAddress() != null && req.remoteAddress().getAddress() != null) {
            ip = req.remoteAddress().getAddress().getHostAddress();
        }
        String key = decision == null || decision.key() == null ? "-" : decision.key();
        if (key.length() > 128) {
            key = key.substring(0, 128);
        }
        String strategy = decision == null || decision.strategy() == null ? "-" : decision.strategy().name();
        int status = decision == null ? 429 : decision.rejectStatus();
        String line = "scope=" + scope + " method=" + method + " path=" + path + " route=" + route
                + " traceId=" + traceId + " ip=" + ip + " strategy=" + strategy + " key=" + key
                + " status=" + status + " reason=token_exhausted";
        try {
            Vostok.Log.logger(RATELIMIT_LOGGER).warn(line);
        } catch (Throwable e) {
            Vostok.Log.warn("[ratelimit] " + line);
        }
    }

    private static void tryRegisterLogger(String loggerName, String filePrefix) {
        try {
            Method m = Vostok.Log.class.getMethod("registerLogger", String.class, VKLogSinkConfig.class);
            m.invoke(null, loggerName, new VKLogSinkConfig().filePrefix(filePrefix));
        } catch (NoSuchMethodException e) {
            warnRegisterMissing();
        } catch (Throwable e) {
            warnRegisterMissing();
        }
    }

    private static void warnRegisterMissing() {
        if (REGISTER_WARNED.compareAndSet(false, true)) {
            Vostok.Log.warn("Vostok.Log.registerLogger(...) not found, web logs fallback to default logger");
        }
    }
}
