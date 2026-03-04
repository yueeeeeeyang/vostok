package yueyang.vostok.office.word;

import yueyang.vostok.office.style.VKOfficeImageStyle;

/** Word 图片元素。 */
public final class VKWordImageElement implements VKWordWriteElement {
    private final VKWordImageSourceType sourceType;
    private final String fileName;
    private final byte[] bytes;
    private final String filePath;
    private final VKOfficeImageStyle style;

    private VKWordImageElement(VKWordImageSourceType sourceType,
                               String fileName,
                               byte[] bytes,
                               String filePath,
                               VKOfficeImageStyle style) {
        this.sourceType = sourceType;
        this.fileName = fileName;
        this.bytes = bytes;
        this.filePath = filePath;
        this.style = style;
    }

    /** 基于 bytes 构建图片元素。 */
    public static VKWordImageElement fromBytes(String fileName, byte[] bytes) {
        return new VKWordImageElement(VKWordImageSourceType.BYTES, fileName, bytes, null, null);
    }

    /** 基于 bytes 构建图片元素（带样式）。 */
    public static VKWordImageElement fromBytes(String fileName, byte[] bytes, VKOfficeImageStyle style) {
        return new VKWordImageElement(VKWordImageSourceType.BYTES, fileName, bytes, null, style);
    }

    /** 基于本地文件路径构建图片元素。 */
    public static VKWordImageElement fromFile(String filePath) {
        return new VKWordImageElement(VKWordImageSourceType.FILE_PATH, null, null, filePath, null);
    }

    /** 基于本地文件路径构建图片元素（带样式）。 */
    public static VKWordImageElement fromFile(String filePath, VKOfficeImageStyle style) {
        return new VKWordImageElement(VKWordImageSourceType.FILE_PATH, null, null, filePath, style);
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

    /** 图片样式（可空）。 */
    public VKOfficeImageStyle style() {
        return style;
    }
}
