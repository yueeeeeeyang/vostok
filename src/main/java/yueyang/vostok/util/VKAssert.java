package yueyang.vostok.util;

import yueyang.vostok.exception.VKArgumentException;

public final class VKAssert {
    private VKAssert() {
    }

    public static void notBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new VKArgumentException(message);
        }
    }

    public static void notNull(Object value, String message) {
        if (value == null) {
            throw new VKArgumentException(message);
        }
    }

    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new VKArgumentException(message);
        }
    }
}
