package yueyang.vostok.office.ppt.structured;

import yueyang.vostok.office.ppt.VKPptImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PPT 结构化节点。 */
public final class VKPptStructuredNode {
    private final VKPptStructuredNodeType type;
    private final int slideIndex;
    private final String sourcePart;
    private final String text;
    private final VKPptImage image;
    private final List<VKPptStructuredNode> children = new ArrayList<>();

    public VKPptStructuredNode(VKPptStructuredNodeType type,
                               int slideIndex,
                               String sourcePart,
                               String text,
                               VKPptImage image) {
        this.type = type;
        this.slideIndex = slideIndex;
        this.sourcePart = sourcePart;
        this.text = text;
        this.image = image;
    }

    public VKPptStructuredNode addChild(VKPptStructuredNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    public VKPptStructuredNodeType type() {
        return type;
    }

    public int slideIndex() {
        return slideIndex;
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

    public List<VKPptStructuredNode> children() {
        return Collections.unmodifiableList(children);
    }
}
