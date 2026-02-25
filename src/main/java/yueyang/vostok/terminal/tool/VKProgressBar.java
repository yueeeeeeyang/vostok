package yueyang.vostok.terminal.tool;

/**
 * Progress bar renderer.
 */
public final class VKProgressBar {
    private int width = 24;
    private char filled = '#';
    private char empty = '-';

    public VKProgressBar width(int width) {
        this.width = Math.max(5, width);
        return this;
    }

    public VKProgressBar filled(char filled) {
        this.filled = filled;
        return this;
    }

    public VKProgressBar empty(char empty) {
        this.empty = empty;
        return this;
    }

    public String render(double progress, String label) {
        double normalized = Math.max(0d, Math.min(1d, progress));
        int done = (int) Math.round(normalized * width);
        int left = Math.max(0, width - done);
        int percent = (int) Math.round(normalized * 100d);

        String suffix = (label == null || label.isBlank()) ? "" : (" " + label);
        return "[" + String.valueOf(filled).repeat(done) + String.valueOf(empty).repeat(left) + "] " + percent + "%" + suffix;
    }

    public String renderIndeterminate(long tick, String label) {
        int pos = (int) (Math.abs(tick) % width);
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            sb.append(i == pos ? filled : empty);
        }
        String suffix = (label == null || label.isBlank()) ? "" : (" " + label);
        return "[" + sb + "]" + suffix;
    }
}
