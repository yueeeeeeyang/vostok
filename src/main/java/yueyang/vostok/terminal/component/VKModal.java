package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.tool.VKTextWidth;

import java.util.ArrayList;
import java.util.List;

public final class VKModal extends VKView {
    private String title = "Modal";
    private String message = "";
    private boolean visible = false;

    public VKModal title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    public VKModal message(String message) {
        this.message = message == null ? "" : message;
        return this;
    }

    public VKModal visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        if (!visible) {
            return List.of();
        }
        int width = Math.min(ctx.width(), Math.max(20, Math.max(VKTextWidth.width(title), VKTextWidth.width(message)) + 6));
        VKPanel panel = new VKPanel(title).padding(1).child(new VKTextView(message));

        List<String> panelLines = panel.render(ctx.child(width));
        int leftPad = Math.max(0, (ctx.width() - width) / 2);

        List<String> out = new ArrayList<>();
        for (String panelLine : panelLines) {
            out.add(" ".repeat(leftPad) + panelLine);
        }
        return out;
    }
}
