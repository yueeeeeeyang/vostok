package yueyang.vostok;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokUtilExtraToolsTest {
    @Test
    void testCollectionsTools() {
        assertTrue(Vostok.Util.isEmpty((List<?>) null));
        assertTrue(Vostok.Util.isEmpty(List.of()));
        assertTrue(Vostok.Util.isEmpty((Map<?, ?>) null));
        assertTrue(Vostok.Util.isEmpty(Map.of()));

        var map = Vostok.Util.<String, Integer>newHashMapWithExpectedSize(100);
        map.put("a", 1);
        assertEquals(1, map.get("a"));

        List<String> data = List.of("x", "y", "x", "z");
        assertEquals("y", Vostok.Util.safeGet(data, 1, "d"));
        assertEquals("d", Vostok.Util.safeGet(data, 9, "d"));
        assertEquals(List.of("x", "y", "z"), Vostok.Util.distinctPreserveOrder(data));
    }

    @Test
    void testCollectionCapacityBuilders() {
        var arr = Vostok.Util.<String>newArrayListWithExpectedSize(8);
        arr.add("a");
        assertEquals(1, arr.size());

        var set = Vostok.Util.<String>newHashSetWithExpectedSize(8);
        set.add("x");
        assertTrue(set.contains("x"));

        var linkedMap = Vostok.Util.<String, Integer>newLinkedHashMapWithExpectedSize(8);
        linkedMap.put("k", 1);
        assertEquals(1, linkedMap.get("k"));

        var chm = Vostok.Util.<String, Integer>newConcurrentHashMapWithExpectedSize(8);
        chm.put("k", 2);
        assertEquals(2, chm.get("k"));
    }

    @Test
    void testSafeReadAndDistinctBy() {
        List<String> data = List.of("a", "b", "c");
        assertEquals("a", Vostok.Util.safeFirst(data, "x"));
        assertEquals("c", Vostok.Util.safeLast(data, "x"));
        assertEquals("x", Vostok.Util.safeFirst(List.of(), "x"));
        assertEquals(List.of("b", "c"), Vostok.Util.safeSubList(data, 1, 9));
        assertEquals(List.of(), Vostok.Util.safeSubList(data, 5, 8));

        assertEquals(99, Vostok.Util.getOrDefault(Map.<String, Integer>of("a", 1), "b", 99));
        assertEquals(88, Vostok.Util.getOrDefault((Map<String, Integer>) null, "a", 88));

        List<String> d = List.of("a1", "a2", "b1", "b2");
        assertEquals(List.of("a1", "b1"), Vostok.Util.distinctBy(d, s -> s.substring(0, 1)));
    }

    @Test
    void testSetOpsAndTransform() {
        List<String> a = List.of("a", "b", "c", "a");
        List<String> b = List.of("b", "d", "a");
        assertEquals(List.of("a", "b", "c", "d"), Vostok.Util.union(a, b));
        assertEquals(List.of("a", "b"), Vostok.Util.intersect(a, b));
        assertEquals(List.of("c"), Vostok.Util.difference(List.of("a", "b", "c"), List.of("a", "b")));

        assertEquals(List.of("b", "c"), Vostok.Util.filter(List.of("a", "b", "c"), s -> !s.equals("a")));
        assertEquals(List.of(1, 2, 3), Vostok.Util.map(List.of("a", "bb", "ccc"), String::length));
        assertEquals(List.of("a", "A", "b", "B"), Vostok.Util.flatMap(List.of("a", "b"), s -> List.of(s, s.toUpperCase())));
        assertEquals(List.of("a", "b"), Vostok.Util.compact(new ArrayList<>(Arrays.asList("a", "b", null))));
    }

    @Test
    void testIndexGroupToMapAndPaging() {
        List<String> data = List.of("aa", "ab", "ba");
        assertEquals("ba", Vostok.Util.indexBy(data, s -> s.substring(0, 1)).get("b"));
        assertEquals(List.of("aa", "ab"), Vostok.Util.groupBy(data, s -> s.substring(0, 1)).get("a"));

        Map<String, Integer> m = Vostok.Util.toMap(List.of("a", "a", "b"), v -> v, v -> 1, Integer::sum);
        assertEquals(2, m.get("a"));
        assertEquals(1, m.get("b"));

        assertEquals(List.of(List.of(1, 2), List.of(3, 4), List.of(5)),
                Vostok.Util.partition(List.of(1, 2, 3, 4, 5), 2));
        assertEquals(List.of(3, 4), Vostok.Util.page(List.of(1, 2, 3, 4, 5), 2, 2));
        assertEquals(List.of(), Vostok.Util.page(List.of(1, 2, 3), 9, 2));
    }

    @Test
    void testPredicateCountAndSmallTools() {
        List<Integer> nums = List.of(1, 2, 3, 4);
        assertTrue(Vostok.Util.anyMatch(nums, n -> n > 3));
        assertTrue(Vostok.Util.allMatch(nums, n -> n > 0));
        assertEquals(2L, Vostok.Util.count(nums, n -> (n % 2) == 0));
        assertTrue(Vostok.Util.containsAny(List.of("a", "b"), List.of("x", "b")));

        assertEquals(List.of(3, 2, 1), Vostok.Util.reverseNew(List.of(1, 2, 3)));

        ArrayList<String> list = new ArrayList<>(List.of("a", "b", "c"));
        Vostok.Util.swap(list, 0, 2);
        assertEquals(List.of("c", "b", "a"), list);

        assertEquals(List.of("x", "x", "x"), Vostok.Util.repeat("x", 3));
        assertEquals(List.of(), Vostok.Util.repeat("x", 0));

        List<String> immutableList = Vostok.Util.immutableCopy(List.of("a", "b"));
        Set<String> immutableSet = Vostok.Util.immutableCopy(Set.of("a", "b"));
        Map<String, Integer> immutableMap = Vostok.Util.immutableCopy(Map.of("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> immutableList.add("c"));
        assertThrows(UnsupportedOperationException.class, () -> immutableSet.add("c"));
        assertThrows(UnsupportedOperationException.class, () -> immutableMap.put("b", 2));
    }

    @Test
    void testTimeTools() {
        long nowMs = Vostok.Util.nowEpochMs();
        long nowSec = Vostok.Util.nowEpochSec();
        assertTrue(nowMs > 0);
        assertTrue(nowSec > 0);

        Instant instant = Instant.parse("2026-02-20T15:30:00Z");
        ZoneId zone = ZoneId.of("UTC");
        String text = Vostok.Util.formatInstant(instant, zone, "yyyy-MM-dd HH:mm:ss");
        assertEquals("2026-02-20 15:30:00", text);
        assertEquals(instant, Vostok.Util.parseInstant(text, zone, "yyyy-MM-dd HH:mm:ss"));

        assertEquals(Instant.parse("2026-02-20T00:00:00Z"), Vostok.Util.startOfDay(instant, zone));
        assertEquals(Instant.parse("2026-02-20T23:59:59.999999999Z"), Vostok.Util.endOfDay(instant, zone));
    }

    @Test
    void testIdAndRandomTools() {
        String uuid = Vostok.Util.uuid();
        assertNotNull(uuid);
        assertTrue(uuid.length() >= 36);

        String r = Vostok.Util.randomAlphaNum(24);
        assertEquals(24, r.length());
        assertTrue(r.matches("[0-9A-Za-z]+"));

        String traceId = Vostok.Util.traceId();
        assertNotNull(traceId);
        assertTrue(traceId.contains("-"));
        assertFalse(traceId.isBlank());
        assertNotEquals(traceId, Vostok.Util.traceId());
    }

    @Test
    void testCodecTools() {
        byte[] raw = "hello".getBytes(StandardCharsets.UTF_8);

        String b64 = Vostok.Util.base64Encode(raw);
        assertEquals("aGVsbG8=", b64);
        assertArrayEquals(raw, Vostok.Util.base64DecodeToBytes(b64));
        assertEquals("hello", Vostok.Util.base64DecodeToString(Vostok.Util.base64Encode("hello")));

        String hex = Vostok.Util.hexEncode(raw);
        assertEquals("68656c6c6f", hex);
        assertArrayEquals(raw, Vostok.Util.hexDecode(hex));

        assertEquals(907060870L, Vostok.Util.crc32(raw));
        assertEquals("5d41402abc4b2a76b9719d911017c592", Vostok.Util.md5Hex("hello"));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", Vostok.Util.sha256Hex("hello"));
    }
}
