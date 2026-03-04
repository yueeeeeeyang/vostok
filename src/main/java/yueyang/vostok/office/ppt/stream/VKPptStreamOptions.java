package yueyang.vostok.office.ppt.stream;

/** PPT 流式读取选项。 */
public final class VKPptStreamOptions {
    private boolean includeText = true;
    private boolean includeImages = true;
    private boolean includeMeta = true;

    public static VKPptStreamOptions defaults() {
        return new VKPptStreamOptions();
    }

    public boolean includeText() {
        return includeText;
    }

    public VKPptStreamOptions includeText(boolean includeText) {
        this.includeText = includeText;
        return this;
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKPptStreamOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }

    public boolean includeMeta() {
        return includeMeta;
    }

    public VKPptStreamOptions includeMeta(boolean includeMeta) {
        this.includeMeta = includeMeta;
        return this;
    }
}
