package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.List;

/**
 * Base terminal view.
 */
public abstract class VKView {
    public abstract List<String> render(VKRenderContext ctx);
}
