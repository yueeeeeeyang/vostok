package yueyang.vostok.office.pdf;

/** PDF 读取选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKPdfReadOptions {
    private VKPdfImageLoadMode imageLoadMode = VKPdfImageLoadMode.BYTES;
    private long maxDocumentBytes = -1;
    private int maxPages = -1;
    private int maxTextChars = -1;
    private int maxImages = -1;
    private long maxSingleImageBytes = -1;
    private long maxTotalImageBytes = -1;
    private int maxObjects = -1;
    private long maxStreamBytes = -1;

    public static VKPdfReadOptions defaults() {
        return new VKPdfReadOptions();
    }

    public VKPdfImageLoadMode imageLoadMode() {
        return imageLoadMode;
    }

    public VKPdfReadOptions imageLoadMode(VKPdfImageLoadMode imageLoadMode) {
        this.imageLoadMode = imageLoadMode == null ? VKPdfImageLoadMode.BYTES : imageLoadMode;
        return this;
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public VKPdfReadOptions maxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
        return this;
    }

    public int maxPages() {
        return maxPages;
    }

    public VKPdfReadOptions maxPages(int maxPages) {
        this.maxPages = maxPages;
        return this;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public VKPdfReadOptions maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    public int maxImages() {
        return maxImages;
    }

    public VKPdfReadOptions maxImages(int maxImages) {
        this.maxImages = maxImages;
        return this;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public VKPdfReadOptions maxSingleImageBytes(long maxSingleImageBytes) {
        this.maxSingleImageBytes = maxSingleImageBytes;
        return this;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public VKPdfReadOptions maxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
        return this;
    }

    public int maxObjects() {
        return maxObjects;
    }

    public VKPdfReadOptions maxObjects(int maxObjects) {
        this.maxObjects = maxObjects;
        return this;
    }

    public long maxStreamBytes() {
        return maxStreamBytes;
    }

    public VKPdfReadOptions maxStreamBytes(long maxStreamBytes) {
        this.maxStreamBytes = maxStreamBytes;
        return this;
    }
}
