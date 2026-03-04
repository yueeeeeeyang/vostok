package yueyang.vostok.office.word.stream;

import yueyang.vostok.office.word.VKWordImage;

/** Word 流式读取块。 */
public final class VKWordStreamBlock {
    private final VKWordStreamBlockType type;
    private final int sectionIndex;
    private final int paragraphIndex;
    private final String sourcePart;
    private final String text;
    private final VKWordImage image;

    public VKWordStreamBlock(VKWordStreamBlockType type,
                             int sectionIndex,
                             int paragraphIndex,
                             String sourcePart,
                             String text,
                             VKWordImage image) {
        this.type = type;
        this.sectionIndex = sectionIndex;
        this.paragraphIndex = paragraphIndex;
        this.sourcePart = sourcePart;
        this.text = text;
        this.image = image;
    }

    public VKWordStreamBlockType type() {
        return type;
    }

    public int sectionIndex() {
        return sectionIndex;
    }

    public int paragraphIndex() {
        return paragraphIndex;
    }

    public String sourcePart() {
        return sourcePart;
    }

    public String text() {
        return text;
    }

    public VKWordImage image() {
        return image;
    }
}
