package yueyang.vostok.terminal.component;

import yueyang.vostok.terminal.core.VKRenderContext;

import java.util.List;

public final class VKInput extends VKView {
    private String label;
    private String value = "";
    private String placeholder = "";
    private boolean secret;

    public VKInput(String label) {
        this.label = label == null ? "" : label;
    }

    public VKInput label(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    public VKInput value(String value) {
        this.value = value == null ? "" : value;
        return this;
    }

    public VKInput placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    public VKInput secret(boolean secret) {
        this.secret = secret;
        return this;
    }

    @Override
    public List<String> render(VKRenderContext ctx) {
        String shown = value.isBlank() ? placeholder : value;
        if (secret && !shown.isBlank() && !shown.equals(placeholder)) {
            shown = "*".repeat(shown.length());
        }
        return List.of(ctx.styleText(ctx.fit(label + ": " + shown)));
    }
}
