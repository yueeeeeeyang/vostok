package yueyang.vostok.terminal.layout;

import yueyang.vostok.terminal.component.VKView;
import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.ArrayList;
import java.util.List;

public final class VKGrid extends VKView {
    private final List<VKView> children = new ArrayList<>();
    private int columns = 2;
    private int hSpacing = 1;
    private int vSpacing = 0;

    public VKGrid columns(int columns) {
        this.columns = Math.max(1, columns);
        return this;
    }

    public VKGrid hSpacing(int hSpacing) {
        this.hSpacing = Math.max(0, hSpacing);
        return this;
    }

    public VKGrid vSpacing(int vSpacing) {
        this.vSpacing = Math.max(0, vSpacing);
        return this;
    }

    public VKGrid child(VKView child) {
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
        List<String> out = new ArrayList<>();
        for (int i = 0; i < children.size(); i += columns) {
            VKHBox row = new VKHBox().spacing(hSpacing);
            for (int c = 0; c < columns && i + c < children.size(); c++) {
                row.child(children.get(i + c));
            }
            out.addAll(row.render(ctx));
            if (i + columns < children.size()) {
                for (int j = 0; j < vSpacing; j++) {
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
