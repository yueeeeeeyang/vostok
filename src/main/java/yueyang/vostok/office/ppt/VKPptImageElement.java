package yueyang.vostok.office.ppt;

import yueyang.vostok.office.style.VKOfficeImageStyle;

/** PPT 图片元素。 */
public final class VKPptImageElement implements VKPptWriteElement {
    private final VKPptImageSourceType sourceType;
    private final String fileName;
    private final byte[] bytes;
    private final String filePath;
    private final VKOfficeImageStyle style;

    private VKPptImageElement(VKPptImageSourceType sourceType,
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
    public static VKPptImageElement fromBytes(String fileName, byte[] bytes) {
        return new VKPptImageElement(VKPptImageSourceType.BYTES, fileName, bytes, null, null);
    }

    /** 基于 bytes 构建图片元素（带样式）。 */
    public static VKPptImageElement fromBytes(String fileName, byte[] bytes, VKOfficeImageStyle style) {
        return new VKPptImageElement(VKPptImageSourceType.BYTES, fileName, bytes, null, style);
    }

    /** 基于本地文件路径构建图片元素。 */
    public static VKPptImageElement fromFile(String filePath) {
        return new VKPptImageElement(VKPptImageSourceType.FILE_PATH, null, null, filePath, null);
    }

    /** 基于本地文件路径构建图片元素（带样式）。 */
    public static VKPptImageElement fromFile(String filePath, VKOfficeImageStyle style) {
        return new VKPptImageElement(VKPptImageSourceType.FILE_PATH, null, null, filePath, style);
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

    /** 图片样式（可空）。 */
    public VKOfficeImageStyle style() {
        return style;
    }
}
