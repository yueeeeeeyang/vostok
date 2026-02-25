package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.List;

public final class VKToast extends VKView {
    public enum Level {
        INFO,
        SUCCESS,
        WARN,
        ERROR
    }

    private String text;
    private Level level = Level.INFO;

    public VKToast(String text) {
        this.text = text == null ? "" : text;
    }

    public VKToast level(Level level) {
        if (level != null) {
            this.level = level;
        }
        return this;
    }

    public VKToast text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        String raw = "[" + level.name() + "] " + text;
        String line = ctx.fit(raw);
        line = switch (level) {
            case SUCCESS -> ctx.styleSuccess(line);
            case WARN -> ctx.styleWarn(line);
            case ERROR -> ctx.styleError(line);
            default -> ctx.styleText(line);
        };
        return List.of(line);
    }
}
