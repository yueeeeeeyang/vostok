package yueyang.vostok.office.ppt.structured;

/** PPT 结构化提取选项。 */
public final class VKPptStructuredOptions {
    private boolean includeText = true;
    private boolean includeImages = true;

    public static VKPptStructuredOptions defaults() {
        return new VKPptStructuredOptions();
    }

    public boolean includeText() {
        return includeText;
    }

    public VKPptStructuredOptions includeText(boolean includeText) {
        this.includeText = includeText;
        return this;
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKPptStructuredOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }
}
