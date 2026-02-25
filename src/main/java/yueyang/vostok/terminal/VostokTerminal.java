package yueyang.vostok.terminal;

import yueyang.vostok.terminal.core.VKTerminalApp;
import yueyang.vostok.terminal.core.VKTerminalRuntime;
import yueyang.vostok.terminal.tool.VKConsole;
import yueyang.vostok.terminal.tool.VKProgressBar;
import yueyang.vostok.terminal.tool.VKSpinner;
import yueyang.vostok.terminal.tool.VKTablePrinter;

/**
 * Vostok Terminal entry.
 */
public class VostokTerminal {
    private static final VKTerminalRuntime RUNTIME = VKTerminalRuntime.getInstance();

    protected VostokTerminal() {
    }

    public static void init() {
        RUNTIME.init(new VKTerminalConfig());
    }

    public static void init(VKTerminalConfig config) {
        RUNTIME.init(config);
    }

    public static void reinit(VKTerminalConfig config) {
        RUNTIME.reinit(config);
    }

    public static boolean started() {
        return RUNTIME.started();
    }

    public static VKTerminalConfig config() {
        return RUNTIME.config();
    }

    public static VKTerminalTheme theme() {
        return RUNTIME.theme();
    }

    public static void useTheme(VKTerminalTheme theme) {
        RUNTIME.useTheme(theme);
    }

    public static VKTerminalApp app() {
        return RUNTIME.app();
    }

    public static VKTablePrinter table() {
        return new VKTablePrinter();
    }

    public static VKProgressBar progressBar() {
        return new VKProgressBar();
    }

    public static VKSpinner spinner() {
        return new VKSpinner();
    }

    public static VKConsole console() {
        VKTerminalConfig cfg = RUNTIME.config();
        return new VKConsole(cfg.getOutput(), cfg.isAnsiEnabled(), RUNTIME.theme());
    }

    public static void close() {
        RUNTIME.close();
    }
}
