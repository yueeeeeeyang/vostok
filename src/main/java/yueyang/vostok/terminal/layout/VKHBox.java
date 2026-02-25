package yueyang.vostok.terminal.layout;

import yueyang.vostok.terminal.component.VKView;
import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.util.ArrayList;
import java.util.List;

public final class VKHBox extends VKView {
    private final List<VKView> children = new ArrayList<>();
    private int spacing = 1;

    public VKHBox spacing(int spacing) {
        this.spacing = Math.max(0, spacing);
        return this;
    }

    public VKHBox child(VKView child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        if (children.isEmpty()) {
            return List.of();
        }
        int childWidth = Math.max(8, (ctx.width() - spacing * (children.size() - 1)) / children.size());

        List<List<String>> childLines = new ArrayList<>();
        int maxHeight = 0;
        for (VKView child : children) {
            List<String> rendered = child.render(ctx.child(childWidth));
            childLines.add(rendered);
            maxHeight = Math.max(maxHeight, rendered.size());
        }

        List<String> out = new ArrayList<>();
        String gap = " ".repeat(spacing);
        for (int row = 0; row < maxHeight; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < childLines.size(); col++) {
                List<String> rendered = childLines.get(col);
                String cell = row < rendered.size() ? rendered.get(row) : "";
                line.append(VKTextWidth.padRight(cell, childWidth));
                if (col < childLines.size() - 1) {
                    line.append(gap);
                }
            }
            out.add(ctx.fit(line.toString()));
        }
        return out;
    }

    @Override
    public List<VKView> children() {
        return List.copyOf(children);
    }
}
