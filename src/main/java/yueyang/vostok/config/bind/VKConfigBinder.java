package yueyang.vostok.config.bind;

import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 类型安全配置绑定器，将 config 快照中指定 prefix 下的 key 映射到 POJO 字段。
 * <p>
 * 支持字段类型：{@code String}、{@code int/Integer}、{@code long/Long}、
 * {@code double/Double}、{@code float/Float}、{@code boolean/Boolean}、
 * {@code List<String>}（逗号分隔或 key[0]/key[1] 索引格式）。
 * <p>
 * 字段名查找规则（按优先级）：
 * <ol>
 *   <li>{@code prefix.fieldName}（camelCase 原样）</li>
 *   <li>{@code prefix.field-name}（camelCase → kebab-case 转换）</li>
 * </ol>
 * POJO 必须提供无参构造器（public 或 package-private）。
 */
public final class VKConfigBinder {

    private VKConfigBinder() {
    }

    /**
     * 将 data 中前缀为 prefix 的 key 绑定到 type 的新实例。
     *
     * @param prefix config key 前缀（不含尾部 '.'），传 null 或空串表示从顶层开始
     * @param type   目标 POJO 类，必须有无参构造器
     * @param data   完整 config 快照（已完成插值）
     * @throws VKConfigException 无法实例化或字段赋值失败时抛出
     */
    public static <T> T bind(String prefix, Class<T> type, Map<String, String> data) {
        T instance;
        try {
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            instance = ctor.newInstance();
        } catch (Exception e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Cannot instantiate config class: " + type.getName() + " (needs no-arg constructor)", e);
        }

        // 构造有效前缀（末尾带 '.'，方便拼接 key）
        String effectivePrefix = (prefix == null || prefix.isBlank()) ? "" : prefix.trim() + ".";

        for (Field field : collectFields(type)) {
            field.setAccessible(true);
            String camelKey  = effectivePrefix + field.getName();
            String kebabKey  = effectivePrefix + camelToKebab(field.getName());

            String raw = data.get(camelKey);
            if (raw == null) {
                raw = data.get(kebabKey);
            }

            if (raw == null) {
                // 对 List<String> 尝试索引格式读取
                if (isStringList(field)) {
                    List<String> list = readList(camelKey, data);
                    if (list.isEmpty()) {
                        list = readList(kebabKey, data);
                    }
                    if (!list.isEmpty()) {
                        setFieldValue(instance, field, list);
                    }
                }
                continue;
            }

            setFieldTyped(instance, field, raw);
        }
        return instance;
    }

    /**
     * 从带 {@link VKConfigPrefix} 注解的 POJO 类中读取前缀后绑定。
     *
     * @throws VKConfigException 类未标注 @VKConfigPrefix 时抛出
     */
    public static <T> T bind(Class<T> type, Map<String, String> data) {
        VKConfigPrefix ann = type.getAnnotation(VKConfigPrefix.class);
        if (ann == null) {
            throw new VKConfigException(VKConfigErrorCode.INVALID_ARGUMENT,
                    "Class " + type.getName() + " is not annotated with @VKConfigPrefix");
        }
        return bind(ann.value(), type, data);
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    /** 收集 type 及其所有父类（不含 Object）的声明字段。 */
    private static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> cur = type;
        while (cur != null && cur != Object.class) {
            fields.addAll(Arrays.asList(cur.getDeclaredFields()));
            cur = cur.getSuperclass();
        }
        return fields;
    }

    /**
     * 根据字段类型将字符串 raw 转换并赋值。
     * 不支持的类型静默跳过。
     */
    private static void setFieldTyped(Object instance, Field field, String raw) {
        String v = raw.trim();
        Class<?> ft = field.getType();
        try {
            if (ft == String.class) {
                setFieldValue(instance, field, v);
            } else if (ft == int.class || ft == Integer.class) {
                setFieldValue(instance, field, Integer.parseInt(v));
            } else if (ft == long.class || ft == Long.class) {
                setFieldValue(instance, field, Long.parseLong(v));
            } else if (ft == double.class || ft == Double.class) {
                setFieldValue(instance, field, Double.parseDouble(v));
            } else if (ft == float.class || ft == Float.class) {
                setFieldValue(instance, field, Float.parseFloat(v));
            } else if (ft == boolean.class || ft == Boolean.class) {
                setFieldValue(instance, field, parseBool(v));
            } else if (ft == List.class) {
                setFieldValue(instance, field, splitComma(v));
            }
            // 其他类型暂不支持，静默跳过
        } catch (NumberFormatException e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Failed to convert config value for field '" + field.getName() +
                    "': cannot parse '" + v + "' as " + ft.getSimpleName(), e);
        }
    }

    private static void setFieldValue(Object instance, Field field, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new VKConfigException(VKConfigErrorCode.CONFIG_ERROR,
                    "Failed to set field: " + field.getName(), e);
        }
    }

    /** 判断字段是否为 List&lt;String&gt; 类型。 */
    private static boolean isStringList(Field field) {
        if (field.getType() != List.class) return false;
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType pt)) return false;
        Type[] args = pt.getActualTypeArguments();
        return args.length == 1 && args[0] == String.class;
    }

    /**
     * 读取 List 值：先尝试 prefix 对应的逗号分隔值，再尝试 prefix[0]/prefix[1] 索引格式。
     */
    private static List<String> readList(String prefix, Map<String, String> data) {
        String direct = data.get(prefix);
        if (direct != null) {
            return splitComma(direct);
        }
        List<String> list = new ArrayList<>();
        for (int i = 0; ; i++) {
            String v = data.get(prefix + "[" + i + "]");
            if (v == null) break;
            list.add(v);
        }
        return list;
    }

    private static List<String> splitComma(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static boolean parseBool(String v) {
        String lower = v.toLowerCase(Locale.ROOT);
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower) || "on".equals(lower);
    }

    /** camelCase → camel-case（kebab-case 转换，用于 key 回退匹配）。 */
    private static String camelToKebab(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }
}
