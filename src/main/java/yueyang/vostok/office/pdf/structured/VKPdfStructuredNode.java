package yueyang.vostok.office.pdf.structured;

import yueyang.vostok.office.pdf.VKPdfImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PDF 结构化节点。 */
public final class VKPdfStructuredNode {
    private final VKPdfStructuredNodeType type;
    private final int pageIndex;
    private final String objectRef;
    private final String text;
    private final VKPdfImage image;
    private final List<VKPdfStructuredNode> children = new ArrayList<>();

    public VKPdfStructuredNode(VKPdfStructuredNodeType type,
                               int pageIndex,
                               String objectRef,
                               String text,
                               VKPdfImage image) {
        this.type = type;
        this.pageIndex = pageIndex;
        this.objectRef = objectRef;
        this.text = text;
        this.image = image;
    }

    public VKPdfStructuredNode addChild(VKPdfStructuredNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    public VKPdfStructuredNodeType type() {
        return type;
    }

    public int pageIndex() {
        return pageIndex;
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

    public List<VKPdfStructuredNode> children() {
        return Collections.unmodifiableList(children);
    }
}
