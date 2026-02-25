package yueyang.vostok.terminal;

import yueyang.vostok.terminal.tool.VKAnsi;

/**
 * Theme tokens for terminal rendering.
 */
public final class VKTerminalTheme {
    private String title = VKAnsi.BOLD + VKAnsi.FG_CYAN;
    private String text = VKAnsi.FG_WHITE;
    private String muted = VKAnsi.FG_BRIGHT_BLACK;
    private String success = VKAnsi.FG_GREEN;
    private String warn = VKAnsi.FG_YELLOW;
    private String error = VKAnsi.FG_RED;
    private String border = VKAnsi.FG_BLUE;
    private String statusBg = VKAnsi.BG_BLUE;
    private String statusText = VKAnsi.FG_WHITE + VKAnsi.BOLD;

    public static VKTerminalTheme defaults() {
        return new VKTerminalTheme();
    }

    public static VKTerminalTheme ocean() {
        return new VKTerminalTheme()
                .title(VKAnsi.BOLD + VKAnsi.FG_BRIGHT_CYAN)
                .text(VKAnsi.FG_BRIGHT_WHITE)
                .muted(VKAnsi.FG_BRIGHT_BLACK)
                .success(VKAnsi.FG_BRIGHT_GREEN)
                .warn(VKAnsi.FG_BRIGHT_YELLOW)
                .error(VKAnsi.FG_BRIGHT_RED)
                .border(VKAnsi.FG_CYAN)
                .statusBg(VKAnsi.BG_CYAN)
                .statusText(VKAnsi.FG_BLACK + VKAnsi.BOLD);
    }

    public String getTitle() {
        return title;
    }

    public VKTerminalTheme title(String title) {
        if (title != null) {
            this.title = title;
        }
        return this;
    }

    public String getText() {
        return text;
    }

    public VKTerminalTheme text(String text) {
        if (text != null) {
            this.text = text;
        }
        return this;
    }

    public String getMuted() {
        return muted;
    }

    public VKTerminalTheme muted(String muted) {
        if (muted != null) {
            this.muted = muted;
        }
        return this;
    }

    public String getSuccess() {
        return success;
    }

    public VKTerminalTheme success(String success) {
        if (success != null) {
            this.success = success;
        }
        return this;
    }

    public String getWarn() {
        return warn;
    }

    public VKTerminalTheme warn(String warn) {
        if (warn != null) {
            this.warn = warn;
        }
        return this;
    }

    public String getError() {
        return error;
    }

    public VKTerminalTheme error(String error) {
        if (error != null) {
            this.error = error;
        }
        return this;
    }

    public String getBorder() {
        return border;
    }

    public VKTerminalTheme border(String border) {
        if (border != null) {
            this.border = border;
        }
        return this;
    }

    public String getStatusBg() {
        return statusBg;
    }

    public VKTerminalTheme statusBg(String statusBg) {
        if (statusBg != null) {
            this.statusBg = statusBg;
        }
        return this;
    }

    public String getStatusText() {
        return statusText;
    }

    public VKTerminalTheme statusText(String statusText) {
        if (statusText != null) {
            this.statusText = statusText;
        }
        return this;
    }

    public VKTerminalTheme copy() {
        return new VKTerminalTheme()
                .title(this.title)
                .text(this.text)
                .muted(this.muted)
                .success(this.success)
                .warn(this.warn)
                .error(this.error)
                .border(this.border)
                .statusBg(this.statusBg)
                .statusText(this.statusText);
    }
}
