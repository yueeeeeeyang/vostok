package yueyang.vostok.office.pdf.structured;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PDF 结构化提取结果。 */
public final class VKPdfStructuredDocument {
    private final List<VKPdfStructuredNode> nodes = new ArrayList<>();

    public VKPdfStructuredDocument addNode(VKPdfStructuredNode node) {
        if (node != null) {
            nodes.add(node);
        }
        return this;
    }

    public List<VKPdfStructuredNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }
}
