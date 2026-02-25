package yueyang.vostok.terminal.tool;

import java.util.ArrayList;
import java.util.List;

/**
 * Spinner animation helper.
 */
public final class VKSpinner {
    private final List<String> frames = new ArrayList<>(List.of("|", "/", "-", "\\"));

    public static VKSpinner dots() {
        return new VKSpinner().frames("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏");
    }

    public VKSpinner frames(String... values) {
        frames.clear();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isEmpty()) {
                    frames.add(value);
                }
            }
        }
        if (frames.isEmpty()) {
            frames.add(".");
        }
        return this;
    }

    public String frame(long tick) {
        int idx = (int) (Math.abs(tick) % frames.size());
        return frames.get(idx);
    }

    public String render(long tick, String label) {
        String suffix = (label == null || label.isBlank()) ? "" : (" " + label);
        return frame(tick) + suffix;
    }
}
