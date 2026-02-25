package yueyang.vostok.ai.core;

import yueyang.vostok.ai.rag.VKAiRagRequest;
import yueyang.vostok.ai.rag.VKAiVectorHit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VKAiRagOps {
    private VKAiRagOps() {
    }

    static List<VKAiVectorHit> mergeHybridHits(List<VKAiVectorHit> vectorHits,
                                               List<VKAiVectorHit> keywordHits,
                                               VKAiRagRequest request) {
        Map<String, Double> combined = new LinkedHashMap<>();
        Map<String, VKAiVectorHit> hitById = new LinkedHashMap<>();
        combineHits(combined, hitById, vectorHits, request.getVectorWeight());
        combineHits(combined, hitById, keywordHits, request.getKeywordWeight());

        List<VKAiVectorHit> merged = new ArrayList<>(combined.size());
        for (Map.Entry<String, Double> e : combined.entrySet()) {
            VKAiVectorHit h = hitById.get(e.getKey());
            if (h != null) {
                merged.add(new VKAiVectorHit(h.getId(), h.getText(), e.getValue(), h.getMetadata()));
            }
        }
        merged.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        return deduplicateByText(merged, Math.max(request.getTopK() * 3, request.getTopK()));
    }

    static int computeDynamicTopK(int baseTopK, String query) {
        int base = Math.max(1, baseTopK);
        String[] terms = tokenizeForRewrite(query);
        int termCount = terms.length;
        if (termCount <= 3) {
            return Math.min(base, 2);
        }
        if (termCount <= 8) {
            return Math.min(base, 3);
        }
        if (termCount >= 20) {
            return Math.min(Math.max(base + 1, 4), 8);
        }
        return base;
    }

    static int dynamicCandidateTopK(int configuredTopK, int effectiveTopK) {
        int floor = Math.max(1, effectiveTopK);
        int cap = Math.max(floor, floor * 3);
        return Math.max(floor, Math.min(Math.max(1, configuredTopK), cap));
    }

    static List<VKAiVectorHit> mergeSimilarChunks(List<VKAiVectorHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, List<VKAiVectorHit>> grouped = new LinkedHashMap<>();
        List<VKAiVectorHit> passthrough = new ArrayList<>();
        for (VKAiVectorHit hit : hits) {
            if (hit == null) {
                continue;
            }
            String docId = hit.getMetadata().get("vk_doc_id");
            Integer idx = parseChunkIndex(hit);
            if (docId == null || idx == null) {
                passthrough.add(hit);
                continue;
            }
            String version = hit.getMetadata().get("vk_doc_version");
            String key = docId + "#" + (version == null ? "" : version);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(hit);
        }

        List<VKAiVectorHit> merged = new ArrayList<>(passthrough);
        for (List<VKAiVectorHit> group : grouped.values()) {
            group.sort(Comparator.comparingInt(VKAiRagOps::parseChunkIndex));
            VKAiVectorHit current = null;
            int prevIdx = Integer.MIN_VALUE;
            for (VKAiVectorHit hit : group) {
                int idx = parseChunkIndex(hit);
                if (current == null) {
                    current = hit;
                    prevIdx = idx;
                    continue;
                }
                boolean adjacent = idx - prevIdx <= 1;
                boolean similar = lexicalSimilarity(current.getText(), hit.getText()) >= 0.6;
                if (adjacent || similar) {
                    current = mergeTwoHits(current, hit);
                    prevIdx = idx;
                } else {
                    merged.add(current);
                    current = hit;
                    prevIdx = idx;
                }
            }
            if (current != null) {
                merged.add(current);
            }
        }
        merged.sort(Comparator.comparingDouble(VKAiVectorHit::getScore).reversed());
        return merged;
    }

    static List<VKAiVectorHit> compressContextHits(List<VKAiVectorHit> hits,
                                                   int limit,
                                                   int maxPerChunkChars,
                                                   int maxTotalChars) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        int maxPer = Math.max(64, maxPerChunkChars);
        int maxTotal = Math.max(maxPer, maxTotalChars);
        List<VKAiVectorHit> out = new ArrayList<>(Math.min(limit, hits.size()));
        int total = 0;
        for (VKAiVectorHit hit : hits) {
            if (out.size() >= limit) {
                break;
            }
            String text = compressSnippet(hit.getText(), maxPer);
            if (text.isBlank()) {
                continue;
            }
            if (total + text.length() > maxTotal) {
                int remaining = maxTotal - total;
                if (remaining < 32) {
                    break;
                }
                text = compressSnippet(text, remaining);
                if (text.isBlank()) {
                    break;
                }
            }
            total += text.length();
            out.add(new VKAiVectorHit(hit.getId(), text, hit.getScore(), hit.getMetadata()));
        }
        return out;
    }

    static String rewriteRagQueryLight(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        String normalized = rawQuery.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 96) {
            return normalized;
        }
        String candidate = pickQuestionCandidate(normalized);
        List<String> keywords = extractLightKeywords(normalized, 6);
        String out = candidate;
        for (String kw : keywords) {
            if (!out.toLowerCase().contains(kw.toLowerCase())) {
                out += " " + kw;
            }
        }
        if (out.length() > 128) {
            out = out.substring(0, 128);
        }
        return out.trim();
    }

    static String docKey(String documentId, String version) {
        return documentId + "#" + version;
    }

    static String normalizeForDedup(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String x = Integer.toHexString(b & 0xff);
                if (x.length() == 1) {
                    sb.append('0');
                }
                sb.append(x);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void combineHits(Map<String, Double> combined,
                                    Map<String, VKAiVectorHit> hitById,
                                    List<VKAiVectorHit> hits,
                                    double weight) {
        if (hits == null || hits.isEmpty() || weight <= 0.0) {
            return;
        }
        double maxScore = 0.0;
        for (VKAiVectorHit h : hits) {
            if (h != null && h.getScore() > maxScore) {
                maxScore = h.getScore();
            }
        }
        if (maxScore <= 0.0) {
            return;
        }
        for (VKAiVectorHit h : hits) {
            if (h == null || h.getId() == null) {
                continue;
            }
            double normalized = Math.max(0.0, h.getScore()) / maxScore;
            combined.put(h.getId(), combined.getOrDefault(h.getId(), 0.0) + weight * normalized);
            hitById.putIfAbsent(h.getId(), h);
        }
    }

    private static List<VKAiVectorHit> deduplicateByText(List<VKAiVectorHit> hits, int limit) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<VKAiVectorHit> out = new ArrayList<>(Math.min(limit, hits.size()));
        for (VKAiVectorHit hit : hits) {
            if (hit == null || hit.getText() == null) {
                continue;
            }
            String key = normalizeForDedup(hit.getText());
            if (seen.add(key)) {
                out.add(hit);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private static VKAiVectorHit mergeTwoHits(VKAiVectorHit a, VKAiVectorHit b) {
        String left = a.getText() == null ? "" : a.getText().trim();
        String right = b.getText() == null ? "" : b.getText().trim();
        String text = left.contains(right) ? left : (right.contains(left) ? right : (left + "\n" + right));
        Map<String, String> metadata = new LinkedHashMap<>(a.getMetadata());
        Integer ai = parseChunkIndex(a);
        Integer bi = parseChunkIndex(b);
        if (ai != null && bi != null) {
            metadata.put("vk_chunk_span", Math.min(ai, bi) + "-" + Math.max(ai, bi));
        }
        String id = a.getId().equals(b.getId()) ? a.getId() : a.getId() + "~" + b.getId();
        return new VKAiVectorHit(id, text, Math.max(a.getScore(), b.getScore()), metadata);
    }

    private static Integer parseChunkIndex(VKAiVectorHit hit) {
        if (hit == null || hit.getMetadata() == null) {
            return null;
        }
        String idx = hit.getMetadata().get("vk_chunk_index");
        if (idx == null || idx.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(idx);
        } catch (Exception e) {
            return null;
        }
    }

    private static double lexicalSimilarity(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        LinkedHashSet<String> sa = new LinkedHashSet<>(List.of(tokenizeForRewrite(a)));
        LinkedHashSet<String> sb = new LinkedHashSet<>(List.of(tokenizeForRewrite(b)));
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String t : sa) {
            if (sb.contains(t)) {
                inter++;
            }
        }
        int union = sa.size() + sb.size() - inter;
        if (union <= 0) {
            return 0.0;
        }
        return (double) inter / union;
    }

    private static String compressSnippet(String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        int max = Math.max(32, maxChars);
        if (text.length() <= max) {
            return text;
        }
        int cut = max;
        int min = Math.max(20, max / 2);
        for (int i = max - 1; i >= min; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == ';' || c == '；') {
                cut = i + 1;
                break;
            }
        }
        String snippet = text.substring(0, Math.min(cut, text.length())).trim();
        return snippet.length() < text.length() ? snippet + "..." : snippet;
    }

    private static String pickQuestionCandidate(String normalized) {
        String[] parts = normalized.split("[。！？!?;；\\n\\r]");
        String best = "";
        for (String p : parts) {
            String s = p == null ? "" : p.trim();
            if (s.isBlank()) {
                continue;
            }
            boolean hasQuestionMarker = s.contains("如何") || s.contains("怎么") || s.contains("为什么")
                    || s.contains("什么") || s.contains("请问") || s.endsWith("?") || s.endsWith("？");
            if (hasQuestionMarker) {
                best = s;
            } else if (best.isBlank() && s.length() > best.length()) {
                best = s;
            }
        }
        if (best.isBlank()) {
            best = normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
        }
        return best.length() > 96 ? best.substring(0, 96) : best;
    }

    private static List<String> extractLightKeywords(String input, int limit) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher latin = Pattern.compile("[a-zA-Z0-9_\\-]{3,}").matcher(input.toLowerCase());
        while (latin.find() && out.size() < limit) {
            out.add(latin.group());
        }
        Matcher han = Pattern.compile("[\\p{IsHan}]{2,6}").matcher(input);
        while (han.find() && out.size() < limit) {
            out.add(han.group());
        }
        return new ArrayList<>(out);
    }

    private static String[] tokenizeForRewrite(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        String[] arr = text.toLowerCase().split("[^\\p{IsHan}a-z0-9_\\-]+");
        List<String> out = new ArrayList<>(arr.length);
        for (String it : arr) {
            if (it != null && !it.isBlank()) {
                out.add(it);
            }
        }
        return out.toArray(new String[0]);
    }
}
