package yueyang.vostok.terminal.core;

import yueyang.vostok.terminal.VKTerminalTheme;
import yueyang.vostok.terminal.tool.VKAnsi;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendering context for terminal components.
 */
public final class VKRenderContext {
    private final int width;
    private final int height;
    private final boolean ansiEnabled;
    private final boolean unicodeEnabled;
    private final VKTerminalTheme theme;

    public VKRenderContext(int width, int height, boolean ansiEnabled, boolean unicodeEnabled, VKTerminalTheme theme) {
        this.width = Math.max(20, width);
        this.height = Math.max(5, height);
        this.ansiEnabled = ansiEnabled;
        this.unicodeEnabled = unicodeEnabled;
        this.theme = theme == null ? VKTerminalTheme.defaults() : theme;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean ansiEnabled() {
        return ansiEnabled;
    }

    public boolean unicodeEnabled() {
        return unicodeEnabled;
    }

    public VKTerminalTheme theme() {
        return theme;
    }

    public VKRenderContext child(int childWidth) {
        return new VKRenderContext(Math.max(10, childWidth), this.height, this.ansiEnabled, this.unicodeEnabled, this.theme);
    }

    public String fit(String line) {
        return VKTextWidth.truncate(line == null ? "" : line, width);
    }

    public List<String> fitLines(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            out.add(fit(line));
        }
        return out;
    }

    public String styleTitle(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getTitle());
    }

    public String styleMuted(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getMuted());
    }

    public String styleBorder(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getBorder());
    }

    public String styleSuccess(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getSuccess());
    }

    public String styleWarn(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getWarn());
    }

    public String styleError(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getError());
    }

    public String styleText(String text) {
        return VKAnsi.apply(text, ansiEnabled, theme.getText());
    }
}
