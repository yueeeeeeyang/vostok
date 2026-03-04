package yueyang.vostok.office.word;

/** Word 读取选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKWordReadOptions {
    private boolean includeHeaderFooter = true;
    private boolean includeFootnotes;
    private boolean includeEndnotes;
    private boolean includeComments;
    private VKWordImageLoadMode imageLoadMode = VKWordImageLoadMode.BYTES;
    private long maxDocumentBytes = -1;
    private int maxTextChars = -1;
    private int maxImages = -1;
    private long maxSingleImageBytes = -1;
    private long maxTotalImageBytes = -1;
    private String tempSubDir;
    private int xxeSampleBytes = 8192;

    public static VKWordReadOptions defaults() {
        return new VKWordReadOptions();
    }

    public boolean includeHeaderFooter() {
        return includeHeaderFooter;
    }

    public VKWordReadOptions includeHeaderFooter(boolean includeHeaderFooter) {
        this.includeHeaderFooter = includeHeaderFooter;
        return this;
    }

    public boolean includeFootnotes() {
        return includeFootnotes;
    }

    public VKWordReadOptions includeFootnotes(boolean includeFootnotes) {
        this.includeFootnotes = includeFootnotes;
        return this;
    }

    public boolean includeEndnotes() {
        return includeEndnotes;
    }

    public VKWordReadOptions includeEndnotes(boolean includeEndnotes) {
        this.includeEndnotes = includeEndnotes;
        return this;
    }

    public boolean includeComments() {
        return includeComments;
    }

    public VKWordReadOptions includeComments(boolean includeComments) {
        this.includeComments = includeComments;
        return this;
    }

    public VKWordImageLoadMode imageLoadMode() {
        return imageLoadMode;
    }

    public VKWordReadOptions imageLoadMode(VKWordImageLoadMode imageLoadMode) {
        this.imageLoadMode = imageLoadMode == null ? VKWordImageLoadMode.BYTES : imageLoadMode;
        return this;
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public VKWordReadOptions maxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
        return this;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public VKWordReadOptions maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    public int maxImages() {
        return maxImages;
    }

    public VKWordReadOptions maxImages(int maxImages) {
        this.maxImages = maxImages;
        return this;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public VKWordReadOptions maxSingleImageBytes(long maxSingleImageBytes) {
        this.maxSingleImageBytes = maxSingleImageBytes;
        return this;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public VKWordReadOptions maxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
        return this;
    }

    public String tempSubDir() {
        return tempSubDir;
    }

    public VKWordReadOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }

    public int xxeSampleBytes() {
        return xxeSampleBytes;
    }

    public VKWordReadOptions xxeSampleBytes(int xxeSampleBytes) {
        this.xxeSampleBytes = xxeSampleBytes;
        return this;
    }
}
