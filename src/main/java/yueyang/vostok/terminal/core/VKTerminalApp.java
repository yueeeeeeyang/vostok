package yueyang.vostok.terminal.core;

import yueyang.vostok.terminal.VKTerminalConfig;
import yueyang.vostok.terminal.VKTerminalTheme;
import yueyang.vostok.terminal.component.VKModal;
import yueyang.vostok.terminal.component.VKStatusBar;
import yueyang.vostok.terminal.component.VKToast;
import yueyang.vostok.terminal.component.VKView;
import yueyang.vostok.terminal.event.VKInputDecoder;
import yueyang.vostok.terminal.event.VKKey;
import yueyang.vostok.terminal.event.VKKeyEvent;
import yueyang.vostok.terminal.tool.VKAnsi;
import yueyang.vostok.terminal.tool.VKTerminal;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Terminal application runtime.
 */
public final class VKTerminalApp {
    private final VKTerminalConfig config;
    private final VKTerminalTheme theme;
    private final List<Consumer<VKKeyEvent>> keyListeners = new ArrayList<>();

    private VKView root;
    private VKStatusBar statusBar;
    private VKToast toast;
    private VKModal modal;

    public VKTerminalApp(VKTerminalConfig config, VKTerminalTheme theme) {
        this.config = config == null ? new VKTerminalConfig() : config.copy();
        this.theme = theme == null ? VKTerminalTheme.defaults() : theme.copy();
    }

    public VKTerminalApp root(VKView root) {
        this.root = root;
        return this;
    }

    public VKTerminalApp statusBar(VKStatusBar statusBar) {
        this.statusBar = statusBar;
        return this;
    }

    public VKTerminalApp toast(VKToast toast) {
        this.toast = toast;
        return this;
    }

    public VKTerminalApp modal(VKModal modal) {
        this.modal = modal;
        return this;
    }

    public VKTerminalApp onKey(Consumer<VKKeyEvent> listener) {
        if (listener != null) {
            keyListeners.add(listener);
        }
        return this;
    }

    public String render() {
        VKTerminal.Size size = VKTerminal.size(config);
        boolean ansi = VKTerminal.supportsAnsi(config);
        VKRenderContext ctx = new VKRenderContext(size.columns(), size.rows(), ansi, config.isUnicodeEnabled(), theme);

        List<String> lines = new ArrayList<>();
        if (root != null) {
            lines.addAll(root.render(ctx));
        }
        if (modal != null && modal.isVisible()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.addAll(modal.render(ctx));
        }
        if (toast != null) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.addAll(toast.render(ctx));
        }
        if (statusBar != null) {
            lines.addAll(statusBar.render(ctx));
        }

        int maxLines = Math.max(1, size.rows());
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
        }
        return String.join("\n", ctx.fitLines(lines));
    }

    public void run() {
        PrintStream out = config.getOutput();
        boolean tty = VKTerminal.isTty(config);
        boolean ansi = VKTerminal.supportsAnsi(config);
        boolean useAlt = tty && ansi && config.isAlternateScreen();
        boolean hideCursor = tty && ansi && config.isHideCursor();

        try {
            if (useAlt) {
                out.print(VKAnsi.enterAltScreen());
            }
            if (hideCursor) {
                out.print(VKAnsi.hideCursor());
            }
            if (ansi) {
                out.print(VKAnsi.clearScreen());
            }
            out.print(render());
            out.print("\n");
            out.flush();

            if (config.isInteractive()) {
                readSingleEvent();
            }
        } finally {
            if (hideCursor) {
                out.print(VKAnsi.showCursor());
            }
            if (useAlt) {
                out.print(VKAnsi.exitAltScreen());
            }
            out.flush();
        }
    }

    private void readSingleEvent() {
        VKInputDecoder decoder = new VKInputDecoder();
        VKKeyEvent event = decoder.readEvent(config.getInput());
        for (Consumer<VKKeyEvent> listener : keyListeners) {
            listener.accept(event);
        }
        if (event.key() == VKKey.CTRL_C) {
            throw new IllegalStateException("Terminal app interrupted by Ctrl+C");
        }
    }
}
