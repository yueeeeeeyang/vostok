package yueyang.vostok.ai.tool;

public class VKAiToolCallResult {
    private final String callId;
    private final String toolName;
    private final boolean success;
    private final String outputJson;
    private final String error;
    private final long costMs;

    public VKAiToolCallResult(String callId, String toolName, boolean success, String outputJson, String error, long costMs) {
        this.callId = callId;
        this.toolName = toolName;
        this.success = success;
        this.outputJson = outputJson;
        this.error = error;
        this.costMs = costMs;
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public String getError() {
        return error;
    }

    public long getCostMs() {
        return costMs;
    }
}
