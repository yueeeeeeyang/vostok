package yueyang.vostok.terminal.tool;

import java.util.regex.Pattern;

/**
 * ANSI helpers.
 */
public final class VKAnsi {
    public static final String ESC = "\u001B[";
    public static final String RESET = ESC + "0m";
    public static final String BOLD = ESC + "1m";
    public static final String DIM = ESC + "2m";
    public static final String UNDERLINE = ESC + "4m";

    public static final String FG_BLACK = ESC + "30m";
    public static final String FG_RED = ESC + "31m";
    public static final String FG_GREEN = ESC + "32m";
    public static final String FG_YELLOW = ESC + "33m";
    public static final String FG_BLUE = ESC + "34m";
    public static final String FG_MAGENTA = ESC + "35m";
    public static final String FG_CYAN = ESC + "36m";
    public static final String FG_WHITE = ESC + "37m";

    public static final String FG_BRIGHT_BLACK = ESC + "90m";
    public static final String FG_BRIGHT_RED = ESC + "91m";
    public static final String FG_BRIGHT_GREEN = ESC + "92m";
    public static final String FG_BRIGHT_YELLOW = ESC + "93m";
    public static final String FG_BRIGHT_BLUE = ESC + "94m";
    public static final String FG_BRIGHT_MAGENTA = ESC + "95m";
    public static final String FG_BRIGHT_CYAN = ESC + "96m";
    public static final String FG_BRIGHT_WHITE = ESC + "97m";

    public static final String BG_BLUE = ESC + "44m";
    public static final String BG_CYAN = ESC + "46m";

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\u001B\\[[;\\d]*m");

    private VKAnsi() {
    }

    public static String apply(String text, boolean enabled, String... styles) {
        if (!enabled || text == null) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        if (styles != null) {
            for (String style : styles) {
                if (style != null) {
                    sb.append(style);
                }
            }
        }
        sb.append(text).append(RESET);
        return sb.toString();
    }

    public static String fgRgb(int r, int g, int b) {
        return ESC + "38;2;" + clampColor(r) + ";" + clampColor(g) + ";" + clampColor(b) + "m";
    }

    public static String bgRgb(int r, int g, int b) {
        return ESC + "48;2;" + clampColor(r) + ";" + clampColor(g) + ";" + clampColor(b) + "m";
    }

    public static String clearScreen() {
        return ESC + "2J" + ESC + "H";
    }

    public static String moveCursor(int row, int col) {
        int r = Math.max(1, row);
        int c = Math.max(1, col);
        return ESC + r + ";" + c + "H";
    }

    public static String hideCursor() {
        return ESC + "?25l";
    }

    public static String showCursor() {
        return ESC + "?25h";
    }

    public static String enterAltScreen() {
        return ESC + "?1049h";
    }

    public static String exitAltScreen() {
        return ESC + "?1049l";
    }

    public static String strip(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return ANSI_PATTERN.matcher(value).replaceAll("");
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
