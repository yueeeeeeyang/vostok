package yueyang.vostok.office.ppt;

/** PPT 写入选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKPptWriteOptions {
    private long maxDocumentBytes = -1;
    private int maxSlides = -1;
    private int maxTextChars = -1;
    private int maxImages = -1;
    private long maxSingleImageBytes = -1;
    private long maxTotalImageBytes = -1;
    private String tempSubDir;

    public static VKPptWriteOptions defaults() {
        return new VKPptWriteOptions();
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public VKPptWriteOptions maxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
        return this;
    }

    public int maxSlides() {
        return maxSlides;
    }

    public VKPptWriteOptions maxSlides(int maxSlides) {
        this.maxSlides = maxSlides;
        return this;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public VKPptWriteOptions maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    public int maxImages() {
        return maxImages;
    }

    public VKPptWriteOptions maxImages(int maxImages) {
        this.maxImages = maxImages;
        return this;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public VKPptWriteOptions maxSingleImageBytes(long maxSingleImageBytes) {
        this.maxSingleImageBytes = maxSingleImageBytes;
        return this;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public VKPptWriteOptions maxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
        return this;
    }

    /** 临时解包子目录（相对 File.baseDir）。 */
    public String tempSubDir() {
        return tempSubDir;
    }

    public VKPptWriteOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }
}
