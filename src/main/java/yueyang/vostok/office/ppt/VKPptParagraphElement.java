package yueyang.vostok.office.ppt;

/** PPT 段落元素。 */
public final class VKPptParagraphElement implements VKPptWriteElement {
    private final String text;

    public VKPptParagraphElement(String text) {
        this.text = text == null ? "" : text;
    }

    /** 段落文本。 */
    public String text() {
        return text;
    }
}
