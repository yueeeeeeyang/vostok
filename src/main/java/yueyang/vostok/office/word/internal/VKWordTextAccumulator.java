package yueyang.vostok.office.word.internal;

/** Word 文本累加器，同时维护字数统计。 */
public final class VKWordTextAccumulator extends VKWordXmlScanner.NoopSink {
    private final StringBuilder text = new StringBuilder();
    private final VKWordCharCounter counter;

    public VKWordTextAccumulator(int maxChars) {
        this.counter = new VKWordCharCounter(maxChars);
    }

    @Override
    public void onText(String value) {
        String v = value == null ? "" : value;
        text.append(v);
        counter.onText(v);
    }

    @Override
    public void onTab() {
        text.append('\t');
    }

    @Override
    public void onBreak() {
        text.append('\n');
    }

    @Override
    public void onParagraphEnd() {
        text.append('\n');
    }

    public int charCount() {
        return counter.charCount();
    }

    public String text() {
        return text.toString();
    }
}
