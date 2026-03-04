package yueyang.vostok.office.ppt;

import yueyang.vostok.office.style.VKOfficeTextStyle;

/** PPT 段落元素。 */
public final class VKPptParagraphElement implements VKPptWriteElement {
    private final String text;
    private final VKOfficeTextStyle style;

    public VKPptParagraphElement(String text) {
        this(text, null);
    }

    public VKPptParagraphElement(String text, VKOfficeTextStyle style) {
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
