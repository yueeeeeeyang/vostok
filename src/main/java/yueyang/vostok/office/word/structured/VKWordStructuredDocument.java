package yueyang.vostok.office.word.structured;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Word 结构化提取结果。 */
public final class VKWordStructuredDocument {
    private final List<VKWordStructuredNode> nodes = new ArrayList<>();

    public VKWordStructuredDocument addNode(VKWordStructuredNode node) {
        if (node != null) {
            nodes.add(node);
        }
        return this;
    }

    public List<VKWordStructuredNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }
}
