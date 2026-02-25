package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.ArrayList;
import java.util.List;

public final class VKListView extends VKView {
    private final List<String> items = new ArrayList<>();
    private int selectedIndex = -1;

    public VKListView items(List<String> items) {
        this.items.clear();
        if (items != null) {
            for (String item : items) {
                this.items.add(item == null ? "" : item);
            }
        }
        return this;
    }

    public VKListView selectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        if (items.isEmpty()) {
            return List.of(ctx.styleMuted("(empty)"));
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String prefix = i == selectedIndex ? "> " : "  ";
            String line = ctx.fit(prefix + items.get(i));
            out.add(i == selectedIndex ? ctx.styleSuccess(line) : ctx.styleText(line));
        }
        return out;
    }
}
