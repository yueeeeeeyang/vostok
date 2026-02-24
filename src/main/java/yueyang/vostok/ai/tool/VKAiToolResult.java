package yueyang.vostok.ai.tool;

public class VKAiToolResult {
    private final String outputJson;

    public VKAiToolResult(String outputJson) {
        this.outputJson = outputJson == null ? "{}" : outputJson;
    }

    public static VKAiToolResult ofJson(String outputJson) {
        return new VKAiToolResult(outputJson);
    }

    public String getOutputJson() {
        return outputJson;
    }
}
