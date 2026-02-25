package yueyang.vostok.terminal;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Runtime config for terminal module.
 */
public final class VKTerminalConfig {
    private String appName = "Vostok Terminal";
    private int fps = 20;
    private boolean alternateScreen = true;
    private boolean hideCursor = true;
    private boolean ansiEnabled = true;
    private boolean unicodeEnabled = true;
    private boolean interactive = false;
    private boolean forceTty = false;
    private int width = 100;
    private int height = 30;
    private InputStream input = System.in;
    private PrintStream output = System.out;
    private VKTerminalTheme theme = VKTerminalTheme.defaults();

    public String getAppName() {
        return appName;
    }

    public VKTerminalConfig appName(String appName) {
        if (appName != null && !appName.isBlank()) {
            this.appName = appName;
        }
        return this;
    }

    public int getFps() {
        return fps;
    }

    public VKTerminalConfig fps(int fps) {
        this.fps = Math.max(1, fps);
        return this;
    }

    public boolean isAlternateScreen() {
        return alternateScreen;
    }

    public VKTerminalConfig alternateScreen(boolean alternateScreen) {
        this.alternateScreen = alternateScreen;
        return this;
    }

    public boolean isHideCursor() {
        return hideCursor;
    }

    public VKTerminalConfig hideCursor(boolean hideCursor) {
        this.hideCursor = hideCursor;
        return this;
    }

    public boolean isAnsiEnabled() {
        return ansiEnabled;
    }

    public VKTerminalConfig ansiEnabled(boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
        return this;
    }

    public boolean isUnicodeEnabled() {
        return unicodeEnabled;
    }

    public VKTerminalConfig unicodeEnabled(boolean unicodeEnabled) {
        this.unicodeEnabled = unicodeEnabled;
        return this;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public VKTerminalConfig interactive(boolean interactive) {
        this.interactive = interactive;
        return this;
    }

    public boolean isForceTty() {
        return forceTty;
    }

    public VKTerminalConfig forceTty(boolean forceTty) {
        this.forceTty = forceTty;
        return this;
    }

    public int getWidth() {
        return width;
    }

    public VKTerminalConfig width(int width) {
        this.width = Math.max(40, width);
        return this;
    }

    public int getHeight() {
        return height;
    }

    public VKTerminalConfig height(int height) {
        this.height = Math.max(10, height);
        return this;
    }

    public InputStream getInput() {
        return input;
    }

    public VKTerminalConfig input(InputStream input) {
        if (input != null) {
            this.input = input;
        }
        return this;
    }

    public PrintStream getOutput() {
        return output;
    }

    public VKTerminalConfig output(PrintStream output) {
        if (output != null) {
            this.output = output;
        }
        return this;
    }

    public VKTerminalTheme getTheme() {
        return theme;
    }

    public VKTerminalConfig theme(VKTerminalTheme theme) {
        if (theme != null) {
            this.theme = theme;
        }
        return this;
    }

    public VKTerminalConfig copy() {
        return new VKTerminalConfig()
                .appName(this.appName)
                .fps(this.fps)
                .alternateScreen(this.alternateScreen)
                .hideCursor(this.hideCursor)
                .ansiEnabled(this.ansiEnabled)
                .unicodeEnabled(this.unicodeEnabled)
                .interactive(this.interactive)
                .forceTty(this.forceTty)
                .width(this.width)
                .height(this.height)
                .input(this.input)
                .output(this.output)
                .theme(this.theme == null ? VKTerminalTheme.defaults() : this.theme.copy());
    }
}
