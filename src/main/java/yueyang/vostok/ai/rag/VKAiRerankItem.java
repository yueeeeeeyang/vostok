package yueyang.vostok.ai.rag;

public class VKAiRerankItem {
    private final int index;
    private final String text;

    public VKAiRerankItem(int index, String text) {
        this.index = index;
        this.text = text;
    }

    public int getIndex() {
        return index;
    }

    public String getText() {
        return text;
    }
}
