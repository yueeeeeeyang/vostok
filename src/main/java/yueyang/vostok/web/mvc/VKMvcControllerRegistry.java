package yueyang.vostok.web.mvc;

import yueyang.vostok.Vostok;
import yueyang.vostok.web.VKHandler;
import yueyang.vostok.web.core.VKWebServer;
import yueyang.vostok.web.mvc.annotation.VKApi;
import yueyang.vostok.web.mvc.annotation.VKDelete;
import yueyang.vostok.web.mvc.annotation.VKGet;
import yueyang.vostok.web.mvc.annotation.VKPatch;
import yueyang.vostok.web.mvc.annotation.VKPost;
import yueyang.vostok.web.mvc.annotation.VKPut;
import yueyang.vostok.util.scan.VKScanner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 控制器注册器：扫描 @VKApi 并注册到现有路由系统。
 */
public final class VKMvcControllerRegistry {
    private final VKWebServer server;
    private final VKMvcConfig config;
    private final VKMvcTypeConverterRegistry converters;
    private final Set<String> routeKeys = new HashSet<>();

    public VKMvcControllerRegistry(VKWebServer server, VKMvcConfig config) {
        this.server = server;
        this.config = config == null ? VKMvcConfig.defaults() : config.copy();
        this.converters = new VKMvcTypeConverterRegistry();
    }

    public void registerController(Object controller) {
        if (controller == null) {
            onLoadError("Controller is null", null);
            return;
        }
        Class<?> type = controller.getClass();
        VKApi api = type.getAnnotation(VKApi.class);
        if (api == null) {
            onLoadError("Controller is missing @VKApi: " + type.getName(), null);
            return;
        }

        String prefix = normalizePath(api.value(), true);
        Method[] methods = type.getDeclaredMethods();
        for (Method method : methods) {
            HttpRoute route = resolveHttpRoute(method);
            if (route == null) {
                continue;
            }
            try {
                String fullPath = joinPath(prefix, route.path());
                String key = route.method() + " " + fullPath;
                if (!routeKeys.add(key)) {
                    throw new IllegalArgumentException("Duplicate MVC route in controller registry: " + key);
                }

                VKMvcMethodInvoker invoker = new VKMvcMethodInvoker(controller, method, converters);
                VKMvcRouteMeta meta = new VKMvcRouteMeta(route.method(), fullPath, controller, method, invoker);
                server.addRoute(route.method(), fullPath, buildHandler(meta));
            } catch (Exception e) {
                onLoadError("Register MVC route failed: " + type.getName() + "#" + method.getName(), e);
            }
        }
    }

    public void registerControllers(String... basePackages) {
        Set<Class<?>> classes = VKScanner.scan(basePackages);
        for (Class<?> clazz : classes) {
            if (!clazz.isAnnotationPresent(VKApi.class)) {
                continue;
            }
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object controller = constructor.newInstance();
                registerController(controller);
            } catch (Exception e) {
                onLoadError("Create controller instance failed: " + clazz.getName(), e);
            }
        }
    }

    private VKHandler buildHandler(VKMvcRouteMeta meta) {
        return (req, res) -> {
            long requestTime = System.currentTimeMillis();
            try {
                Object ret = meta.invoker().invoke(req, res);
                if (meta.invoker().isVoidReturn()) {
                    return;
                }
                writeReturn(req, res, ret, requestTime);
            } catch (VKMvcBindException e) {
                writeError(req, res, config.bindErrorStatus(), e.getMessage(), requestTime);
            } catch (VKMvcInvokeException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                String message = config.exposeExceptionMessage()
                        ? (cause.getMessage() == null ? config.internalErrorMessage() : cause.getMessage())
                        : config.internalErrorMessage();
                Vostok.Log.error("MVC invoke failed: " + meta.method(), cause);
                writeError(req, res, config.internalErrorStatus(), message, requestTime);
            } catch (Throwable t) {
                String message = config.exposeExceptionMessage()
                        ? (t.getMessage() == null ? config.internalErrorMessage() : t.getMessage())
                        : config.internalErrorMessage();
                Vostok.Log.error("MVC route failed: " + meta.method(), t);
                writeError(req, res, config.internalErrorStatus(), message, requestTime);
            }
        };
    }

    private void writeReturn(yueyang.vostok.web.http.VKRequest req,
                             yueyang.vostok.web.http.VKResponse res,
                             Object ret,
                             long requestTime) {
        if (!config.autoWrapEnabled() && !(ret instanceof VKWebResult<?>)) {
            writeRaw(res, ret);
            return;
        }

        VKWebResult<?> result;
        if (ret instanceof VKWebResult<?> webResult) {
            result = webResult;
        } else {
            result = VKWebResult.ok(ret).statusCode(res.status());
        }

        int statusCode = result.statusCode() <= 0 ? res.status() : result.statusCode();
        fillRuntimeFields(result, req.traceId(), requestTime, System.currentTimeMillis(), statusCode);
        res.status(statusCode).json(Vostok.Util.toJson(result));
    }

    private void writeError(yueyang.vostok.web.http.VKRequest req,
                            yueyang.vostok.web.http.VKResponse res,
                            int status,
                            String message,
                            long requestTime) {
        long responseTime = System.currentTimeMillis();
        VKWebResult<Object> result = VKWebResult.error(status, message);
        fillRuntimeFields(result, req.traceId(), requestTime, responseTime, status);
        res.status(status).json(Vostok.Util.toJson(result));
    }

    private void fillRuntimeFields(VKWebResult<?> result,
                                   String traceId,
                                   long requestTime,
                                   long responseTime,
                                   int statusCode) {
        result.statusCode(statusCode)
                .traceId(traceId)
                .requestTime(requestTime)
                .responseTime(responseTime)
                .requestCostMs(Math.max(0L, responseTime - requestTime));
    }

    private void writeRaw(yueyang.vostok.web.http.VKResponse res, Object ret) {
        if (ret == null) {
            return;
        }
        if (ret instanceof byte[] bytes) {
            res.body(bytes);
            return;
        }
        if (ret instanceof String s) {
            res.text(s);
            return;
        }
        res.json(Vostok.Util.toJson(ret));
    }

    private HttpRoute resolveHttpRoute(Method method) {
        VKGet get = method.getAnnotation(VKGet.class);
        VKPost post = method.getAnnotation(VKPost.class);
        VKPut put = method.getAnnotation(VKPut.class);
        VKDelete delete = method.getAnnotation(VKDelete.class);
        VKPatch patch = method.getAnnotation(VKPatch.class);

        int count = countNonNull(get, post, put, delete, patch);
        if (count == 0) {
            return null;
        }
        if (count > 1) {
            throw new IllegalArgumentException("Only one HTTP mapping annotation is allowed: " + method);
        }

        if (get != null) {
            return new HttpRoute("GET", get.value());
        }
        if (post != null) {
            return new HttpRoute("POST", post.value());
        }
        if (put != null) {
            return new HttpRoute("PUT", put.value());
        }
        if (delete != null) {
            return new HttpRoute("DELETE", delete.value());
        }
        return new HttpRoute("PATCH", patch.value());
    }

    private static int countNonNull(Object... values) {
        int c = 0;
        for (Object value : values) {
            if (value != null) {
                c++;
            }
        }
        return c;
    }

    private String joinPath(String prefix, String routePath) {
        String p1 = normalizePath(prefix, true);
        String p2 = normalizePath(routePath, false);
        if (p1.isEmpty()) {
            return p2;
        }
        if ("/".equals(p2)) {
            return p1;
        }
        if (p1.endsWith("/")) {
            return normalizePath(p1 + p2, false);
        }
        return normalizePath(p1 + "/" + stripLeadingSlash(p2), false);
    }

    private String normalizePath(String path, boolean allowEmpty) {
        if (path == null || path.isBlank()) {
            return allowEmpty ? "" : "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private String stripLeadingSlash(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.charAt(0) == '/' ? path.substring(1) : path;
    }

    private void onLoadError(String message, Exception e) {
        if (config.failFastOnControllerLoad()) {
            if (e == null) {
                throw new IllegalStateException(message);
            }
            throw new IllegalStateException(message, e);
        }
        if (e == null) {
            Vostok.Log.warn(message);
        } else {
            Vostok.Log.warn(message + ", cause=" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private record HttpRoute(String method, String path) {
    }
}
