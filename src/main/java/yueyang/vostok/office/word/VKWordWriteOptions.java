package yueyang.vostok.office.word;

/** Word 写入选项。值为 -1 表示沿用 Office 全局配置。 */
public final class VKWordWriteOptions {
    private int maxTextChars = -1;
    private int maxImages = -1;
    private long maxSingleImageBytes = -1;
    private long maxTotalImageBytes = -1;
    private long maxDocumentBytes = -1;
    private String tempSubDir;

    public static VKWordWriteOptions defaults() {
        return new VKWordWriteOptions();
    }

    public int maxTextChars() {
        return maxTextChars;
    }

    public VKWordWriteOptions maxTextChars(int maxTextChars) {
        this.maxTextChars = maxTextChars;
        return this;
    }

    public int maxImages() {
        return maxImages;
    }

    public VKWordWriteOptions maxImages(int maxImages) {
        this.maxImages = maxImages;
        return this;
    }

    public long maxSingleImageBytes() {
        return maxSingleImageBytes;
    }

    public VKWordWriteOptions maxSingleImageBytes(long maxSingleImageBytes) {
        this.maxSingleImageBytes = maxSingleImageBytes;
        return this;
    }

    public long maxTotalImageBytes() {
        return maxTotalImageBytes;
    }

    public VKWordWriteOptions maxTotalImageBytes(long maxTotalImageBytes) {
        this.maxTotalImageBytes = maxTotalImageBytes;
        return this;
    }

    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    public VKWordWriteOptions maxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
        return this;
    }

    /** 临时解包子目录（相对 File.baseDir）。 */
    public String tempSubDir() {
        return tempSubDir;
    }

    public VKWordWriteOptions tempSubDir(String tempSubDir) {
        this.tempSubDir = tempSubDir;
        return this;
    }
}
