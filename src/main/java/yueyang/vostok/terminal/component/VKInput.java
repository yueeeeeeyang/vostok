package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.event.VKKey;
import yueyang.vostok.terminal.event.VKKeyEvent;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.util.List;

public final class VKInput extends VKView {
    private String label;
    private String value = "";
    private String placeholder = "";
    private boolean secret;
    private boolean focused;
    private int cursor;
    private int maxLength = 2048;

    public VKInput(String label) {
        this.label = label == null ? "" : label;
    }

    public VKInput label(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    public VKInput value(String value) {
        this.value = value == null ? "" : value;
        this.cursor = Math.min(this.cursor, this.value.length());
        return this;
    }

    public String value() {
        return value;
    }

    public VKInput placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public VKInput secret(boolean secret) {
        this.secret = secret;
        return this;
    }

    public VKInput maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        if (value.length() > this.maxLength) {
            value = value.substring(0, this.maxLength);
            cursor = Math.min(cursor, value.length());
        }
        return this;
    }

    public int cursor() {
        return cursor;
    }

    public VKInput cursor(int cursor) {
        this.cursor = Math.max(0, Math.min(value.length(), cursor));
        return this;
    }

    public VKInput clear() {
        this.value = "";
        this.cursor = 0;
        return this;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void focused(boolean focused) {
        this.focused = focused;
        this.cursor = Math.max(0, Math.min(value.length(), this.cursor));
    }

    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean onKey(VKKeyEvent event) {
        if (event == null || !focused) {
            return false;
        }
        VKKey key = event.key();
        switch (key) {
            case CHAR -> {
                char c = event.ch();
                if (c >= 32 && value.length() < maxLength) {
                    value = value.substring(0, cursor) + c + value.substring(cursor);
                    cursor++;
                    return true;
                }
                return false;
            }
            case BACKSPACE -> {
                if (cursor > 0 && !value.isEmpty()) {
                    value = value.substring(0, cursor - 1) + value.substring(cursor);
                    cursor--;
                    return true;
                }
                return false;
            }
            case DELETE -> {
                if (cursor < value.length()) {
                    value = value.substring(0, cursor) + value.substring(cursor + 1);
                    return true;
                }
                return false;
            }
            case LEFT -> {
                if (cursor > 0) {
                    cursor--;
                    return true;
                }
                return false;
            }
            case RIGHT -> {
                if (cursor < value.length()) {
                    cursor++;
                    return true;
                }
                return false;
            }
            case HOME -> {
                if (cursor != 0) {
                    cursor = 0;
                    return true;
                }
                return false;
            }
            case END -> {
                if (cursor != value.length()) {
                    cursor = value.length();
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        String prefix = (label == null || label.isBlank()) ? "" : (label + ": ");
        String raw = value.isBlank() ? placeholder : value;
        String visible = maskIfSecret(raw, value.isBlank());

        int visualCursor = value.isBlank() ? 0 : cursor;
        if (focused) {
            visible = injectCursor(visible, visualCursor, value.isBlank());
        }

        int maxWidth = Math.max(1, ctx.width() - VKTextWidth.width(prefix));
        String fitted = fitEditable(visible, maxWidth, focused ? visualCursor : -1);
        String line = prefix + fitted;

        if (focused) {
            return List.of(ctx.styleSuccess(ctx.fit(line)));
        }
        if (value.isBlank() && !placeholder.isBlank()) {
            return List.of(ctx.styleMuted(ctx.fit(line)));
        }
        return List.of(ctx.styleText(ctx.fit(line)));
    }

    private String maskIfSecret(String shown, boolean isPlaceholder) {
        if (!secret || shown.isBlank() || isPlaceholder) {
            return shown;
        }
        return "*".repeat(shown.length());
    }

    private String injectCursor(String shown, int pos, boolean isPlaceholder) {
        if (shown == null) {
            return "|";
        }
        int p = Math.max(0, Math.min(shown.length(), pos));
        if (isPlaceholder) {
            p = 0;
        }
        return shown.substring(0, p) + "|" + shown.substring(p);
    }

    private String fitEditable(String shown, int maxWidth, int cursorPos) {
        if (VKTextWidth.width(shown) <= maxWidth) {
            return VKTextWidth.padRight(shown, maxWidth);
        }
        if (cursorPos < 0) {
            return VKTextWidth.truncate(shown, maxWidth);
        }

        int safeCursor = Math.max(0, Math.min(shown.length(), cursorPos + 1));
        int start = Math.max(0, safeCursor - Math.max(4, maxWidth / 2));
        String slice = shown.substring(start);
        if (VKTextWidth.width(slice) > maxWidth) {
            slice = VKTextWidth.truncate(slice, maxWidth);
        }
        if (start > 0 && !slice.isEmpty()) {
            slice = "…" + slice;
            if (VKTextWidth.width(slice) > maxWidth) {
                slice = VKTextWidth.truncate(slice, maxWidth);
            }
        }
        return VKTextWidth.padRight(slice, maxWidth);
    }
}
