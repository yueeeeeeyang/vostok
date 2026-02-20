package yueyang.vostok.util.string;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VKStrings {
    private VKStrings() {
    }

    public static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    public static String[] fastSplit(String value, char delimiter) {
        if (value == null) {
            return new String[0];
        }
        if (value.isEmpty()) {
            return new String[]{""};
        }
        int parts = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == delimiter) {
                parts++;
            }
        }
        String[] out = new String[parts];
        int idx = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == delimiter) {
                out[idx++] = value.substring(start, i);
                start = i + 1;
            }
        }
        out[idx] = value.substring(start);
        return out;
    }

    public static List<String> splitToList(String value, char delimiter) {
        String[] arr = fastSplit(value, delimiter);
        if (arr.length == 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>(arr.length);
        Collections.addAll(out, arr);
        return out;
    }

    public static String join(char delimiter, Object... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    sb.append(delimiter);
                }
                if (values[i] != null) {
                    sb.append(values[i]);
                }
            }
            return sb.toString();
        });
    }

    public static String join(char delimiter, Iterable<?> values) {
        if (values == null) {
            return "";
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            boolean first = true;
            for (Object value : values) {
                if (!first) {
                    sb.append(delimiter);
                }
                first = false;
                if (value != null) {
                    sb.append(value);
                }
            }
            return sb.toString();
        });
    }

    public static boolean equalsIgnoreCaseAscii(String a, String b) {
        return VKStringMatcher.equalsIgnoreCaseAscii(a, b);
    }

    public static boolean containsIgnoreCaseAscii(String value, String part) {
        return VKStringMatcher.containsIgnoreCaseAscii(value, part);
    }

    public static boolean startsWithIgnoreCaseAscii(String value, String prefix) {
        return VKStringMatcher.startsWithIgnoreCaseAscii(value, prefix);
    }

    public static boolean wildcardMatch(String value, String pattern) {
        return VKStringMatcher.wildcardMatch(value, pattern);
    }

    public static String replaceChar(String value, char target, char replacement) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char[] chars = value.toCharArray();
        boolean changed = false;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == target) {
                chars[i] = replacement;
                changed = true;
            }
        }
        return changed ? new String(chars) : value;
    }

    public static String removeWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!Character.isWhitespace(c)) {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    public static String collapseSpaces(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            boolean prevSpace = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (!prevSpace) {
                        sb.append(' ');
                        prevSpace = true;
                    }
                } else {
                    sb.append(c);
                    prevSpace = false;
                }
            }
            return sb.toString().trim();
        });
    }

    public static String stripControlChars(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c >= 32 || c == '\n' || c == '\r' || c == '\t') {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    public static String truncateByCodePoint(String value, int maxCodePoints) {
        if (value == null || maxCodePoints < 0) {
            return value;
        }
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    public static String ellipsis(String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }
        if (maxCodePoints <= 0) {
            return "...";
        }
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        if (maxCodePoints <= 3) {
            return ".".repeat(maxCodePoints);
        }
        int keep = maxCodePoints - 3;
        int end = value.offsetByCodePoints(0, keep);
        return value.substring(0, end) + "...";
    }

    public static String safeSubstring(String value, int start, int end) {
        if (value == null) {
            return null;
        }
        int s = Math.min(value.length(), Math.max(0, start));
        int e = Math.min(value.length(), Math.max(s, end));
        return value.substring(s, e);
    }

    public static String camelToSnake(String value) {
        if (isEmpty(value)) {
            return value;
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isUpperCase(c)) {
                    if (i > 0) {
                        sb.append('_');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    public static String snakeToCamel(String value) {
        return separatedToCamel(value, '_');
    }

    public static String kebabToCamel(String value) {
        return separatedToCamel(value, '-');
    }

    private static String separatedToCamel(String value, char delimiter) {
        if (isEmpty(value)) {
            return value;
        }
        return VKStringBuilderPool.withBuilder(sb -> {
            boolean upperNext = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == delimiter) {
                    upperNext = true;
                    continue;
                }
                if (upperNext) {
                    sb.append(Character.toUpperCase(c));
                    upperNext = false;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    public static String urlEncodeUtf8(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String urlDecodeUtf8(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static byte[] toUtf8Bytes(String value) {
        if (value == null) {
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String fromUtf8Bytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static int toIntOrDefault(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    public static long toLongOrDefault(String value, long defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    public static boolean toBoolOrDefault(String value, boolean defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
            return false;
        }
        return defaultValue;
    }
}
