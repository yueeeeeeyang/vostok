package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.util.ArrayList;
import java.util.List;

public final class VKPanel extends VKView {
    private final List<VKView> children = new ArrayList<>();
    private String title;
    private int padding = 1;

    public VKPanel(String title) {
        this.title = title == null ? "" : title;
    }

    public VKPanel title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    public VKPanel padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    public VKPanel child(VKView child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        int width = Math.max(10, ctx.width());
        boolean unicode = ctx.unicodeEnabled();
        char h = unicode ? '─' : '-';
        char v = unicode ? '│' : '|';
        char tl = unicode ? '┌' : '+';
        char tr = unicode ? '┐' : '+';
        char bl = unicode ? '└' : '+';
        char br = unicode ? '┘' : '+';

        int innerWidth = Math.max(1, width - 2);
        List<String> childLines = new ArrayList<>();
        VKRenderContext childCtx = ctx.child(Math.max(5, innerWidth - padding * 2));
        for (VKView child : children) {
            childLines.addAll(child.render(childCtx));
        }
        if (childLines.isEmpty()) {
            childLines.add("");
        }

        List<String> out = new ArrayList<>();
        String top = String.valueOf(tl) + String.valueOf(h).repeat(innerWidth) + tr;
        if (!title.isBlank()) {
            String t = " " + title + " ";
            int tWidth = Math.min(innerWidth, VKTextWidth.width(t));
            String left = String.valueOf(h).repeat(Math.max(0, (innerWidth - tWidth) / 2));
            String right = String.valueOf(h).repeat(Math.max(0, innerWidth - VKTextWidth.width(left) - tWidth));
            top = String.valueOf(tl) + left + VKTextWidth.truncate(t, innerWidth) + right + tr;
        }
        out.add(ctx.styleBorder(top));

        for (String line : childLines) {
            String padded = " ".repeat(padding)
                    + VKTextWidth.padRight(line, Math.max(0, innerWidth - padding * 2))
                    + " ".repeat(padding);
            padded = VKTextWidth.truncate(padded, innerWidth);
            padded = VKTextWidth.padRight(padded, innerWidth);
            out.add(ctx.styleBorder(String.valueOf(v)) + padded + ctx.styleBorder(String.valueOf(v)));
        }

        String bottom = String.valueOf(bl) + String.valueOf(h).repeat(innerWidth) + br;
        out.add(ctx.styleBorder(bottom));
        return out;
    }
}
