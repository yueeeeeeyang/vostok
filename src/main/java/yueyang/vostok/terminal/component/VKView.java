package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;
import yueyang.vostok.terminal.event.VKKeyEvent;

import java.util.List;

/**
 * Base terminal view.
 */
public abstract class VKView {
    public abstract List<String> render(VKRenderContext ctx);

    public boolean focusable() {
        return false;
    }

    public void focused(boolean focused) {
        // default no-op
    }

    public boolean onKey(VKKeyEvent event) {
        return false;
    }

    public List<VKView> children() {
        return List.of();
    }
}
