package yueyang.vostok.util;

public final class VKStringUtil {
    private VKStringUtil() {
    }

    public static String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}
