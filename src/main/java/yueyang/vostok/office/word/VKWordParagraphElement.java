package yueyang.vostok.office.word;

/** Word 段落元素。 */
public final class VKWordParagraphElement implements VKWordWriteElement {
    private final String text;

    public VKWordParagraphElement(String text) {
        this.text = text == null ? "" : text;
    }

    /** 段落文本。 */
    public String text() {
        return text;
    }
}
