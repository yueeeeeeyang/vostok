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

public class VKAiInMemoryVectorStore implements VKAiVectorStore {
    private final ConcurrentHashMap<String, VKAiVectorDoc> docs = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<VKAiVectorDoc> values) {
        if (values == null) {
            return;
        }
        for (VKAiVectorDoc doc : values) {
            if (doc == null || doc.getId() == null || doc.getId().isBlank()) {
                continue;
            }
            docs.put(doc.getId(), doc);
        }
    }

    @Override
    public List<VKAiVectorHit> search(List<Double> queryVector, int topK) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }
        List<VKAiVectorHit> out = new ArrayList<>();
        for (Map.Entry<String, VKAiVectorDoc> e : docs.entrySet()) {
            VKAiVectorDoc doc = e.getValue();
            double score = cosine(queryVector, doc.getVector());
            out.add(new VKAiVectorHit(doc.getId(), doc.getText(), score, doc.getMetadata()));
        }
        out.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        if (out.size() > topK) {
            return List.copyOf(out.subList(0, topK));
        }
        return List.copyOf(out);
    }

    @Override
    public List<VKAiVectorHit> searchByKeywords(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0 || docs.isEmpty()) {
            return List.of();
        }

        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty()) {
            return List.of();
        }

        int nDocs = docs.size();
        Map<String, Integer> docFreq = new HashMap<>();
        Map<String, Map<String, Integer>> tfByDoc = new HashMap<>();
        Map<String, Integer> docLen = new HashMap<>();
        int totalLen = 0;

        for (Map.Entry<String, VKAiVectorDoc> e : docs.entrySet()) {
            String id = e.getKey();
            List<String> tokens = tokenize(e.getValue().getText());
            totalLen += tokens.size();
            docLen.put(id, tokens.size());
            Map<String, Integer> tf = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (String t : tokens) {
                tf.put(t, tf.getOrDefault(t, 0) + 1);
                if (seen.add(t)) {
                    docFreq.put(t, docFreq.getOrDefault(t, 0) + 1);
                }
            }
            tfByDoc.put(id, tf);
        }

        double avgdl = nDocs == 0 ? 1.0 : Math.max(1.0, (double) totalLen / nDocs);
        final double k1 = 1.2;
        final double b = 0.75;

        List<VKAiVectorHit> out = new ArrayList<>();
        for (Map.Entry<String, VKAiVectorDoc> e : docs.entrySet()) {
            String id = e.getKey();
            Map<String, Integer> tf = tfByDoc.getOrDefault(id, Collections.emptyMap());
            int dl = Math.max(1, docLen.getOrDefault(id, 0));

            double score = 0.0;
            for (String q : qTokens) {
                int f = tf.getOrDefault(q, 0);
                if (f <= 0) {
                    continue;
                }
                int df = docFreq.getOrDefault(q, 0);
                double idf = Math.log(1.0 + (nDocs - df + 0.5) / (df + 0.5));
                double tfNorm = (f * (k1 + 1.0)) / (f + k1 * (1.0 - b + b * dl / avgdl));
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
        for (String id : ids) {
            if (id != null) {
                docs.remove(id);
            }
        }
    }

    @Override
    public void clear() {
        docs.clear();
    }

    private static double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int n = Math.min(a.size(), b.size());
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < n; i++) {
            double av = a.get(i) == null ? 0.0 : a.get(i);
            double bv = b.get(i) == null ? 0.0 : b.get(i);
            dot += av * bv;
            na += av * av;
            nb += bv * bv;
        }
        if (na <= 0.0 || nb <= 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

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
}
