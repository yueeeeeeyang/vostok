package yueyang.vostok.office.style;

/**
 * 文本样式配置。
 *
 * <p>首版为轻量模型：用于写入请求表达样式意图，不同格式按各自能力尽力映射。</p>
 */
public final class VKOfficeTextStyle {
    private String fontFamily;
    private Integer fontSize;
    private String colorHex;
    private Boolean bold;
    private Boolean italic;
    private VKOfficeTextAlign align;
    private Double lineSpacing;

    public static VKOfficeTextStyle create() {
        return new VKOfficeTextStyle();
    }

    public String fontFamily() {
        return fontFamily;
    }

    public VKOfficeTextStyle fontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
        return this;
    }

    public Integer fontSize() {
        return fontSize;
    }

    public VKOfficeTextStyle fontSize(Integer fontSize) {
        this.fontSize = fontSize;
        return this;
    }

    public String colorHex() {
        return colorHex;
    }

    public VKOfficeTextStyle colorHex(String colorHex) {
        this.colorHex = colorHex;
        return this;
    }

    public Boolean bold() {
        return bold;
    }

    public VKOfficeTextStyle bold(Boolean bold) {
        this.bold = bold;
        return this;
    }

    public Boolean italic() {
        return italic;
    }

    public VKOfficeTextStyle italic(Boolean italic) {
        this.italic = italic;
        return this;
    }

    public VKOfficeTextAlign align() {
        return align;
    }

    public VKOfficeTextStyle align(VKOfficeTextAlign align) {
        this.align = align;
        return this;
    }

    public Double lineSpacing() {
        return lineSpacing;
    }

    public VKOfficeTextStyle lineSpacing(Double lineSpacing) {
        this.lineSpacing = lineSpacing;
        return this;
    }
}
