package yueyang.vostok.office.ppt;

/** PPT 读取选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKPptReadOptions {
    private VKPptImageLoadMode imageLoadMode = VKPptImageLoadMode.BYTES;
    private long maxDocumentBytes = -1;
    private int maxSlides = -1;
    private int maxTextChars = -1;
    private int maxImages = -1;
    private long maxSingleImageBytes = -1;
    private long maxTotalImageBytes = -1;
    private String tempSubDir;
    private int xxeSampleBytes = 8192;

    public static VKPptReadOptions defaults() {
        return new VKPptReadOptions();
    }

    public VKPptImageLoadMode imageLoadMode() {
        return imageLoadMode;
    }

    public VKPptReadOptions imageLoadMode(VKPptImageLoadMode imageLoadMode) {
        this.imageLoadMode = imageLoadMode == null ? VKPptImageLoadMode.BYTES : imageLoadMode;
        return this;
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public VKPptReadOptions maxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
        return this;
    }

    public int maxSlides() {
        return maxSlides;
    }

    public VKPptReadOptions maxSlides(int maxSlides) {
        this.maxSlides = maxSlides;
        return this;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public VKPptReadOptions maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    public int maxImages() {
        return maxImages;
    }

    public VKPptReadOptions maxImages(int maxImages) {
        this.maxImages = maxImages;
        return this;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public VKPptReadOptions maxSingleImageBytes(long maxSingleImageBytes) {
        this.maxSingleImageBytes = maxSingleImageBytes;
        return this;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public VKPptReadOptions maxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
        return this;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    public VKPptReadOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }

    public int xxeSampleBytes() {
        return xxeSampleBytes;
    }

    public VKPptReadOptions xxeSampleBytes(int xxeSampleBytes) {
        this.xxeSampleBytes = xxeSampleBytes;
        return this;
    }
}
