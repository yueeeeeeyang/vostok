package yueyang.vostok.office.pdf.structured;

/** PDF 结构化提取选项。 */
public final class VKPdfStructuredOptions {
    private boolean includeText = true;
    private boolean includeImages = true;

    public static VKPdfStructuredOptions defaults() {
        return new VKPdfStructuredOptions();
    }

    public boolean includeText() {
        return includeText;
    }

    public VKPdfStructuredOptions includeText(boolean includeText) {
        this.includeText = includeText;
        return this;
    }

    public boolean includeImages() {
        return includeImages;
    }

    public VKPdfStructuredOptions includeImages(boolean includeImages) {
        this.includeImages = includeImages;
        return this;
    }
}
