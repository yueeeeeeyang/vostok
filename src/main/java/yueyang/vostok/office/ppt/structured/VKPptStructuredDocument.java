package yueyang.vostok.office.ppt.structured;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PPT 结构化提取结果。 */
public final class VKPptStructuredDocument {
    private final List<VKPptStructuredNode> nodes = new ArrayList<>();

    public VKPptStructuredDocument addNode(VKPptStructuredNode node) {
        if (node != null) {
            nodes.add(node);
        }
        return this;
    }

    public List<VKPptStructuredNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }
}
