package yueyang.vostok.terminal.tool;

/**
 * Display width helpers with basic CJK width support.
 */
public final class VKTextWidth {
    private VKTextWidth() {
    }

    public static int width(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String plain = VKAnsi.strip(value);
        int out = 0;
        for (int i = 0; i < plain.length(); ) {
            int cp = plain.codePointAt(i);
            out += codePointWidth(cp);
            i += Character.charCount(cp);
        }
        return out;
    }

    public static String truncate(String value, int maxWidth) {
        return truncate(value, maxWidth, "...");
    }

    public static String truncate(String value, int maxWidth, String ellipsis) {
        if (value == null) {
            return "";
        }
        if (maxWidth <= 0) {
            return "";
        }
        String plain = VKAnsi.strip(value);
        if (width(plain) <= maxWidth) {
            return plain;
        }

        String e = ellipsis == null ? "" : ellipsis;
        int eWidth = width(e);
        int limit = Math.max(0, maxWidth - eWidth);

        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < plain.length(); ) {
            int cp = plain.codePointAt(i);
            int w = codePointWidth(cp);
            if (used + w > limit) {
                break;
            }
            sb.appendCodePoint(cp);
            used += w;
            i += Character.charCount(cp);
        }
        return sb.append(e).toString();
    }

    public static String padRight(String value, int width) {
        String v = value == null ? "" : value;
        int pad = Math.max(0, width - width(v));
        return v + " ".repeat(pad);
    }

    public static String padLeft(String value, int width) {
        String v = value == null ? "" : value;
        int pad = Math.max(0, width - width(v));
        return " ".repeat(pad) + v;
    }

    public static String center(String value, int width) {
        String v = value == null ? "" : value;
        int w = width(v);
        if (w >= width) {
            return v;
        }
        int total = width - w;
        int left = total / 2;
        int right = total - left;
        return " ".repeat(left) + v + " ".repeat(right);
    }

    private static int codePointWidth(int cp) {
        if (cp == 0) {
            return 0;
        }
        if (Character.isISOControl(cp)) {
            return 0;
        }
        if (cp >= 0x0300 && cp <= 0x036F) {
            return 0;
        }
        if (isWide(cp)) {
            return 2;
        }
        return 1;
    }

    private static boolean isWide(int cp) {
        return cp >= 0x1100 && (
                cp <= 0x115F
                        || cp == 0x2329
                        || cp == 0x232A
                        || (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F)
                        || (cp >= 0xAC00 && cp <= 0xD7A3)
                        || (cp >= 0xF900 && cp <= 0xFAFF)
                        || (cp >= 0xFE10 && cp <= 0xFE19)
                        || (cp >= 0xFE30 && cp <= 0xFE6F)
                        || (cp >= 0xFF00 && cp <= 0xFF60)
                        || (cp >= 0xFFE0 && cp <= 0xFFE6)
                        || (cp >= 0x1F300 && cp <= 0x1FAFF)
        );
    }
}
