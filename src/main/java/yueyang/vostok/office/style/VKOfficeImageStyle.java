package yueyang.vostok.office.style;

/** 图片样式配置。 */
public final class VKOfficeImageStyle {
    private Integer width;
    private Integer height;
    private Boolean keepAspectRatio = Boolean.TRUE;
    private VKOfficeImageFit fit = VKOfficeImageFit.CONTAIN;

    public static VKOfficeImageStyle create() {
        return new VKOfficeImageStyle();
    }

    public Integer width() {
        return width;
    }

    public VKOfficeImageStyle width(Integer width) {
        this.width = width;
        return this;
    }

    public Integer height() {
        return height;
    }

    public VKOfficeImageStyle height(Integer height) {
        this.height = height;
        return this;
    }

    public Boolean keepAspectRatio() {
        return keepAspectRatio;
    }

    public VKOfficeImageStyle keepAspectRatio(Boolean keepAspectRatio) {
        this.keepAspectRatio = keepAspectRatio;
        return this;
    }

    public VKOfficeImageFit fit() {
        return fit;
    }

    public VKOfficeImageStyle fit(VKOfficeImageFit fit) {
        if (fit != null) {
            this.fit = fit;
        }
        return this;
    }
}
