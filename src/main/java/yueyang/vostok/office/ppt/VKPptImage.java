package yueyang.vostok.office.ppt;

import java.util.Arrays;

/** PPT 图片视图。 */
public final class VKPptImage {
    private final int index;
    private final int slideIndex;
    private final String partName;
    private final String mediaPath;
    private final String contentType;
    private final long size;
    private final byte[] bytes;

    public VKPptImage(int index,
                      int slideIndex,
                      String partName,
                      String mediaPath,
                      String contentType,
                      long size,
                      byte[] bytes) {
        this.index = index;
        this.slideIndex = slideIndex;
        this.partName = partName;
        this.mediaPath = mediaPath;
        this.contentType = contentType;
        this.size = size;
        this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    /** 文档中的出现顺序（从 1 开始）。 */
    public int index() {
        return index;
    }

    /** 所属幻灯片序号（从 1 开始）。 */
    public int slideIndex() {
        return slideIndex;
    }

    /** 来源部件，例如 ppt/slides/slide1.xml。 */
    public String partName() {
        return partName;
    }

    /** 包内媒体路径，例如 ppt/media/image1.png。 */
    public String mediaPath() {
        return mediaPath;
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
