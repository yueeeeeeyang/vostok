package yueyang.vostok.office.ppt.stream;

import yueyang.vostok.office.ppt.VKPptImage;

/** PPT 流式读取块。 */
public final class VKPptStreamBlock {
    private final VKPptStreamBlockType type;
    private final int slideIndex;
    private final int blockIndex;
    private final String sourcePart;
    private final String text;
    private final VKPptImage image;

    public VKPptStreamBlock(VKPptStreamBlockType type,
                            int slideIndex,
                            int blockIndex,
                            String sourcePart,
                            String text,
                            VKPptImage image) {
        this.type = type;
        this.slideIndex = slideIndex;
        this.blockIndex = blockIndex;
        this.sourcePart = sourcePart;
        this.text = text;
        this.image = image;
    }

    public VKPptStreamBlockType type() {
        return type;
    }

    public int slideIndex() {
        return slideIndex;
    }

    public int blockIndex() {
        return blockIndex;
    }

    public String sourcePart() {
        return sourcePart;
    }

    public String text() {
        return text;
    }

    public VKPptImage image() {
        return image;
    }
}
