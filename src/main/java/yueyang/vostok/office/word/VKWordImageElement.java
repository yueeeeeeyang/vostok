package yueyang.vostok.office.word;

/** Word 图片元素。 */
public final class VKWordImageElement implements VKWordWriteElement {
    private final VKWordImageSourceType sourceType;
    private final String fileName;
    private final byte[] bytes;
    private final String filePath;

    private VKWordImageElement(VKWordImageSourceType sourceType, String fileName, byte[] bytes, String filePath) {
        this.sourceType = sourceType;
        this.fileName = fileName;
        this.bytes = bytes;
        this.filePath = filePath;
    }

    /** 基于 bytes 构建图片元素。 */
    public static VKWordImageElement fromBytes(String fileName, byte[] bytes) {
        return new VKWordImageElement(VKWordImageSourceType.BYTES, fileName, bytes, null);
    }

    /** 基于本地文件路径构建图片元素。 */
    public static VKWordImageElement fromFile(String filePath) {
        return new VKWordImageElement(VKWordImageSourceType.FILE_PATH, null, null, filePath);
    }

    public VKWordImageSourceType sourceType() {
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
