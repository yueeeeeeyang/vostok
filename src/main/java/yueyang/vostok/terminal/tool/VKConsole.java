package yueyang.vostok.terminal.tool;

import yueyang.vostok.terminal.VKTerminalTheme;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Styled console output helper.
 */
public final class VKConsole {
    private final PrintStream out;
    private final boolean ansiEnabled;
    private final VKTerminalTheme theme;
    private boolean timestampEnabled = true;

    public VKConsole(PrintStream out, boolean ansiEnabled, VKTerminalTheme theme) {
        this.out = out;
        this.ansiEnabled = ansiEnabled;
        this.theme = theme == null ? VKTerminalTheme.defaults() : theme;
    }

    public VKConsole timestampEnabled(boolean timestampEnabled) {
        this.timestampEnabled = timestampEnabled;
        return this;
    }

    public void info(String message) {
        print("INFO", message, theme.getText());
    }

    public void success(String message) {
        print("OK", message, theme.getSuccess());
    }

    public void warn(String message) {
        print("WARN", message, theme.getWarn());
    }

    public void error(String message) {
        print("ERROR", message, theme.getError());
    }

    private void print(String level, String message, String style) {
        String time = timestampEnabled
                ? (LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " ")
                : "";
        String plain = time + "[" + level + "] " + (message == null ? "" : message);
        out.println(VKAnsi.apply(plain, ansiEnabled, style));
    }
}
