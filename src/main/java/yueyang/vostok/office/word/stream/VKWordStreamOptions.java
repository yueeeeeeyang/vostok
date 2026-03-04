package yueyang.vostok.office.word.stream;

/** Word 流式读取选项。 */
public final class VKWordStreamOptions {
    private boolean includeText = true;
    private boolean includeImages = true;
    private boolean includeMeta = true;

    public static VKWordStreamOptions defaults() {
        return new VKWordStreamOptions();
    }

    public boolean includeText() {
        return includeText;
    }

    public VKWordStreamOptions includeText(boolean includeText) {
        this.includeText = includeText;
        return this;
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKWordStreamOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }

    public boolean includeMeta() {
        return includeMeta;
    }

    public VKWordStreamOptions includeMeta(boolean includeMeta) {
        this.includeMeta = includeMeta;
        return this;
    }
}
