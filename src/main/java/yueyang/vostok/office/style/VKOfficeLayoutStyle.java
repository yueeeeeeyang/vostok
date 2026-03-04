package yueyang.vostok.office.style;

/** 版式配置（页边距、块间距、默认行高）。 */
public final class VKOfficeLayoutStyle {
    private Double marginTop;
    private Double marginRight;
    private Double marginBottom;
    private Double marginLeft;
    private Double blockSpacing;
    private Double defaultLineHeight;

    public static VKOfficeLayoutStyle create() {
        return new VKOfficeLayoutStyle();
    }

    public Double marginTop() {
        return marginTop;
    }

    public VKOfficeLayoutStyle marginTop(Double marginTop) {
        this.marginTop = marginTop;
        return this;
    }

    public Double marginRight() {
        return marginRight;
    }

    public VKOfficeLayoutStyle marginRight(Double marginRight) {
        this.marginRight = marginRight;
        return this;
    }

    public Double marginBottom() {
        return marginBottom;
    }

    public VKOfficeLayoutStyle marginBottom(Double marginBottom) {
        this.marginBottom = marginBottom;
        return this;
    }

    public Double marginLeft() {
        return marginLeft;
    }

    public VKOfficeLayoutStyle marginLeft(Double marginLeft) {
        this.marginLeft = marginLeft;
        return this;
    }

    public Double blockSpacing() {
        return blockSpacing;
    }

    public VKOfficeLayoutStyle blockSpacing(Double blockSpacing) {
        this.blockSpacing = blockSpacing;
        return this;
    }

    public Double defaultLineHeight() {
        return defaultLineHeight;
    }

    public VKOfficeLayoutStyle defaultLineHeight(Double defaultLineHeight) {
        this.defaultLineHeight = defaultLineHeight;
        return this;
    }
}
