package yueyang.vostok.ai;

public class VKAiUsage {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    public VKAiUsage() {
    }

    public VKAiUsage(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }
}
