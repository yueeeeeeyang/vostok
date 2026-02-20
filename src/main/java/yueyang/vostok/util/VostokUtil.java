package yueyang.vostok.util;

import yueyang.vostok.util.json.VKJson;
import yueyang.vostok.util.json.VKJsonProvider;
import yueyang.vostok.util.string.VKStrings;

import java.util.Set;

/**
 * Vostok Util entry.
 */
public class VostokUtil {
    protected VostokUtil() {
    }

    public static String toJson(Object value) {
        return VKJson.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return VKJson.fromJson(json, type);
    }

    public static void registerJsonProvider(VKJsonProvider provider) {
        VKJson.registerProvider(provider);
    }

    public static void useJsonProvider(String name) {
        VKJson.use(name);
    }

    public static void resetDefaultJsonProvider() {
        VKJson.resetDefault();
    }

    public static String currentJsonProviderName() {
        return VKJson.currentProviderName();
    }

    public static Set<String> jsonProviderNames() {
        return VKJson.providerNames();
    }

    public static boolean isEmpty(String value) {
        return VKStrings.isEmpty(value);
    }

    public static boolean isBlank(String value) {
        return VKStrings.isBlank(value);
    }

    public static String trimToNull(String value) {
        return VKStrings.trimToNull(value);
    }

    public static String trimToEmpty(String value) {
        return VKStrings.trimToEmpty(value);
    }

    public static String defaultIfBlank(String value, String defaultValue) {
        return VKStrings.defaultIfBlank(value, defaultValue);
    }

    public static String[] fastSplit(String value, char delimiter) {
        return VKStrings.fastSplit(value, delimiter);
    }

    public static String join(char delimiter, Object... values) {
        return VKStrings.join(delimiter, values);
    }

    public static boolean equalsIgnoreCaseAscii(String a, String b) {
        return VKStrings.equalsIgnoreCaseAscii(a, b);
    }

    public static boolean containsIgnoreCaseAscii(String value, String part) {
        return VKStrings.containsIgnoreCaseAscii(value, part);
    }

    public static boolean startsWithIgnoreCaseAscii(String value, String prefix) {
        return VKStrings.startsWithIgnoreCaseAscii(value, prefix);
    }

    public static boolean wildcardMatch(String value, String pattern) {
        return VKStrings.wildcardMatch(value, pattern);
    }

    public static String replaceChar(String value, char target, char replacement) {
        return VKStrings.replaceChar(value, target, replacement);
    }

    public static String removeWhitespace(String value) {
        return VKStrings.removeWhitespace(value);
    }

    public static String collapseSpaces(String value) {
        return VKStrings.collapseSpaces(value);
    }

    public static String stripControlChars(String value) {
        return VKStrings.stripControlChars(value);
    }

    public static String truncateByCodePoint(String value, int maxCodePoints) {
        return VKStrings.truncateByCodePoint(value, maxCodePoints);
    }

    public static String ellipsis(String value, int maxCodePoints) {
        return VKStrings.ellipsis(value, maxCodePoints);
    }

    public static String safeSubstring(String value, int start, int end) {
        return VKStrings.safeSubstring(value, start, end);
    }

    public static String camelToSnake(String value) {
        return VKStrings.camelToSnake(value);
    }

    public static String snakeToCamel(String value) {
        return VKStrings.snakeToCamel(value);
    }

    public static String kebabToCamel(String value) {
        return VKStrings.kebabToCamel(value);
    }

    public static String urlEncodeUtf8(String value) {
        return VKStrings.urlEncodeUtf8(value);
    }

    public static String urlDecodeUtf8(String value) {
        return VKStrings.urlDecodeUtf8(value);
    }

    public static byte[] toUtf8Bytes(String value) {
        return VKStrings.toUtf8Bytes(value);
    }

    public static String fromUtf8Bytes(byte[] bytes) {
        return VKStrings.fromUtf8Bytes(bytes);
    }

    public static int toIntOrDefault(String value, int defaultValue) {
        return VKStrings.toIntOrDefault(value, defaultValue);
    }

    public static long toLongOrDefault(String value, long defaultValue) {
        return VKStrings.toLongOrDefault(value, defaultValue);
    }

    public static boolean toBoolOrDefault(String value, boolean defaultValue) {
        return VKStrings.toBoolOrDefault(value, defaultValue);
    }
}
