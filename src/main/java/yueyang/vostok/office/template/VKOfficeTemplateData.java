package yueyang.vostok.office.template;

import java.util.LinkedHashMap;
import java.util.Map;

/** 模板渲染数据。 */
public final class VKOfficeTemplateData {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public static VKOfficeTemplateData create() {
        return new VKOfficeTemplateData();
    }

    public VKOfficeTemplateData put(String key, Object value) {
        if (key != null && !key.isBlank()) {
            values.put(key, value);
        }
        return this;
    }

    public Map<String, Object> values() {
        return values;
    }
}
