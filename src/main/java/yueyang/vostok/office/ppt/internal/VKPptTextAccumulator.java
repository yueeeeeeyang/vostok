package yueyang.vostok.office.ppt.internal;

/** PPT 文本累加器，同时维护字数统计。 */
public final class VKPptTextAccumulator {
    private final StringBuilder sb = new StringBuilder();
    private final VKPptCharCounter counter;

    public VKPptTextAccumulator(int maxChars) {
        this.counter = new VKPptCharCounter(maxChars);
    }

    public void onText(String text) {
        String v = text == null ? "" : text;
        sb.append(v);
        counter.onText(v);
    }

    public void onParagraphEnd() {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    public void onSlideEnd() {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    public String text() {
        return sb.toString();
    }

    public int charCount() {
        return counter.charCount();
    }
}
