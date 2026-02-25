package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.List;

public final class VKDivider extends VKView {
    private char ch = '-';

    public VKDivider ch(char ch) {
        this.ch = ch;
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        String line = String.valueOf(ch).repeat(Math.max(1, ctx.width()));
        return List.of(ctx.styleBorder(line));
    }
}
