package yueyang.vostok;

import org.junit.jupiter.api.Test;
import yueyang.vostok.util.string.VKStrings;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokUtilStringTest {
    @Test
    void testBlankAndTrim() {
        assertTrue(Vostok.Util.isBlank("  \n\t"));
        assertTrue(Vostok.Util.isEmpty(""));
        assertEquals("x", Vostok.Util.trimToNull(" x "));
        assertNull(Vostok.Util.trimToNull("  "));
        assertEquals("", Vostok.Util.trimToEmpty(null));
        assertEquals("fallback", Vostok.Util.defaultIfBlank(" ", "fallback"));
    }

    @Test
    void testFastSplitAndJoin() {
        assertArrayEquals(new String[]{"a", "b", "c"}, Vostok.Util.fastSplit("a,b,c", ','));
        assertArrayEquals(new String[]{""}, Vostok.Util.fastSplit("", ','));
        assertEquals("a,b,c", Vostok.Util.join(',', "a", "b", "c"));
    }

    @Test
    void testMatcherFunctions() {
        assertTrue(Vostok.Util.equalsIgnoreCaseAscii("ABc", "aBC"));
        assertTrue(Vostok.Util.containsIgnoreCaseAscii("HelloWorld", "WORLD"));
        assertTrue(Vostok.Util.startsWithIgnoreCaseAscii("Prefix", "pre"));
        assertTrue(Vostok.Util.wildcardMatch("hello.txt", "*.txt"));
        assertFalse(Vostok.Util.wildcardMatch("hello.txt", "*.csv"));
    }

    @Test
    void testReplaceAndCleanup() {
        assertEquals("a-b-c", Vostok.Util.replaceChar("a_b_c", '_', '-'));
        assertEquals("abc", Vostok.Util.removeWhitespace(" a b\n c\t"));
        assertEquals("a b c", Vostok.Util.collapseSpaces("  a\t b\n  c  "));
        assertEquals("ab\nc", Vostok.Util.stripControlChars("a\u0001b\nc\u0002"));
    }

    @Test
    void testTruncateAndSubstring() {
        assertEquals("abc", Vostok.Util.truncateByCodePoint("abcdef", 3));
        assertEquals("ab...", Vostok.Util.ellipsis("abcdef", 5));
        assertEquals("...", Vostok.Util.ellipsis("abcdef", 3));
        assertEquals("bc", Vostok.Util.safeSubstring("abcd", 1, 3));
        assertEquals("", Vostok.Util.safeSubstring("abcd", 5, 9));
    }

    @Test
    void testCaseConverters() {
        assertEquals("user_name", Vostok.Util.camelToSnake("userName"));
        assertEquals("userName", Vostok.Util.snakeToCamel("user_name"));
        assertEquals("userName", Vostok.Util.kebabToCamel("user-name"));
    }

    @Test
    void testUtf8AndUrl() {
        String raw = "A B+中";
        String encoded = Vostok.Util.urlEncodeUtf8(raw);
        assertEquals(raw, Vostok.Util.urlDecodeUtf8(encoded));

        byte[] bytes = Vostok.Util.toUtf8Bytes("hello");
        assertEquals("hello", Vostok.Util.fromUtf8Bytes(bytes));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void testParseHelpers() {
        assertEquals(12, Vostok.Util.toIntOrDefault(" 12 ", 1));
        assertEquals(1, Vostok.Util.toIntOrDefault("x", 1));
        assertEquals(99L, Vostok.Util.toLongOrDefault("99", 1L));
        assertEquals(1L, Vostok.Util.toLongOrDefault(null, 1L));
        assertTrue(Vostok.Util.toBoolOrDefault("yes", false));
        assertFalse(Vostok.Util.toBoolOrDefault("no", true));
        assertTrue(Vostok.Util.toBoolOrDefault("invalid", true));
    }

    @Test
    void testJsonViaUtilFacade() {
        String json = Vostok.Util.toJson(Map.of("name", "Tom", "age", 20));
        Map<?, ?> m = Vostok.Util.fromJson(json, Map.class);
        assertEquals("Tom", m.get("name"));
    }

    @Test
    void testVKStringsDirectSplitListAndJoinIterable() {
        assertEquals(List.of("a", "b", "c"), VKStrings.splitToList("a,b,c", ','));
        assertEquals("x|y|z", VKStrings.join('|', List.of("x", "y", "z")));
    }
}
