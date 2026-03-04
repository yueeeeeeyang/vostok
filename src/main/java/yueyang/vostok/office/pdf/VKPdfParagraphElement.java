package yueyang.vostok.office.pdf;

import yueyang.vostok.office.style.VKOfficeTextStyle;

/** PDF 段落元素。 */
public final class VKPdfParagraphElement implements VKPdfWriteElement {
    private final String text;
    private final VKOfficeTextStyle style;

    public VKPdfParagraphElement(String text) {
        this(text, null);
    }

    public VKPdfParagraphElement(String text, VKOfficeTextStyle style) {
        this.text = text == null ? "" : text;
        this.style = style;
    }

    /** 段落文本。 */
    public String text() {
        return text;
    }

    /** 段落样式（可空）。 */
    public VKOfficeTextStyle style() {
        return style;
    }
}
