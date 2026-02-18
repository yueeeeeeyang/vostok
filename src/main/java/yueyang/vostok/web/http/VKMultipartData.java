package yueyang.vostok.web.http;

import java.util.List;
import java.util.Map;

public final class VKMultipartData {
    public static final VKMultipartData EMPTY = new VKMultipartData(Map.of(), Map.of(), List.of());

    private final Map<String, String> fields;
    private final Map<String, List<VKUploadedFile>> files;
    private final List<VKUploadedFile> all;

    public VKMultipartData(Map<String, String> fields, Map<String, List<VKUploadedFile>> files, List<VKUploadedFile> all) {
        this.fields = fields;
        this.files = files;
        this.all = all;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public Map<String, List<VKUploadedFile>> files() {
        return files;
    }

    public List<VKUploadedFile> all() {
        return all;
    }
}
