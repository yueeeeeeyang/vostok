package yueyang.vostok.office.pdf;

import yueyang.vostok.office.style.VKOfficeImageStyle;

/** PDF 图片元素。 */
public final class VKPdfImageElement implements VKPdfWriteElement {
    private final VKPdfImageSourceType sourceType;
    private final String fileName;
    private final byte[] bytes;
    private final String filePath;
    private final VKOfficeImageStyle style;

    private VKPdfImageElement(VKPdfImageSourceType sourceType,
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
    public static VKPdfImageElement fromBytes(String fileName, byte[] bytes) {
        return new VKPdfImageElement(VKPdfImageSourceType.BYTES, fileName, bytes, null, null);
    }

    /** 基于 bytes 构建图片元素（带样式）。 */
    public static VKPdfImageElement fromBytes(String fileName, byte[] bytes, VKOfficeImageStyle style) {
        return new VKPdfImageElement(VKPdfImageSourceType.BYTES, fileName, bytes, null, style);
    }

    /** 基于本地文件路径构建图片元素。 */
    public static VKPdfImageElement fromFile(String filePath) {
        return new VKPdfImageElement(VKPdfImageSourceType.FILE_PATH, null, null, filePath, null);
    }

    /** 基于本地文件路径构建图片元素（带样式）。 */
    public static VKPdfImageElement fromFile(String filePath, VKOfficeImageStyle style) {
        return new VKPdfImageElement(VKPdfImageSourceType.FILE_PATH, null, null, filePath, style);
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

    /** 图片样式（可空）。 */
    public VKOfficeImageStyle style() {
        return style;
    }
}
