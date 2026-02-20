package yueyang.vostok.util;

import yueyang.vostok.util.codec.VKCodecs;
import yueyang.vostok.util.collection.VKCollections;
import yueyang.vostok.util.id.VKIds;
import yueyang.vostok.util.json.VKJson;
import yueyang.vostok.util.json.VKJsonProvider;
import yueyang.vostok.util.string.VKStrings;
import yueyang.vostok.util.time.VKTimes;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

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

    public static boolean isEmpty(Collection<?> value) {
        return VKCollections.isEmpty(value);
    }

    public static boolean isEmpty(Map<?, ?> value) {
        return VKCollections.isEmpty(value);
    }

    public static <K, V> HashMap<K, V> newHashMapWithExpectedSize(int expectedSize) {
        return VKCollections.newHashMapWithExpectedSize(expectedSize);
    }

    public static <T> T safeGet(List<T> list, int index, T defaultValue) {
        return VKCollections.safeGet(list, index, defaultValue);
    }

    public static <T> List<T> distinctPreserveOrder(List<T> list) {
        return VKCollections.distinctPreserveOrder(list);
    }

    public static <T> ArrayList<T> newArrayListWithExpectedSize(int expectedSize) {
        return VKCollections.newArrayListWithExpectedSize(expectedSize);
    }

    public static <T> HashSet<T> newHashSetWithExpectedSize(int expectedSize) {
        return VKCollections.newHashSetWithExpectedSize(expectedSize);
    }

    public static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
        return VKCollections.newLinkedHashMapWithExpectedSize(expectedSize);
    }

    public static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMapWithExpectedSize(int expectedSize) {
        return VKCollections.newConcurrentHashMapWithExpectedSize(expectedSize);
    }

    public static <T> T safeFirst(List<T> list, T defaultValue) {
        return VKCollections.safeFirst(list, defaultValue);
    }

    public static <T> T safeLast(List<T> list, T defaultValue) {
        return VKCollections.safeLast(list, defaultValue);
    }

    public static <T> List<T> safeSubList(List<T> list, int from, int to) {
        return VKCollections.safeSubList(list, from, to);
    }

    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        return VKCollections.getOrDefault(map, key, defaultValue);
    }

    public static <T, K> List<T> distinctBy(List<T> list, Function<T, K> keyFn) {
        return VKCollections.distinctBy(list, keyFn);
    }

    public static <T> List<T> union(List<T> a, List<T> b) {
        return VKCollections.union(a, b);
    }

    public static <T> List<T> intersect(List<T> a, List<T> b) {
        return VKCollections.intersect(a, b);
    }

    public static <T> List<T> difference(List<T> a, List<T> b) {
        return VKCollections.difference(a, b);
    }

    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        return VKCollections.filter(list, predicate);
    }

    public static <T, R> List<R> map(List<T> list, Function<T, R> fn) {
        return VKCollections.map(list, fn);
    }

    public static <T, R> List<R> flatMap(List<T> list, Function<T, List<R>> fn) {
        return VKCollections.flatMap(list, fn);
    }

    public static <T> List<T> compact(List<T> list) {
        return VKCollections.compact(list);
    }

    public static <T, K> Map<K, T> indexBy(List<T> list, Function<T, K> keyFn) {
        return VKCollections.indexBy(list, keyFn);
    }

    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> keyFn) {
        return VKCollections.groupBy(list, keyFn);
    }

    public static <T, K, V> Map<K, V> toMap(List<T> list, Function<T, K> keyFn, Function<T, V> valueFn,
                                             BiFunction<V, V, V> mergeFn) {
        return VKCollections.toMap(list, keyFn, valueFn, mergeFn);
    }

    public static <T> List<List<T>> partition(List<T> list, int batchSize) {
        return VKCollections.partition(list, batchSize);
    }

    public static <T> List<List<T>> chunked(List<T> list, int chunkSize) {
        return VKCollections.chunked(list, chunkSize);
    }

    public static <T> List<T> page(List<T> list, int pageNo, int pageSize) {
        return VKCollections.page(list, pageNo, pageSize);
    }

    public static <T> boolean anyMatch(Collection<T> value, Predicate<T> predicate) {
        return VKCollections.anyMatch(value, predicate);
    }

    public static <T> boolean allMatch(Collection<T> value, Predicate<T> predicate) {
        return VKCollections.allMatch(value, predicate);
    }

    public static <T> long count(Collection<T> value, Predicate<T> predicate) {
        return VKCollections.count(value, predicate);
    }

    public static <T> boolean containsAny(Collection<T> left, Collection<T> right) {
        return VKCollections.containsAny(left, right);
    }

    public static <T> List<T> reverseNew(List<T> list) {
        return VKCollections.reverseNew(list);
    }

    public static <T> void swap(List<T> list, int i, int j) {
        VKCollections.swap(list, i, j);
    }

    public static <T> List<T> repeat(T value, int n) {
        return VKCollections.repeat(value, n);
    }

    public static <T> List<T> immutableCopy(List<T> list) {
        return VKCollections.immutableCopy(list);
    }

    public static <T> Set<T> immutableCopy(Set<T> set) {
        return VKCollections.immutableCopy(set);
    }

    public static <K, V> Map<K, V> immutableCopy(Map<K, V> map) {
        return VKCollections.immutableCopy(map);
    }

    public static long nowEpochMs() {
        return VKTimes.nowEpochMs();
    }

    public static long nowEpochSec() {
        return VKTimes.nowEpochSec();
    }

    public static String formatInstant(Instant instant, ZoneId zoneId, String pattern) {
        return VKTimes.formatInstant(instant, zoneId, pattern);
    }

    public static Instant parseInstant(String text, ZoneId zoneId, String pattern) {
        return VKTimes.parseInstant(text, zoneId, pattern);
    }

    public static Instant startOfDay(Instant instant, ZoneId zoneId) {
        return VKTimes.startOfDay(instant, zoneId);
    }

    public static Instant endOfDay(Instant instant, ZoneId zoneId) {
        return VKTimes.endOfDay(instant, zoneId);
    }

    public static String uuid() {
        return VKIds.uuid();
    }

    public static String randomAlphaNum(int len) {
        return VKIds.randomAlphaNum(len);
    }

    public static String traceId() {
        return VKIds.traceId();
    }

    public static String base64Encode(byte[] value) {
        return VKCodecs.base64Encode(value);
    }

    public static String base64Encode(String value) {
        return VKCodecs.base64Encode(value);
    }

    public static byte[] base64DecodeToBytes(String value) {
        return VKCodecs.base64DecodeToBytes(value);
    }

    public static String base64DecodeToString(String value) {
        return VKCodecs.base64DecodeToString(value);
    }

    public static String hexEncode(byte[] bytes) {
        return VKCodecs.hexEncode(bytes);
    }

    public static byte[] hexDecode(String hex) {
        return VKCodecs.hexDecode(hex);
    }

    public static long crc32(byte[] bytes) {
        return VKCodecs.crc32(bytes);
    }

    public static String md5Hex(String value) {
        return VKCodecs.md5Hex(value);
    }

    public static String sha256Hex(String value) {
        return VKCodecs.sha256Hex(value);
    }
}
