package yueyang.vostok.office.word;

import yueyang.vostok.office.style.VKOfficeTextStyle;

/** Word 段落元素。 */
public final class VKWordParagraphElement implements VKWordWriteElement {
    private final String text;
    private final VKOfficeTextStyle style;

    public VKWordParagraphElement(String text) {
        this(text, null);
    }

    public VKWordParagraphElement(String text, VKOfficeTextStyle style) {
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
