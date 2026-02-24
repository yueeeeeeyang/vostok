package yueyang.vostok.ai.rag;

import java.util.List;

public interface VKAiVectorStore {
    void upsert(List<VKAiVectorDoc> docs);

    List<VKAiVectorHit> search(java.util.List<Double> queryVector, int topK);

    default List<VKAiVectorHit> searchByKeywords(String query, int topK) {
        return List.of();
    }

    void deleteByIds(List<String> ids);

    void clear();
}
