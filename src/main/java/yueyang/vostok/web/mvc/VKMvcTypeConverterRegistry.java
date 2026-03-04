package yueyang.vostok.web.mvc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内置 + 可扩展的参数类型转换注册表。 */
public final class VKMvcTypeConverterRegistry {
    private final Map<Class<?>, VKMvcTypeConverter<?>> custom = new ConcurrentHashMap<>();

    public <T> VKMvcTypeConverterRegistry register(Class<T> type, VKMvcTypeConverter<T> converter) {
        if (type != null && converter != null) {
            custom.put(type, converter);
        }
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object convert(String raw, Class<?> targetType) {
        if (targetType == null) {
            return raw;
        }
        if (raw == null) {
            return null;
        }
        if (targetType == String.class || targetType == Object.class) {
            return raw;
        }

        VKMvcTypeConverter<?> customConverter = custom.get(targetType);
        if (customConverter != null) {
            return ((VKMvcTypeConverter) customConverter).convert(raw, targetType);
        }

        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(raw);
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.parseShort(raw);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.parseByte(raw);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(raw);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(raw);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if ("1".equals(raw)) {
                return true;
            }
            if ("0".equals(raw)) {
                return false;
            }
            return Boolean.parseBoolean(raw);
        }
        if (targetType == char.class || targetType == Character.class) {
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("Cannot convert empty text to char");
            }
            return raw.charAt(0);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), raw);
        }

        throw new IllegalArgumentException("Unsupported target type: " + targetType.getName());
    }
}
