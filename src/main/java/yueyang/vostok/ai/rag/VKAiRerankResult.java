package yueyang.vostok.ai.rag;

public class VKAiRerankResult {
    private final int index;
    private final double score;
    private final String document;

    public VKAiRerankResult(int index, double score, String document) {
        this.index = index;
        this.score = score;
        this.document = document;
    }

    public int getIndex() {
        return index;
    }

    public double getScore() {
        return score;
    }

    public String getDocument() {
        return document;
    }
}
