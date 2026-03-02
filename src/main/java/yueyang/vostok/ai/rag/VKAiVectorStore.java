package yueyang.vostok.ai.rag;

import java.util.List;
import java.util.Map;

public interface VKAiVectorStore {
    void upsert(List<VKAiVectorDoc> docs);

    List<VKAiVectorHit> search(List<Double> queryVector, int topK);

    /**
     * 支持元数据过滤的向量检索（Ext 5）。
     * 默认实现：忽略 filter，调用无 filter 版本。
     * filter 为 null 或空时等价于无过滤。
     * 过滤语义：文档 metadata 必须包含所有 filter 条目（AND 关系）。
     */
    default List<VKAiVectorHit> search(List<Double> queryVector, int topK, Map<String, String> filter) {
        return search(queryVector, topK);
    }

    default List<VKAiVectorHit> searchByKeywords(String query, int topK) {
        return List.of();
    }

    /**
     * 支持元数据过滤的关键词检索（Ext 5）。
     * 默认实现：忽略 filter，调用无 filter 版本。
     */
    default List<VKAiVectorHit> searchByKeywords(String query, int topK, Map<String, String> filter) {
        return searchByKeywords(query, topK);
    }

    void deleteByIds(List<String> ids);

    void clear();
}
