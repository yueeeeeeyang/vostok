package yueyang.vostok.type;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class VKTypeMapper {
    private VKTypeMapper() {
    }

    public static Object toJdbc(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return Date.valueOf((LocalDate) value);
        }
        if (value instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) value);
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value;
    }

    public static Object fromJdbc(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        if (targetType == LocalDate.class) {
            if (value instanceof Date) {
                return ((Date) value).toLocalDate();
            }
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime().toLocalDate();
            }
        }

        if (targetType == LocalDateTime.class) {
            if (value instanceof Timestamp) {
                return ((Timestamp) value).toLocalDateTime();
            }
            if (value instanceof Date) {
                return ((Date) value).toLocalDate().atStartOfDay();
            }
        }

        if (targetType == BigDecimal.class) {
            if (value instanceof BigDecimal) {
                return value;
            }
            return new BigDecimal(value.toString());
        }

        if (targetType.isEnum()) {
            return mapEnum(value, targetType);
        }

        return value;
    }

    private static Object mapEnum(Object value, Class<?> targetType) {
        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;

        if (value instanceof String) {
            return Enum.valueOf(enumType, (String) value);
        }
        if (value instanceof Number) {
            int ordinal = ((Number) value).intValue();
            Enum[] values = enumType.getEnumConstants();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new yueyang.vostok.exception.VKArgumentException("Enum ordinal out of range: " + ordinal);
            }
            return values[ordinal];
        }

        throw new yueyang.vostok.exception.VKArgumentException("Unsupported enum value type: " + value.getClass());
    }
}
