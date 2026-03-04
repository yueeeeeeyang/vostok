package yueyang.vostok.office.pdf;

/** PDF 写入选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKPdfWriteOptions {
    private long maxDocumentBytes = -1;
    private int maxPages = -1;
    private int maxTextChars = -1;
    private int maxImages = -1;
    private long maxSingleImageBytes = -1;
    private long maxTotalImageBytes = -1;
    private int maxObjects = -1;
    private long maxStreamBytes = -1;
    private String tempSubDir;

    public static VKPdfWriteOptions defaults() {
        return new VKPdfWriteOptions();
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public VKPdfWriteOptions maxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
        return this;
    }

    public int maxPages() {
        return maxPages;
    }

    public VKPdfWriteOptions maxPages(int maxPages) {
        this.maxPages = maxPages;
        return this;
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public VKPdfWriteOptions maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    public int maxImages() {
        return maxImages;
    }

    public VKPdfWriteOptions maxImages(int maxImages) {
        this.maxImages = maxImages;
        return this;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public VKPdfWriteOptions maxSingleImageBytes(long maxSingleImageBytes) {
        this.maxSingleImageBytes = maxSingleImageBytes;
        return this;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public VKPdfWriteOptions maxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
        return this;
    }

    public int maxObjects() {
        return maxObjects;
    }

    public VKPdfWriteOptions maxObjects(int maxObjects) {
        this.maxObjects = maxObjects;
        return this;
    }

    public long maxStreamBytes() {
        return maxStreamBytes;
    }

    public VKPdfWriteOptions maxStreamBytes(long maxStreamBytes) {
        this.maxStreamBytes = maxStreamBytes;
        return this;
    }

    /** 临时子目录（相对 File.baseDir）。 */
    public String tempSubDir() {
        return tempSubDir;
    }

    public VKPdfWriteOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }
}
