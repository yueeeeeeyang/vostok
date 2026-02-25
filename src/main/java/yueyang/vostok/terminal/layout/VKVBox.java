package yueyang.vostok.terminal.layout;

import yueyang.vostok.terminal.component.VKView;
import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.ArrayList;
import java.util.List;

public final class VKVBox extends VKView {
    private final List<VKView> children = new ArrayList<>();
    private int spacing = 0;

    public VKVBox spacing(int spacing) {
        this.spacing = Math.max(0, spacing);
        return this;
    }

    public VKVBox child(VKView child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            out.addAll(children.get(i).render(ctx));
            if (i < children.size() - 1) {
                for (int j = 0; j < spacing; j++) {
                    out.add("");
                }
            }
        }
        return ctx.fitLines(out);
    }

    @Override
    public List<VKView> children() {
        return List.copyOf(children);
    }
}
