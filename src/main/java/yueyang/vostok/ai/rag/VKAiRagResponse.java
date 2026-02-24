package yueyang.vostok.ai.rag;

import yueyang.vostok.ai.VKAiChatResponse;

import java.util.List;

public class VKAiRagResponse {
    private final VKAiChatResponse answer;
    private final List<VKAiVectorHit> hits;

    public VKAiRagResponse(VKAiChatResponse answer, List<VKAiVectorHit> hits) {
        this.answer = answer;
        this.hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public VKAiChatResponse getAnswer() {
        return answer;
    }

    public List<VKAiVectorHit> getHits() {
        return hits;
    }
}
