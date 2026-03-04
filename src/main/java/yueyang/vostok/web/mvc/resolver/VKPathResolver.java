package yueyang.vostok.web.mvc.resolver;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.mvc.VKMvcArgumentResolver;
import yueyang.vostok.web.mvc.VKMvcBindException;
import yueyang.vostok.web.mvc.VKMvcMethodInvoker;
import yueyang.vostok.web.mvc.VKMvcTypeConverterRegistry;

/** 路径参数解析器。 */
public final class VKPathResolver implements VKMvcArgumentResolver {
    @Override
    public boolean supports(VKMvcMethodInvoker.ParameterSpec parameter) {
        return parameter.source() == VKMvcMethodInvoker.Source.PATH;
    }

    @Override
    public Object resolve(VKMvcMethodInvoker.ParameterSpec parameter,
                          VKRequest req,
                          VKResponse res,
                          VKMvcTypeConverterRegistry converters) {
        String raw = req.param(parameter.name());
        if (raw == null || raw.isEmpty()) {
            throw new VKMvcBindException("Missing path parameter: " + parameter.name());
        }
        try {
            return converters.convert(raw, parameter.type());
        } catch (Exception e) {
            throw new VKMvcBindException("Invalid path parameter  + parameter.name() + : " + raw, e);
        }
    }
}
