package yueyang.vostok.util.string;

public final class VKStringMatcher {
    private VKStringMatcher() {
    }

    public static boolean equalsIgnoreCaseAscii(String a, String b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (toLowerAscii(a.charAt(i)) != toLowerAscii(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean startsWithIgnoreCaseAscii(String value, String prefix) {
        if (value == null || prefix == null || prefix.length() > value.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (toLowerAscii(value.charAt(i)) != toLowerAscii(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsIgnoreCaseAscii(String value, String part) {
        if (value == null || part == null) {
            return false;
        }
        if (part.isEmpty()) {
            return true;
        }
        int limit = value.length() - part.length();
        for (int i = 0; i <= limit; i++) {
            int j = 0;
            while (j < part.length() && toLowerAscii(value.charAt(i + j)) == toLowerAscii(part.charAt(j))) {
                j++;
            }
            if (j == part.length()) {
                return true;
            }
        }
        return false;
    }

    public static boolean wildcardMatch(String value, String pattern) {
        if (value == null || pattern == null) {
            return false;
        }
        int v = 0;
        int p = 0;
        int star = -1;
        int match = 0;
        while (v < value.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == value.charAt(v))) {
                p++;
                v++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                match = v;
            } else if (star != -1) {
                p = star + 1;
                v = ++match;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    private static char toLowerAscii(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        return c;
    }
}
