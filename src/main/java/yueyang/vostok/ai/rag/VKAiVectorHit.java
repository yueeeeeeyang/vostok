package yueyang.vostok.ai.rag;

public class VKAiVectorHit {
    private final String id;
    private final String text;
    private final double score;
    private final java.util.Map<String, String> metadata;

    public VKAiVectorHit(String id, String text, double score, java.util.Map<String, String> metadata) {
        this.id = id;
        this.text = text;
        this.score = score;
        this.metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public double getScore() {
        return score;
    }

    public java.util.Map<String, String> getMetadata() {
        return metadata;
    }
}
