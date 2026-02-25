package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.tool.VKAnsi;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.util.List;

public final class VKStatusBar extends VKView {
    private String text;

    public VKStatusBar(String text) {
        this.text = text == null ? "" : text;
    }

    public VKStatusBar text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        String line = VKTextWidth.padRight(VKTextWidth.truncate(text, ctx.width()), ctx.width());
        if (ctx.ansiEnabled()) {
            line = VKAnsi.apply(line, true, ctx.theme().getStatusBg(), ctx.theme().getStatusText());
        }
        return List.of(line);
    }
}
