package yueyang.vostok.web.mvc;

import java.lang.reflect.Method;

/** MVC 路由元数据。 */
public final class VKMvcRouteMeta {
    private final String httpMethod;
    private final String path;
    private final Object controller;
    private final Method method;
    private final VKMvcMethodInvoker invoker;

    public VKMvcRouteMeta(String httpMethod,
                          String path,
                          Object controller,
                          Method method,
                          VKMvcMethodInvoker invoker) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.controller = controller;
        this.method = method;
        this.invoker = invoker;
    }

    public String httpMethod() {
        return httpMethod;
    }

    public String path() {
        return path;
    }

    public Object controller() {
        return controller;
    }

    public Method method() {
        return method;
    }

    public VKMvcMethodInvoker invoker() {
        return invoker;
    }
}
