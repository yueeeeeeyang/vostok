package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.ArrayList;
import java.util.List;

public final class VKForm extends VKView {
    private final List<VKInput> inputs = new ArrayList<>();

    public VKForm add(VKInput input) {
        if (input != null) {
            inputs.add(input);
        }
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        if (inputs.isEmpty()) {
            return List.of(ctx.styleMuted("(form empty)"));
        }
        List<String> out = new ArrayList<>();
        for (VKInput input : inputs) {
            out.addAll(input.render(ctx));
        }
        return out;
    }

    @Override
    public List<VKView> children() {
        return new ArrayList<>(inputs);
    }
}
