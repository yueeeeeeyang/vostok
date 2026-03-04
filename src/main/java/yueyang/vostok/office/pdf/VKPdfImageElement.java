package yueyang.vostok.office.pdf;

/** PDF 图片元素。 */
public final class VKPdfImageElement implements VKPdfWriteElement {
    private final VKPdfImageSourceType sourceType;
    private final String fileName;
    private final byte[] bytes;
    private final String filePath;

    private VKPdfImageElement(VKPdfImageSourceType sourceType, String fileName, byte[] bytes, String filePath) {
        this.sourceType = sourceType;
        this.fileName = fileName;
        this.bytes = bytes;
        this.filePath = filePath;
    }

    /** 基于 bytes 构建图片元素。 */
    public static VKPdfImageElement fromBytes(String fileName, byte[] bytes) {
        return new VKPdfImageElement(VKPdfImageSourceType.BYTES, fileName, bytes, null);
    }

    /** 基于本地文件路径构建图片元素。 */
    public static VKPdfImageElement fromFile(String filePath) {
        return new VKPdfImageElement(VKPdfImageSourceType.FILE_PATH, null, null, filePath);
    }

    public VKPdfImageSourceType sourceType() {
        return sourceType;
    }

    public String fileName() {
        return fileName;
    }

    public byte[] bytes() {
        return bytes;
    }

    public String filePath() {
        return filePath;
    }
}
