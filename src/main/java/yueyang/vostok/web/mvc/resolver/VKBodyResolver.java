package yueyang.vostok.web.mvc.resolver;

import yueyang.vostok.Vostok;
import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.mvc.VKMvcArgumentResolver;
import yueyang.vostok.web.mvc.VKMvcBindException;
import yueyang.vostok.web.mvc.VKMvcMethodInvoker;
import yueyang.vostok.web.mvc.VKMvcTypeConverterRegistry;

/** JSON 请求体解析器。 */
public final class VKBodyResolver implements VKMvcArgumentResolver {
    @Override
    public boolean supports(VKMvcMethodInvoker.ParameterSpec parameter) {
        return parameter.source() == VKMvcMethodInvoker.Source.BODY;
    }

    @Override
    public Object resolve(VKMvcMethodInvoker.ParameterSpec parameter,
                          VKRequest req,
                          VKResponse res,
                          VKMvcTypeConverterRegistry converters) {
        byte[] bodyBytes = req.body();
        if ((bodyBytes == null || bodyBytes.length == 0) && parameter.required()) {
            throw new VKMvcBindException("Missing request body");
        }
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }
        String body = req.bodyText();
        if ((body == null || body.isBlank()) && parameter.required()) {
            throw new VKMvcBindException("Missing request body");
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return Vostok.Util.fromJson(body, parameter.type());
        } catch (Exception e) {
            throw new VKMvcBindException("Invalid JSON request body", e);
        }
    }
}
