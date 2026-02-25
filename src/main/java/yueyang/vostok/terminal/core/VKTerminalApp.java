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
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Terminal application runtime.
 */
public final class VKTerminalApp {
    private final VKTerminalConfig config;
    private final VKTerminalTheme theme;
    private final VKInputDecoder decoder = new VKInputDecoder();
    private final List<Consumer<VKKeyEvent>> keyListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Long>> tickListeners = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> uiTasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong streamSeq = new AtomicLong();

    private final List<VKView> focusables = new ArrayList<>();
    private int focusIndex = -1;

    private volatile boolean dirty = true;

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
        this.dirty = true;
        refreshFocusables();
        return this;
    }

    public VKTerminalApp statusBar(VKStatusBar statusBar) {
        this.statusBar = statusBar;
        this.dirty = true;
        return this;
    }

    public VKTerminalApp toast(VKToast toast) {
        this.toast = toast;
        this.dirty = true;
        return this;
    }

    public VKTerminalApp modal(VKModal modal) {
        this.modal = modal;
        this.dirty = true;
        return this;
    }

    public VKTerminalApp onKey(Consumer<VKKeyEvent> listener) {
        if (listener != null) {
            keyListeners.add(listener);
        }
        return this;
    }

    public VKTerminalApp onTick(Consumer<Long> listener) {
        if (listener != null) {
            tickListeners.add(listener);
        }
        return this;
    }

    public void invokeLater(Runnable task) {
        if (task != null) {
            uiTasks.offer(task);
            requestRender();
        }
    }

    public void requestRender() {
        this.dirty = true;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void stop() {
        running.set(false);
    }

    public Thread streamText(String text, long tokenIntervalMs, Consumer<String> onToken, Runnable onDone) {
        Objects.requireNonNull(onToken, "onToken is null");
        String payload = text == null ? "" : text;
        long interval = Math.max(0L, tokenIntervalMs);
        String threadName = "vostok-terminal-stream-" + streamSeq.incrementAndGet();

        Thread t = new Thread(() -> {
            for (int i = 0; i < payload.length(); ) {
                int cp = payload.codePointAt(i);
                String token = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                invokeLater(() -> onToken.accept(token));
                if (interval > 0) {
                    sleepQuietly(interval);
                }
            }
            if (onDone != null) {
                invokeLater(onDone);
            }
        }, threadName);
        t.setDaemon(true);
        t.start();
        return t;
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
        if (config.isContinuousLoop()) {
            runLoop();
        } else {
            runSingleFrame();
        }
    }

    public void runLoop() {
        PrintStream out = config.getOutput();
        boolean tty = VKTerminal.isTty(config);
        boolean ansi = VKTerminal.supportsAnsi(config);
        boolean useAlt = tty && ansi && config.isAlternateScreen();
        boolean hideCursor = tty && ansi && config.isHideCursor();
        VKTerminalRawMode.RawState rawState = VKTerminalRawMode.RawState.disabled();

        long frameIntervalMs = Math.max(1L, 1000L / Math.max(1, config.getFps()));
        long pollMs = Math.max(1L, config.getInputPollIntervalMs());
        long tick = 0L;
        long lastRenderAt = 0L;

        running.set(true);
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
            if (tty && config.isRawMode()) {
                rawState = VKTerminalRawMode.enable();
            }

            refreshFocusables();
            while (running.get()) {
                drainUiTasks();
                refreshFocusables();

                if (config.isInteractive()) {
                    VKKeyEvent event = decoder.pollEvent(config.getInput());
                    if (event != null) {
                        dispatchKey(event);
                    }
                }

                for (Consumer<Long> tickListener : tickListeners) {
                    tickListener.accept(tick);
                }

                long now = System.currentTimeMillis();
                if (dirty || now - lastRenderAt >= frameIntervalMs) {
                    drawFrame(out, ansi);
                    dirty = false;
                    lastRenderAt = now;
                }

                tick++;
                sleepQuietly(Math.min(frameIntervalMs, pollMs));
            }
        } finally {
            running.set(false);
            if (tty && config.isRawMode()) {
                VKTerminalRawMode.restore(rawState);
            }
            if (hideCursor) {
                out.print(VKAnsi.showCursor());
            }
            if (useAlt) {
                out.print(VKAnsi.exitAltScreen());
            }
            out.flush();
        }
    }

    public void runSingleFrame() {
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
            drawFrame(out, ansi);

            if (config.isInteractive()) {
                VKKeyEvent event = decoder.readEvent(config.getInput());
                dispatchKey(event);
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

    private void drawFrame(PrintStream out, boolean ansi) {
        String frame = render();
        if (ansi) {
            out.print(VKAnsi.moveCursor(1, 1));
            out.print(frame);
            // Clear tail to avoid stale lines from previous longer frames.
            out.print(VKAnsi.ESC + "0J");
        } else {
            out.print(frame);
            out.print("\n");
        }
        out.flush();
    }

    private void dispatchKey(VKKeyEvent event) {
        if (event == null) {
            return;
        }
        if (event.key() == VKKey.CTRL_C) {
            stop();
            return;
        }
        if (event.key() == VKKey.TAB) {
            focusNext();
            requestRender();
            return;
        }

        boolean consumed = false;
        VKView focused = currentFocusedView();
        if (focused != null) {
            consumed = focused.onKey(event);
        }

        for (Consumer<VKKeyEvent> listener : keyListeners) {
            listener.accept(event);
        }
        if (consumed) {
            requestRender();
        }
    }

    private void refreshFocusables() {
        VKView previous = currentFocusedView();

        focusables.clear();
        collectFocusable(root, focusables);
        if (focusables.isEmpty()) {
            focusIndex = -1;
            return;
        }

        if (previous != null) {
            int idx = focusables.indexOf(previous);
            if (idx >= 0) {
                setFocusIndex(idx);
                return;
            }
        }
        if (focusIndex < 0 || focusIndex >= focusables.size()) {
            setFocusIndex(0);
        } else {
            setFocusIndex(focusIndex);
        }
    }

    private VKView currentFocusedView() {
        if (focusIndex < 0 || focusIndex >= focusables.size()) {
            return null;
        }
        return focusables.get(focusIndex);
    }

    private void setFocusIndex(int index) {
        int target = Math.max(0, Math.min(focusables.size() - 1, index));
        VKView old = currentFocusedView();
        if (old != null) {
            old.focused(false);
        }
        focusIndex = target;
        VKView now = currentFocusedView();
        if (now != null) {
            now.focused(true);
        }
    }

    private void focusNext() {
        if (focusables.isEmpty()) {
            return;
        }
        int next = (focusIndex + 1) % focusables.size();
        setFocusIndex(next);
    }

    private void collectFocusable(VKView node, List<VKView> out) {
        if (node == null) {
            return;
        }
        if (node.focusable()) {
            out.add(node);
        }
        for (VKView child : node.children()) {
            collectFocusable(child, out);
        }
    }

    private void drainUiTasks() {
        while (true) {
            Runnable task = uiTasks.poll();
            if (task == null) {
                break;
            }
            try {
                task.run();
            } catch (Exception ignore) {
                // ignore ui task failures
            }
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(Math.max(1L, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
        }
    }
}
