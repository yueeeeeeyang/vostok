package yueyang.vostok.web.mvc;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;

/** 参数解析器接口。 */
public interface VKMvcArgumentResolver {
    boolean supports(VKMvcMethodInvoker.ParameterSpec parameter);

    Object resolve(VKMvcMethodInvoker.ParameterSpec parameter,
                   VKRequest req,
                   VKResponse res,
                   VKMvcTypeConverterRegistry converters);
}
