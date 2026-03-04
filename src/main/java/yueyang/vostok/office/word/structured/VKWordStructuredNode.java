package yueyang.vostok.office.word.structured;

import yueyang.vostok.office.word.VKWordImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Word 结构化节点。 */
public final class VKWordStructuredNode {
    private final VKWordStructuredNodeType type;
    private final int sectionIndex;
    private final int paragraphIndex;
    private final String sourcePart;
    private final String text;
    private final VKWordImage image;
    private final List<VKWordStructuredNode> children = new ArrayList<>();

    public VKWordStructuredNode(VKWordStructuredNodeType type,
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

    public VKWordStructuredNode addChild(VKWordStructuredNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    public VKWordStructuredNodeType type() {
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

    public List<VKWordStructuredNode> children() {
        return Collections.unmodifiableList(children);
    }
}
