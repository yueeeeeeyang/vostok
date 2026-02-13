package yueyang.vostok.common.json;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class VKJson {
    private VKJson() {
    }

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(obj, sb);
        return sb.toString();
    }

    public static <T> T fromJson(String json, Class<T> type) {
        Objects.requireNonNull(type, "type is null");
        if (json == null) {
            return null;
        }
        Parser p = new Parser(json);
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.isEnd()) {
            throw new IllegalArgumentException("Invalid JSON: trailing content");
        }
        return cast(value, type, null);
    }

    private static void writeValue(Object obj, StringBuilder sb) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        if (obj instanceof String s) {
            writeString(s, sb);
            return;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }
        if (obj.getClass().isEnum()) {
            writeString(((Enum<?>) obj).name(), sb);
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            writeMap(map, sb);
            return;
        }
        if (obj instanceof Iterable<?> it) {
            writeIterable(it, sb);
            return;
        }
        if (obj.getClass().isArray()) {
            writeArray(obj, sb);
            return;
        }
        writePojo(obj, sb);
    }

    private static void writeMap(Map<?, ?> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(String.valueOf(e.getKey()), sb);
            sb.append(':');
            writeValue(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeIterable(Iterable<?> it, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object v : it) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeValue(v, sb);
        }
        sb.append(']');
    }

    private static void writeArray(Object array, StringBuilder sb) {
        sb.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(Array.get(array, i), sb);
        }
        sb.append(']');
    }

    private static void writePojo(Object obj, StringBuilder sb) {
        sb.append('{');
        Field[] fields = FieldCache.get(obj.getClass());
        boolean first = true;
        for (Field f : fields) {
            try {
                Object v = f.get(obj);
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(f.getName(), sb);
                sb.append(':');
                writeValue(v, sb);
            } catch (IllegalAccessException ignore) {
            }
        }
        sb.append('}');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value, Class<T> type, Type genericType) {
        if (value == null) {
            return null;
        }
        if (type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == int.class || type == Integer.class) {
            return (T) Integer.valueOf(toNumber(value).intValue());
        }
        if (type == long.class || type == Long.class) {
            return (T) Long.valueOf(toNumber(value).longValue());
        }
        if (type == double.class || type == Double.class) {
            return (T) Double.valueOf(toNumber(value).doubleValue());
        }
        if (type == float.class || type == Float.class) {
            return (T) Float.valueOf(toNumber(value).floatValue());
        }
        if (type == boolean.class || type == Boolean.class) {
            return (T) Boolean.valueOf(toBoolean(value));
        }
        if (type.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) type, String.valueOf(value));
        }
        if (type.isArray() && value instanceof List<?> list) {
            Class<?> component = type.getComponentType();
            Object arr = Array.newInstance(component, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(arr, i, cast(list.get(i), component, component));
            }
            return (T) arr;
        }
        if (Map.class.isAssignableFrom(type) && value instanceof Map<?, ?> map) {
            return (T) map;
        }
        if (List.class.isAssignableFrom(type) && value instanceof List<?> list) {
            return (T) list;
        }
        if (value instanceof Map<?, ?> map) {
            return (T) mapToPojo(map, type);
        }
        throw new IllegalArgumentException("Unsupported type: " + type.getName());
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number n) {
            return n;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object mapToPojo(Map<?, ?> map, Class<?> type) {
        Object obj = newInstance(type);
        Field[] fields = FieldCache.get(type);
        for (Field f : fields) {
            Object v = map.get(f.getName());
            if (v == null) {
                continue;
            }
            try {
                Object converted = convertFieldValue(v, f.getType(), f.getGenericType());
                f.set(obj, converted);
            } catch (Exception ignore) {
            }
        }
        return obj;
    }

    private static Object convertFieldValue(Object v, Class<?> fieldType, Type genericType) {
        if (v == null) {
            return null;
        }
        if (fieldType.isArray() && v instanceof List<?> list) {
            Class<?> component = fieldType.getComponentType();
            Object arr = Array.newInstance(component, list.size());
            for (int i = 0; i < list.size(); i++) {
                Array.set(arr, i, cast(list.get(i), component, component));
            }
            return arr;
        }
        if (List.class.isAssignableFrom(fieldType) && v instanceof List<?> list) {
            Class<?> itemType = Object.class;
            if (genericType instanceof ParameterizedType pt) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> c) {
                    itemType = c;
                }
            }
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(cast(item, itemType, itemType));
            }
            return out;
        }
        if (Map.class.isAssignableFrom(fieldType) && v instanceof Map<?, ?> map) {
            return map;
        }
        if (fieldType.isAssignableFrom(v.getClass())) {
            return v;
        }
        return cast(v, fieldType, genericType);
    }

    private static Object newInstance(Class<?> type) {
        try {
            Constructor<?> c = type.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("No default constructor: " + type.getName(), e);
        }
    }

    private static final class FieldCache {
        private static final ConcurrentHashMap<Class<?>, Field[]> CACHE = new ConcurrentHashMap<>();

        static Field[] get(Class<?> type) {
            return CACHE.computeIfAbsent(type, FieldCache::resolve);
        }

        private static Field[] resolve(Class<?> type) {
            List<Field> list = new ArrayList<>();
            Class<?> curr = type;
            while (curr != null && curr != Object.class) {
                for (Field f : curr.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isTransient(mod)) {
                        continue;
                    }
                    f.setAccessible(true);
                    list.add(f);
                }
                curr = curr.getSuperclass();
            }
            return list.toArray(new Field[0]);
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean isEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (!isEnd()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (isEnd()) {
                throw new IllegalArgumentException("Empty JSON");
            }
            char c = s.charAt(pos);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                return parseNull();
            }
            return parseNumber();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (s.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected '\"'");
                }
                pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (s.charAt(pos) == '}') {
                    pos++;
                    break;
                }
                if (s.charAt(pos) != ',') {
                    throw new IllegalArgumentException("Expected ','");
                }
                pos++;
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (s.charAt(pos) == ']') {
                    pos++;
                    break;
                }
                if (s.charAt(pos) != ',') {
                    throw new IllegalArgumentException("Expected ','");
                }
                pos++;
            }
            return list;
        }

        String parseString() {
            if (s.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected '\"'");
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (!isEnd()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (isEnd()) {
                        break;
                    }
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean");
        }

        Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null");
        }

        Number parseNumber() {
            int start = pos;
            boolean dot = false;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            while (!isEnd()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                    continue;
                }
                if (c == '.') {
                    dot = true;
                    pos++;
                    continue;
                }
                break;
            }
            String num = s.substring(start, pos);
            try {
                if (dot) {
                    return Double.parseDouble(num);
                }
                long v = Long.parseLong(num);
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                    return (int) v;
                }
                return v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + num);
            }
        }
    }
}
