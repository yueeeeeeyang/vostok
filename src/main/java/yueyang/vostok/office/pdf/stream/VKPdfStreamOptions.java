package yueyang.vostok.office.pdf.stream;

/** PDF 流式读取选项。 */
public final class VKPdfStreamOptions {
    private boolean includeText = true;
    private boolean includeImages = true;
    private boolean includeMeta = true;

    public static VKPdfStreamOptions defaults() {
        return new VKPdfStreamOptions();
    }

    public boolean includeText() {
        return includeText;
    }

    public VKPdfStreamOptions includeText(boolean includeText) {
        this.includeText = includeText;
        return this;
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKPdfStreamOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }

    public boolean includeMeta() {
        return includeMeta;
    }

    public VKPdfStreamOptions includeMeta(boolean includeMeta) {
        this.includeMeta = includeMeta;
        return this;
    }
}
