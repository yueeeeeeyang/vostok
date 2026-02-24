package yueyang.vostok.ai.rag;

import java.util.List;

public class VKAiEmbedding {
    private final int index;
    private final List<Double> vector;

    public VKAiEmbedding(int index, List<Double> vector) {
        this.index = index;
        this.vector = vector == null ? List.of() : List.copyOf(vector);
    }

    public int getIndex() {
        return index;
    }

    public List<Double> getVector() {
        return vector;
    }
}
