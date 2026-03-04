package yueyang.vostok.office.word.structured;

/** Word 结构化提取选项。 */
public final class VKWordStructuredOptions {
    private boolean includeText = true;
    private boolean includeImages = true;

    public static VKWordStructuredOptions defaults() {
        return new VKWordStructuredOptions();
    }

    public boolean includeText() {
        return includeText;
    }

    public VKWordStructuredOptions includeText(boolean includeText) {
        this.includeText = includeText;
        return this;
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKWordStructuredOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }
}
