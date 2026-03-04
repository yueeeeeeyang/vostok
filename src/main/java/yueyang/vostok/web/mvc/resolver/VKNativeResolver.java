package yueyang.vostok.web.mvc.resolver;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.mvc.VKMvcArgumentResolver;
import yueyang.vostok.web.mvc.VKMvcMethodInvoker;
import yueyang.vostok.web.mvc.VKMvcTypeConverterRegistry;

/** 原生对象注入解析器（VKRequest / VKResponse）。 */
public final class VKNativeResolver implements VKMvcArgumentResolver {
    @Override
    public boolean supports(VKMvcMethodInvoker.ParameterSpec parameter) {
        return parameter.source() == VKMvcMethodInvoker.Source.REQUEST
                || parameter.source() == VKMvcMethodInvoker.Source.RESPONSE;
    }

    @Override
    public Object resolve(VKMvcMethodInvoker.ParameterSpec parameter,
                          VKRequest req,
                          VKResponse res,
                          VKMvcTypeConverterRegistry converters) {
        if (parameter.source() == VKMvcMethodInvoker.Source.REQUEST) {
            return req;
        }
        return res;
    }
}
