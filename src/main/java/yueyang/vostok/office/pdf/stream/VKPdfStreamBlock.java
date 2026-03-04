package yueyang.vostok.office.pdf.stream;

import yueyang.vostok.office.pdf.VKPdfImage;

/** PDF 流式读取块。 */
public final class VKPdfStreamBlock {
    private final VKPdfStreamBlockType type;
    private final int pageIndex;
    private final int blockIndex;
    private final String objectRef;
    private final String text;
    private final VKPdfImage image;

    public VKPdfStreamBlock(VKPdfStreamBlockType type,
                            int pageIndex,
                            int blockIndex,
                            String objectRef,
                            String text,
                            VKPdfImage image) {
        this.type = type;
        this.pageIndex = pageIndex;
        this.blockIndex = blockIndex;
        this.objectRef = objectRef;
        this.text = text;
        this.image = image;
    }

    public VKPdfStreamBlockType type() {
        return type;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int blockIndex() {
        return blockIndex;
    }

    public String objectRef() {
        return objectRef;
    }

    public String text() {
        return text;
    }

    public VKPdfImage image() {
        return image;
    }
}
