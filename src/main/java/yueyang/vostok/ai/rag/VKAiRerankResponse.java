package yueyang.vostok.ai.rag;

import java.util.List;

public class VKAiRerankResponse {
    private final List<VKAiRerankResult> results;
    private final long latencyMs;

    public VKAiRerankResponse(List<VKAiRerankResult> results, long latencyMs) {
        this.results = results == null ? List.of() : List.copyOf(results);
        this.latencyMs = latencyMs;
    }

    public List<VKAiRerankResult> getResults() {
        return results;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
