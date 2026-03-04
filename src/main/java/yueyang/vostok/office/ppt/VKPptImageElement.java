package yueyang.vostok.office.ppt;

/** PPT 图片元素。 */
public final class VKPptImageElement implements VKPptWriteElement {
    private final VKPptImageSourceType sourceType;
    private final String fileName;
    private final byte[] bytes;
    private final String filePath;

    private VKPptImageElement(VKPptImageSourceType sourceType, String fileName, byte[] bytes, String filePath) {
        this.sourceType = sourceType;
        this.fileName = fileName;
        this.bytes = bytes;
        this.filePath = filePath;
    }

    /** 基于 bytes 构建图片元素。 */
    public static VKPptImageElement fromBytes(String fileName, byte[] bytes) {
        return new VKPptImageElement(VKPptImageSourceType.BYTES, fileName, bytes, null);
    }

    /** 基于本地文件路径构建图片元素。 */
    public static VKPptImageElement fromFile(String filePath) {
        return new VKPptImageElement(VKPptImageSourceType.FILE_PATH, null, null, filePath);
    }

    public VKPptImageSourceType sourceType() {
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
