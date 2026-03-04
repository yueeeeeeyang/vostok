package yueyang.vostok.office.pdf;

import java.util.Arrays;

/** PDF 图片视图。 */
public final class VKPdfImage {
    private final int index;
    private final int pageIndex;
    private final String objectRef;
    private final String contentType;
    private final long size;
    private final byte[] bytes;

    public VKPdfImage(int index,
                      int pageIndex,
                      String objectRef,
                      String contentType,
                      long size,
                      byte[] bytes) {
        this.index = index;
        this.pageIndex = pageIndex;
        this.objectRef = objectRef;
        this.contentType = contentType;
        this.size = size;
        this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    /** 文档中的出现顺序（从 1 开始）。 */
    public int index() {
        return index;
    }

    /** 所属页序号（从 1 开始）。 */
    public int pageIndex() {
        return pageIndex;
    }

    /** 来源对象引用，例如 12 0 R。 */
    public String objectRef() {
        return objectRef;
    }

    /** MIME 类型。 */
    public String contentType() {
        return contentType;
    }

    /** 图片字节数。 */
    public long size() {
        return size;
    }

    /** 图片字节，METADATA_ONLY 模式下为 null。 */
    public byte[] bytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
