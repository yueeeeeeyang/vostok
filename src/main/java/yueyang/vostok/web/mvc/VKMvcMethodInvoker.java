package yueyang.vostok.web.mvc;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.http.VKUploadedFile;
import yueyang.vostok.web.mvc.annotation.VKBody;
import yueyang.vostok.web.mvc.annotation.VKCookie;
import yueyang.vostok.web.mvc.annotation.VKFile;
import yueyang.vostok.web.mvc.annotation.VKFiles;
import yueyang.vostok.web.mvc.annotation.VKForm;
import yueyang.vostok.web.mvc.annotation.VKHeader;
import yueyang.vostok.web.mvc.annotation.VKPath;
import yueyang.vostok.web.mvc.annotation.VKQuery;
import yueyang.vostok.web.mvc.resolver.VKBodyResolver;
import yueyang.vostok.web.mvc.resolver.VKCookieResolver;
import yueyang.vostok.web.mvc.resolver.VKFileResolver;
import yueyang.vostok.web.mvc.resolver.VKFormResolver;
import yueyang.vostok.web.mvc.resolver.VKHeaderResolver;
import yueyang.vostok.web.mvc.resolver.VKNativeResolver;
import yueyang.vostok.web.mvc.resolver.VKPathResolver;
import yueyang.vostok.web.mvc.resolver.VKQueryResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/** 控制器方法调用器：负责参数绑定并反射调用。 */
public final class VKMvcMethodInvoker {
    private final Object controller;
    private final Method method;
    private final boolean voidReturn;
    private final List<ParameterSpec> specs;
    private final List<VKMvcArgumentResolver> resolvers;
    private final VKMvcTypeConverterRegistry converters;

    public VKMvcMethodInvoker(Object controller, Method method, VKMvcTypeConverterRegistry converters) {
        this.controller = controller;
        this.method = method;
        this.method.setAccessible(true);
        this.voidReturn = method.getReturnType() == void.class;
        this.converters = converters == null ? new VKMvcTypeConverterRegistry() : converters;
        this.specs = resolveSpecs(method);
        this.resolvers = List.of(
                new VKNativeResolver(),
                new VKPathResolver(),
                new VKQueryResolver(),
                new VKHeaderResolver(),
                new VKCookieResolver(),
                new VKBodyResolver(),
                new VKFormResolver(),
                new VKFileResolver()
        );
    }

    public boolean isVoidReturn() {
        return voidReturn;
    }

    public Method method() {
        return method;
    }

    public Object invoke(VKRequest req, VKResponse res) {
        Object[] args = new Object[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            ParameterSpec spec = specs.get(i);
            VKMvcArgumentResolver resolver = findResolver(spec);
            if (resolver == null) {
                throw new VKMvcBindException("No resolver for parameter: " + method.getName() + "#" + i);
            }
            Object value = resolver.resolve(spec, req, res, converters);
            if (value == null && spec.type().isPrimitive()) {
                throw new VKMvcBindException("Primitive parameter cannot be null: " + spec.type().getSimpleName());
            }
            args[i] = value;
        }

        try {
            return method.invoke(controller, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException() == null ? e : e.getTargetException();
            throw new VKMvcInvokeException("Invoke controller method failed: " + method, cause);
        } catch (Exception e) {
            throw new VKMvcInvokeException("Invoke controller method failed: " + method, e);
        }
    }

    private VKMvcArgumentResolver findResolver(ParameterSpec spec) {
        for (VKMvcArgumentResolver resolver : resolvers) {
            if (resolver.supports(spec)) {
                return resolver;
            }
        }
        return null;
    }

    private static List<ParameterSpec> resolveSpecs(Method method) {
        Parameter[] parameters = method.getParameters();
        List<ParameterSpec> out = new ArrayList<>(parameters.length);
        int bodyCount = 0;

        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Class<?> type = p.getType();

            if (type == VKRequest.class) {
                out.add(new ParameterSpec(i, type, Source.REQUEST, null, true, null, false));
                continue;
            }
            if (type == VKResponse.class) {
                out.add(new ParameterSpec(i, type, Source.RESPONSE, null, true, null, false));
                continue;
            }

            VKPath path = p.getAnnotation(VKPath.class);
            VKQuery query = p.getAnnotation(VKQuery.class);
            VKHeader header = p.getAnnotation(VKHeader.class);
            VKCookie cookie = p.getAnnotation(VKCookie.class);
            VKBody body = p.getAnnotation(VKBody.class);
            VKForm form = p.getAnnotation(VKForm.class);
            VKFile file = p.getAnnotation(VKFile.class);
            VKFiles files = p.getAnnotation(VKFiles.class);

            int bindCount = countNonNull(path, query, header, cookie, body, form, file, files);
            if (bindCount == 0) {
                throw new IllegalArgumentException("Missing binding annotation on parameter: " + method + " index=" + i);
            }
            if (bindCount > 1) {
                throw new IllegalArgumentException("Only one binding annotation is allowed per parameter: " + method + " index=" + i);
            }

            if (path != null) {
                out.add(new ParameterSpec(i, type, Source.PATH, path.value(), true, null, false));
                continue;
            }
            if (query != null) {
                boolean hasDefault = query.defaultValue() != null && !query.defaultValue().isEmpty();
                out.add(new ParameterSpec(i, type, Source.QUERY, query.value(), query.required(), query.defaultValue(), hasDefault));
                continue;
            }
            if (header != null) {
                boolean hasDefault = header.defaultValue() != null && !header.defaultValue().isEmpty();
                out.add(new ParameterSpec(i, type, Source.HEADER, header.value(), header.required(), header.defaultValue(), hasDefault));
                continue;
            }
            if (cookie != null) {
                boolean hasDefault = cookie.defaultValue() != null && !cookie.defaultValue().isEmpty();
                out.add(new ParameterSpec(i, type, Source.COOKIE, cookie.value(), cookie.required(), cookie.defaultValue(), hasDefault));
                continue;
            }
            if (body != null) {
                bodyCount++;
                if (bodyCount > 1) {
                    throw new IllegalArgumentException("Only one @VKBody is allowed per method: " + method);
                }
                out.add(new ParameterSpec(i, type, Source.BODY, null, body.required(), null, false));
                continue;
            }
            if (form != null) {
                boolean hasDefault = form.defaultValue() != null && !form.defaultValue().isEmpty();
                out.add(new ParameterSpec(i, type, Source.FORM, form.value(), form.required(), form.defaultValue(), hasDefault));
                continue;
            }
            if (file != null) {
                if (!VKUploadedFile.class.isAssignableFrom(type)) {
                    throw new IllegalArgumentException("@VKFile parameter must be VKUploadedFile: " + method + " index=" + i);
                }
                out.add(new ParameterSpec(i, type, Source.FILE, file.value(), file.required(), null, false));
                continue;
            }
            if (files != null) {
                if (!List.class.isAssignableFrom(type)) {
                    throw new IllegalArgumentException("@VKFiles parameter must be List<VKUploadedFile>: " + method + " index=" + i);
                }
                out.add(new ParameterSpec(i, type, Source.FILES, files.value(), files.required(), null, false));
            }
        }

        return out;
    }

    private static int countNonNull(Annotation... annotations) {
        int c = 0;
        for (Annotation annotation : annotations) {
            if (annotation != null) {
                c++;
            }
        }
        return c;
    }

    public enum Source {
        REQUEST,
        RESPONSE,
        PATH,
        QUERY,
        HEADER,
        COOKIE,
        BODY,
        FORM,
        FILE,
        FILES
    }

    /** 参数绑定规格。 */
    public record ParameterSpec(int index,
                                Class<?> type,
                                Source source,
                                String name,
                                boolean required,
                                String defaultValue,
                                boolean hasDefaultValue) {
    }
}
