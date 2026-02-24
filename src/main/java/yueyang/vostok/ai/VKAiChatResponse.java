package yueyang.vostok.ai;

import yueyang.vostok.ai.tool.VKAiToolCallResult;

public class VKAiChatResponse {
    private final String text;
    private final String finishReason;
    private final VKAiUsage usage;
    private final long latencyMs;
    private final String providerRequestId;
    private final int statusCode;
    private final java.util.List<VKAiToolCallResult> toolResults;

    public VKAiChatResponse(String text,
                            String finishReason,
                            VKAiUsage usage,
                            long latencyMs,
                            String providerRequestId,
                            int statusCode,
                            java.util.List<VKAiToolCallResult> toolResults) {
        this.text = text;
        this.finishReason = finishReason;
        this.usage = usage;
        this.latencyMs = latencyMs;
        this.providerRequestId = providerRequestId;
        this.statusCode = statusCode;
        this.toolResults = toolResults == null ? java.util.List.of() : java.util.List.copyOf(toolResults);
    }

    public String getText() {
        return text;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public VKAiUsage getUsage() {
        return usage;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public java.util.List<VKAiToolCallResult> getToolResults() {
        return toolResults;
    }
}
