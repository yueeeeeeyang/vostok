package yueyang.vostok.ai.rag;

import java.util.Map;

public class VKAiVectorDoc {
    private final String id;
    private final String text;
    private final java.util.List<Double> vector;
    private final Map<String, String> metadata;

    public VKAiVectorDoc(String id, String text, java.util.List<Double> vector, Map<String, String> metadata) {
        this.id = id;
        this.text = text;
        this.vector = vector == null ? java.util.List.of() : java.util.List.copyOf(vector);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public java.util.List<Double> getVector() {
        return vector;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
