package yueyang.vostok.config.validate;

import java.util.Map;

public class VKConfigView {
    private final Map<String, String> data;

    public VKConfigView(Map<String, String> data) {
        this.data = data;
    }

    public String get(String key) {
        return data.get(key);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public int getInt(String key, int defaultValue) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Map<String, String> asMap() {
        return data;
    }
}
