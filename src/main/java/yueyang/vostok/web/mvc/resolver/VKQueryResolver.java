package yueyang.vostok.web.mvc.resolver;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.mvc.VKMvcArgumentResolver;
import yueyang.vostok.web.mvc.VKMvcBindException;
import yueyang.vostok.web.mvc.VKMvcMethodInvoker;
import yueyang.vostok.web.mvc.VKMvcTypeConverterRegistry;

/** 查询参数解析器。 */
public final class VKQueryResolver implements VKMvcArgumentResolver {
    @Override
    public boolean supports(VKMvcMethodInvoker.ParameterSpec parameter) {
        return parameter.source() == VKMvcMethodInvoker.Source.QUERY;
    }

    @Override
    public Object resolve(VKMvcMethodInvoker.ParameterSpec parameter,
                          VKRequest req,
                          VKResponse res,
                          VKMvcTypeConverterRegistry converters) {
        String raw = req.queryParam(parameter.name());
        if ((raw == null || raw.isEmpty()) && parameter.hasDefaultValue()) {
            raw = parameter.defaultValue();
        }
        if ((raw == null || raw.isEmpty()) && parameter.required()) {
            throw new VKMvcBindException("Missing query parameter: " + parameter.name());
        }
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return converters.convert(raw, parameter.type());
        } catch (Exception e) {
            throw new VKMvcBindException("Invalid query parameter  + parameter.name() + : " + raw, e);
        }
    }
}
