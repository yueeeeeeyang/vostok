package yueyang.vostok.ai.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存向量库实现。
 *
 * Perf 1：BM25 索引懒建并缓存，仅在文档集变化（版本号不同）时重建，搜索时 O(1) 读缓存。
 * Perf 5：向量插入时预计算 L2 范数，向量相似度检索退化为纯点积，省去每次 sqrt 计算。
 * Ext 5：search/searchByKeywords 均支持 Map<String,String> metadataFilter，
 *        语义为文档 metadata AND 匹配所有 filter 条目。
 */
public class VKAiInMemoryVectorStore implements VKAiVectorStore {

    // 存储原始文档
    private final ConcurrentHashMap<String, VKAiVectorDoc> docs = new ConcurrentHashMap<>();

    // Perf 5：预归一化向量缓存（L2 归一化后的 double[]）
    private final ConcurrentHashMap<String, double[]> normalizedVectors = new ConcurrentHashMap<>();

    // Perf 1：BM25 缓存及版本号
    private final AtomicLong docVersion = new AtomicLong(0);
    private volatile Bm25Index bm25Cache = null;

    // -------------------------------------------------------------------------
    // VKAiVectorStore 实现
    // -------------------------------------------------------------------------

    @Override
    public void upsert(List<VKAiVectorDoc> values) {
        if (values == null) {
            return;
        }
        boolean changed = false;
        for (VKAiVectorDoc doc : values) {
            if (doc == null || doc.getId() == null || doc.getId().isBlank()) {
                continue;
            }
            docs.put(doc.getId(), doc);
            // Perf 5：预计算归一化向量
            normalizedVectors.put(doc.getId(), normalize(doc.getVector()));
            changed = true;
        }
        if (changed) {
            docVersion.incrementAndGet();
        }
    }

    @Override
    public List<VKAiVectorHit> search(List<Double> queryVector, int topK) {
        return search(queryVector, topK, null);
    }

    @Override
    public List<VKAiVectorHit> search(List<Double> queryVector, int topK, Map<String, String> filter) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }
        // Perf 5：将查询向量也归一化，之后用纯点积代替完整 cosine 计算
        double[] qNorm = normalize(queryVector);
        List<VKAiVectorHit> out = new ArrayList<>();
        for (Map.Entry<String, VKAiVectorDoc> e : docs.entrySet()) {
            if (!matchesFilter(e.getValue(), filter)) {
                continue;
            }
            double[] dNorm = normalizedVectors.get(e.getKey());
            if (dNorm == null) {
                // 兜底：重新计算
                dNorm = normalize(e.getValue().getVector());
                normalizedVectors.put(e.getKey(), dNorm);
            }
            double score = dot(qNorm, dNorm);
            out.add(new VKAiVectorHit(e.getValue().getId(), e.getValue().getText(), score, e.getValue().getMetadata()));
        }
        out.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        if (out.size() > topK) {
            return List.copyOf(out.subList(0, topK));
        }
        return List.copyOf(out);
    }

    @Override
    public List<VKAiVectorHit> searchByKeywords(String query, int topK) {
        return searchByKeywords(query, topK, null);
    }

    @Override
    public List<VKAiVectorHit> searchByKeywords(String query, int topK, Map<String, String> filter) {
        if (query == null || query.isBlank() || topK <= 0 || docs.isEmpty()) {
            return List.of();
        }
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return List.of();
        }

        // Perf 1：获取（或重建）BM25 缓存索引
        Bm25Index idx = getOrBuildBm25Index();

        final double k1 = 1.2;
        final double b = 0.75;

        List<VKAiVectorHit> out = new ArrayList<>();
        for (Map.Entry<String, VKAiVectorDoc> e : docs.entrySet()) {
            if (!matchesFilter(e.getValue(), filter)) {
                continue;
            }
            String id = e.getKey();
            Map<String, Integer> tf = idx.tf.getOrDefault(id, Collections.emptyMap());
            int dl = Math.max(1, idx.docLen.getOrDefault(id, 0));

            double score = 0.0;
            for (String q : qTokens) {
                int f = tf.getOrDefault(q, 0);
                if (f <= 0) {
                    continue;
                }
                int df = idx.docFreq.getOrDefault(q, 0);
                double idf = Math.log(1.0 + (idx.nDocs - df + 0.5) / (df + 0.5));
                double tfNorm = (f * (k1 + 1.0)) / (f + k1 * (1.0 - b + b * dl / idx.avgdl));
                score += idf * tfNorm;
            }
            if (score > 0.0) {
                VKAiVectorDoc doc = e.getValue();
                out.add(new VKAiVectorHit(doc.getId(), doc.getText(), score, doc.getMetadata()));
            }
        }

        out.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        if (out.size() > topK) {
            return List.copyOf(out.subList(0, topK));
        }
        return List.copyOf(out);
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null) {
            return;
        }
        boolean changed = false;
        for (String id : ids) {
            if (id != null && docs.remove(id) != null) {
                normalizedVectors.remove(id);
                changed = true;
            }
        }
        if (changed) {
            docVersion.incrementAndGet();
        }
    }

    @Override
    public void clear() {
        docs.clear();
        normalizedVectors.clear();
        docVersion.incrementAndGet();
        bm25Cache = null;
    }

    // -------------------------------------------------------------------------
    // Perf 1：BM25 索引懒建缓存
    // -------------------------------------------------------------------------

    /**
     * 双检锁：若缓存版本与当前文档版本一致则直接复用；否则在同步块内重建。
     */
    private Bm25Index getOrBuildBm25Index() {
        long ver = docVersion.get();
        Bm25Index cached = bm25Cache;
        if (cached != null && cached.version == ver) {
            return cached;
        }
        synchronized (this) {
            ver = docVersion.get();
            cached = bm25Cache;
            if (cached != null && cached.version == ver) {
                return cached;
            }
            cached = buildBm25Index(ver);
            bm25Cache = cached;
            return cached;
        }
    }

    private Bm25Index buildBm25Index(long ver) {
        int nDocs = docs.size();
        Map<String, Map<String, Integer>> tf = new HashMap<>(nDocs * 2);
        Map<String, Integer> docFreq = new HashMap<>();
        Map<String, Integer> docLen = new HashMap<>(nDocs * 2);
        long totalLen = 0;

        for (Map.Entry<String, VKAiVectorDoc> e : docs.entrySet()) {
            String id = e.getKey();
            List<String> tokens = tokenize(e.getValue().getText());
            int len = tokens.size();
            totalLen += len;
            docLen.put(id, len);
            Map<String, Integer> tfMap = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (String t : tokens) {
                tfMap.put(t, tfMap.getOrDefault(t, 0) + 1);
                if (seen.add(t)) {
                    docFreq.put(t, docFreq.getOrDefault(t, 0) + 1);
                }
            }
            tf.put(id, tfMap);
        }

        double avgdl = nDocs == 0 ? 1.0 : Math.max(1.0, (double) totalLen / nDocs);
        return new Bm25Index(ver, nDocs, tf, docFreq, docLen, avgdl);
    }

    // -------------------------------------------------------------------------
    // Perf 5：向量归一化与点积
    // -------------------------------------------------------------------------

    /**
     * 计算 L2 归一化向量（返回 double[]）。
     * 若范数为 0 则返回全零数组（零向量与任何向量的相似度均为 0）。
     */
    static double[] normalize(List<Double> vec) {
        if (vec == null || vec.isEmpty()) {
            return new double[0];
        }
        double[] arr = new double[vec.size()];
        double norm = 0.0;
        for (int i = 0; i < vec.size(); i++) {
            double v = vec.get(i) == null ? 0.0 : vec.get(i);
            arr[i] = v;
            norm += v * v;
        }
        if (norm <= 0.0) {
            return arr;
        }
        double invNorm = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < arr.length; i++) {
            arr[i] *= invNorm;
        }
        return arr;
    }

    /** 点积（用于归一化向量的相似度计算，等价于 cosine similarity）。 */
    private static double dot(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0;
        }
        int n = Math.min(a.length, b.length);
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // Ext 5：元数据过滤
    // -------------------------------------------------------------------------

    /**
     * 判断文档是否通过元数据过滤。
     * filter 为 null 或空时直接通过；否则文档 metadata 需包含所有 filter 键值对（AND 语义）。
     */
    private static boolean matchesFilter(VKAiVectorDoc doc, Map<String, String> filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        Map<String, String> meta = doc.getMetadata();
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> f : filter.entrySet()) {
            if (!f.getValue().equals(meta.get(f.getKey()))) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // 分词（复用）
    // -------------------------------------------------------------------------

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] arr = text.toLowerCase().split("[^a-z0-9]+");
        List<String> out = new ArrayList<>(arr.length);
        for (String t : arr) {
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // 内部 BM25 索引结构
    // -------------------------------------------------------------------------

    /** 不可变 BM25 索引快照，携带构建时的 docVersion 用于缓存失效判断。 */
    private static final class Bm25Index {
        final long version;
        final int nDocs;
        final Map<String, Map<String, Integer>> tf;   // docId → (term → count)
        final Map<String, Integer> docFreq;            // term → 文档频率
        final Map<String, Integer> docLen;             // docId → token 数
        final double avgdl;

        Bm25Index(long version, int nDocs,
                  Map<String, Map<String, Integer>> tf,
                  Map<String, Integer> docFreq,
                  Map<String, Integer> docLen,
                  double avgdl) {
            this.version = version;
            this.nDocs = nDocs;
            this.tf = tf;
            this.docFreq = docFreq;
            this.docLen = docLen;
            this.avgdl = avgdl;
        }
    }
}
