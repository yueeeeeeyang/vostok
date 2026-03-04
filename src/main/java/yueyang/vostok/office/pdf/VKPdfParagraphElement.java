package yueyang.vostok.office.pdf;

/** PDF 段落元素。 */
public final class VKPdfParagraphElement implements VKPdfWriteElement {
    private final String text;

    public VKPdfParagraphElement(String text) {
        this.text = text == null ? "" : text;
    }

    /** 段落文本。 */
    public String text() {
        return text;
    }
}
