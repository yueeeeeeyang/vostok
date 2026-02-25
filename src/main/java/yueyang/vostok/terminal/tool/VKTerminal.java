package yueyang.vostok.terminal.tool;

import yueyang.vostok.terminal.VKTerminalConfig;

import java.util.Locale;
import java.util.Map;

/**
 * Terminal capability probes.
 */
public final class VKTerminal {
    private VKTerminal() {
    }

    public static boolean isTty(VKTerminalConfig config) {
        if (config != null && config.isForceTty()) {
            return true;
        }
        return System.console() != null;
    }

    public static boolean supportsAnsi(VKTerminalConfig config) {
        if (config != null && !config.isAnsiEnabled()) {
            return false;
        }
        Map<String, String> env = System.getenv();
        if (env.containsKey("NO_COLOR")) {
            return false;
        }
        String term = env.getOrDefault("TERM", "");
        if ("dumb".equalsIgnoreCase(term)) {
            return false;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return true;
        }
        return env.containsKey("WT_SESSION")
                || env.containsKey("ANSICON")
                || "ON".equalsIgnoreCase(env.getOrDefault("ConEmuANSI", ""));
    }

    public static Size size(VKTerminalConfig config) {
        int fallbackColumns = config == null ? 100 : config.getWidth();
        int fallbackRows = config == null ? 30 : config.getHeight();

        int columns = parsePositive(System.getenv("COLUMNS"), fallbackColumns);
        int rows = parsePositive(System.getenv("LINES"), fallbackRows);
        return new Size(columns, rows);
    }

    private static int parsePositive(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    public record Size(int columns, int rows) {
        public Size {
            columns = Math.max(20, columns);
            rows = Math.max(5, rows);
        }
    }
}
