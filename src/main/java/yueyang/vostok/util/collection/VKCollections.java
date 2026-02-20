package yueyang.vostok.util.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class VKCollections {
    private VKCollections() {
    }

    public static boolean isEmpty(Collection<?> value) {
        return value == null || value.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> value) {
        return value == null || value.isEmpty();
    }

    public static <K, V> HashMap<K, V> newHashMapWithExpectedSize(int expectedSize) {
        return new HashMap<>(hashCapacity(expectedSize));
    }

    public static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
        return new LinkedHashMap<>(hashCapacity(expectedSize));
    }

    public static <T> HashSet<T> newHashSetWithExpectedSize(int expectedSize) {
        return new HashSet<>(hashCapacity(expectedSize));
    }

    public static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMapWithExpectedSize(int expectedSize) {
        int cap = Math.max(1, expectedSize);
        return new ConcurrentHashMap<>(cap);
    }

    public static <T> ArrayList<T> newArrayListWithExpectedSize(int expectedSize) {
        return new ArrayList<>(Math.max(0, expectedSize));
    }

    public static <T> T safeGet(List<T> list, int index, T defaultValue) {
        if (list == null || index < 0 || index >= list.size()) {
            return defaultValue;
        }
        T value = list.get(index);
        return value == null ? defaultValue : value;
    }

    public static <T> T safeFirst(List<T> list, T defaultValue) {
        return safeGet(list, 0, defaultValue);
    }

    public static <T> T safeLast(List<T> list, T defaultValue) {
        if (list == null || list.isEmpty()) {
            return defaultValue;
        }
        T value = list.get(list.size() - 1);
        return value == null ? defaultValue : value;
    }

    public static <T> List<T> safeSubList(List<T> list, int from, int to) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        int start = Math.min(list.size(), Math.max(0, from));
        int end = Math.min(list.size(), Math.max(start, to));
        return new ArrayList<>(list.subList(start, end));
    }

    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        V v = map.get(key);
        return v == null ? defaultValue : v;
    }

    public static <T> List<T> distinctPreserveOrder(List<T> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<T> set = new LinkedHashSet<>(list);
        return new ArrayList<>(set);
    }

    public static <T, K> List<T> distinctBy(List<T> list, Function<T, K> keyFn) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        if (keyFn == null) {
            throw new IllegalArgumentException("keyFn is null");
        }
        LinkedHashSet<K> seen = new LinkedHashSet<>();
        ArrayList<T> out = new ArrayList<>(list.size());
        for (T item : list) {
            K key = keyFn.apply(item);
            if (seen.add(key)) {
                out.add(item);
            }
        }
        return out;
    }

    public static <T> List<T> union(List<T> a, List<T> b) {
        if (isEmpty(a) && isEmpty(b)) {
            return List.of();
        }
        LinkedHashSet<T> set = new LinkedHashSet<>();
        if (a != null) {
            set.addAll(a);
        }
        if (b != null) {
            set.addAll(b);
        }
        return new ArrayList<>(set);
    }

    public static <T> List<T> intersect(List<T> a, List<T> b) {
        if (isEmpty(a) || isEmpty(b)) {
            return List.of();
        }
        HashSet<T> right = new HashSet<>(b);
        LinkedHashSet<T> out = new LinkedHashSet<>();
        for (T item : a) {
            if (right.contains(item)) {
                out.add(item);
            }
        }
        return new ArrayList<>(out);
    }

    public static <T> List<T> difference(List<T> a, List<T> b) {
        if (isEmpty(a)) {
            return List.of();
        }
        if (isEmpty(b)) {
            return new ArrayList<>(a);
        }
        HashSet<T> right = new HashSet<>(b);
        ArrayList<T> out = new ArrayList<>();
        for (T item : a) {
            if (!right.contains(item)) {
                out.add(item);
            }
        }
        return out;
    }

    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate is null");
        }
        ArrayList<T> out = new ArrayList<>();
        for (T item : list) {
            if (predicate.test(item)) {
                out.add(item);
            }
        }
        return out;
    }

    public static <T, R> List<R> map(List<T> list, Function<T, R> fn) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        if (fn == null) {
            throw new IllegalArgumentException("fn is null");
        }
        ArrayList<R> out = new ArrayList<>(list.size());
        for (T item : list) {
            out.add(fn.apply(item));
        }
        return out;
    }

    public static <T, R> List<R> flatMap(List<T> list, Function<T, List<R>> fn) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        if (fn == null) {
            throw new IllegalArgumentException("fn is null");
        }
        ArrayList<R> out = new ArrayList<>();
        for (T item : list) {
            List<R> part = fn.apply(item);
            if (part != null && !part.isEmpty()) {
                out.addAll(part);
            }
        }
        return out;
    }

    public static <T> List<T> compact(List<T> list) {
        return filter(list, v -> v != null);
    }

    public static <T, K> Map<K, T> indexBy(List<T> list, Function<T, K> keyFn) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        if (keyFn == null) {
            throw new IllegalArgumentException("keyFn is null");
        }
        LinkedHashMap<K, T> out = new LinkedHashMap<>(hashCapacity(list.size()));
        for (T item : list) {
            out.put(keyFn.apply(item), item);
        }
        return out;
    }

    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> keyFn) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        if (keyFn == null) {
            throw new IllegalArgumentException("keyFn is null");
        }
        LinkedHashMap<K, List<T>> out = new LinkedHashMap<>(hashCapacity(list.size()));
        for (T item : list) {
            K key = keyFn.apply(item);
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        return out;
    }

    public static <T, K, V> Map<K, V> toMap(List<T> list, Function<T, K> keyFn, Function<T, V> valueFn,
                                             BiFunction<V, V, V> mergeFn) {
        if (list == null || list.isEmpty()) {
            return Map.of();
        }
        if (keyFn == null || valueFn == null) {
            throw new IllegalArgumentException("keyFn/valueFn is null");
        }
        BiFunction<V, V, V> merge = mergeFn == null ? (oldVal, newVal) -> newVal : mergeFn;
        LinkedHashMap<K, V> out = new LinkedHashMap<>(hashCapacity(list.size()));
        for (T item : list) {
            K key = keyFn.apply(item);
            V value = valueFn.apply(item);
            V prev = out.putIfAbsent(key, value);
            if (prev != null) {
                out.put(key, merge.apply(prev, value));
            }
        }
        return out;
    }

    public static <T> List<List<T>> partition(List<T> list, int batchSize) {
        return chunked(list, batchSize);
    }

    public static <T> List<List<T>> chunked(List<T> list, int chunkSize) {
        if (list == null || list.isEmpty() || chunkSize <= 0) {
            return List.of();
        }
        int chunks = (list.size() + chunkSize - 1) / chunkSize;
        ArrayList<List<T>> out = new ArrayList<>(chunks);
        for (int i = 0; i < list.size(); i += chunkSize) {
            out.add(new ArrayList<>(list.subList(i, Math.min(list.size(), i + chunkSize))));
        }
        return out;
    }

    public static <T> List<T> page(List<T> list, int pageNo, int pageSize) {
        if (list == null || list.isEmpty() || pageNo <= 0 || pageSize <= 0) {
            return List.of();
        }
        int start = (pageNo - 1) * pageSize;
        if (start >= list.size()) {
            return List.of();
        }
        int end = Math.min(list.size(), start + pageSize);
        return new ArrayList<>(list.subList(start, end));
    }

    public static <T> boolean anyMatch(Collection<T> value, Predicate<T> predicate) {
        if (isEmpty(value)) {
            return false;
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate is null");
        }
        for (T item : value) {
            if (predicate.test(item)) {
                return true;
            }
        }
        return false;
    }

    public static <T> boolean allMatch(Collection<T> value, Predicate<T> predicate) {
        if (isEmpty(value)) {
            return false;
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate is null");
        }
        for (T item : value) {
            if (!predicate.test(item)) {
                return false;
            }
        }
        return true;
    }

    public static <T> long count(Collection<T> value, Predicate<T> predicate) {
        if (isEmpty(value)) {
            return 0L;
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate is null");
        }
        long c = 0;
        for (T item : value) {
            if (predicate.test(item)) {
                c++;
            }
        }
        return c;
    }

    public static <T> boolean containsAny(Collection<T> left, Collection<T> right) {
        if (isEmpty(left) || isEmpty(right)) {
            return false;
        }
        Collection<T> small = left.size() <= right.size() ? left : right;
        Collection<T> large = small == left ? right : left;
        HashSet<T> set = new HashSet<>(large);
        for (T item : small) {
            if (set.contains(item)) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> reverseNew(List<T> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        ArrayList<T> out = new ArrayList<>(list);
        Collections.reverse(out);
        return out;
    }

    public static <T> void swap(List<T> list, int i, int j) {
        if (list == null) {
            throw new IllegalArgumentException("list is null");
        }
        Collections.swap(list, i, j);
    }

    public static <T> List<T> repeat(T value, int n) {
        if (n <= 0) {
            return List.of();
        }
        ArrayList<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(value);
        }
        return out;
    }

    public static <T> List<T> immutableCopy(List<T> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list);
    }

    public static <T> Set<T> immutableCopy(Set<T> set) {
        if (set == null || set.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(set);
    }

    public static <K, V> Map<K, V> immutableCopy(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(map);
    }

    private static int hashCapacity(int expectedSize) {
        if (expectedSize <= 0) {
            return 16;
        }
        int capacity = (int) ((float) expectedSize / 0.75f) + 1;
        return Math.max(16, capacity);
    }
}
