package yueyang.vostok.web.mvc.resolver;

import yueyang.vostok.web.http.VKRequest;
import yueyang.vostok.web.http.VKResponse;
import yueyang.vostok.web.http.VKUploadedFile;
import yueyang.vostok.web.mvc.VKMvcArgumentResolver;
import yueyang.vostok.web.mvc.VKMvcBindException;
import yueyang.vostok.web.mvc.VKMvcMethodInvoker;
import yueyang.vostok.web.mvc.VKMvcTypeConverterRegistry;

import java.util.List;

/** multipart 文件解析器。 */
public final class VKFileResolver implements VKMvcArgumentResolver {
    @Override
    public boolean supports(VKMvcMethodInvoker.ParameterSpec parameter) {
        return parameter.source() == VKMvcMethodInvoker.Source.FILE
                || parameter.source() == VKMvcMethodInvoker.Source.FILES;
    }

    @Override
    public Object resolve(VKMvcMethodInvoker.ParameterSpec parameter,
                          VKRequest req,
                          VKResponse res,
                          VKMvcTypeConverterRegistry converters) {
        if (parameter.source() == VKMvcMethodInvoker.Source.FILE) {
            VKUploadedFile file = req.file(parameter.name());
            if (file == null && parameter.required()) {
                throw new VKMvcBindException("Missing multipart file: " + parameter.name());
            }
            return file;
        }

        List<VKUploadedFile> files = req.files(parameter.name());
        if ((files == null || files.isEmpty()) && parameter.required()) {
            throw new VKMvcBindException("Missing multipart files: " + parameter.name());
        }
        return files;
    }
}
