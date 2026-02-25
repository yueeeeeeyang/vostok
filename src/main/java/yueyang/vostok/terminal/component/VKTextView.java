package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.List;

public final class VKTextView extends VKView {
    public enum Style {
        TITLE,
        TEXT,
        MUTED,
        SUCCESS,
        WARN,
        ERROR
    }

    private String text;
    private Style style = Style.TEXT;

    public VKTextView(String text) {
        this.text = text == null ? "" : text;
    }

    public VKTextView text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    public VKTextView style(Style style) {
        if (style != null) {
            this.style = style;
        }
        return this;
    }

    public VKTextView title() {
        return style(Style.TITLE);
    }

    public VKTextView muted() {
        return style(Style.MUTED);
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        String line = ctx.fit(text);
        line = switch (style) {
            case TITLE -> ctx.styleTitle(line);
            case MUTED -> ctx.styleMuted(line);
            case SUCCESS -> ctx.styleSuccess(line);
            case WARN -> ctx.styleWarn(line);
            case ERROR -> ctx.styleError(line);
            default -> ctx.styleText(line);
        };
        return List.of(line);
    }
}
