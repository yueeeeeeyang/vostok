package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiChatResponse;
import yueyang.vostok.ai.VKAiUsage;
import yueyang.vostok.ai.rag.VKAiRagRequest;
import yueyang.vostok.ai.rag.VKAiVectorHit;
import yueyang.vostok.util.json.VKJson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VKAiRuntimeSupportOps {
    private VKAiRuntimeSupportOps() {
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    static String shortHash(String raw) {
        String digest = VKAiRagOps.sha256Hex(raw == null ? "" : raw);
        return digest.length() > 24 ? digest.substring(0, 24) : digest;
    }

    static String buildRagAnswerCacheMaterial(VKAiRagRequest request,
                                              String chatClientName,
                                              String embeddingClientName,
                                              String rerankClientName,
                                              String systemPrompt,
                                              List<VKAiVectorHit> hits) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(chatClientName).append('|')
                .append(embeddingClientName).append('|')
                .append(rerankClientName).append('|')
                .append(request.getModel()).append('|')
                .append(request.getEmbeddingModel()).append('|')
                .append(request.getRerankModel()).append('|')
                .append(request.getQuery()).append('|')
                .append(request.getTopK()).append('|')
                .append(systemPrompt == null ? "" : systemPrompt);
        for (VKAiVectorHit hit : hits) {
            sb.append('|').append(hit.getId()).append('@').append(hit.getScore());
            String version = hit.getMetadata() == null ? null : hit.getMetadata().get("vk_doc_version");
            if (version != null) {
                sb.append('#').append(version);
            }
        }
        return sb.toString();
    }

    static String encodeChatResponseForCache(VKAiChatResponse answer) {
        if (answer == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", answer.getText());
        payload.put("finishReason", answer.getFinishReason());
        payload.put("providerRequestId", answer.getProviderRequestId());
        payload.put("statusCode", answer.getStatusCode());
        VKAiUsage usage = answer.getUsage();
        if (usage != null) {
            payload.put("promptTokens", usage.getPromptTokens());
            payload.put("completionTokens", usage.getCompletionTokens());
            payload.put("totalTokens", usage.getTotalTokens());
        }
        return VKJson.toJson(payload);
    }

    static VKAiChatResponse decodeCachedChatResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> payload = VKJson.fromJson(raw, Map.class);
            String text = payload.get("text") == null ? "" : String.valueOf(payload.get("text"));
            String finishReason = payload.get("finishReason") == null ? "cached" : String.valueOf(payload.get("finishReason"));
            String requestId = payload.get("providerRequestId") == null ? "cached" : String.valueOf(payload.get("providerRequestId"));
            int statusCode = VKAiJsonOps.asInt(payload.get("statusCode"));
            VKAiUsage usage = new VKAiUsage(
                    VKAiJsonOps.asInt(payload.get("promptTokens")),
                    VKAiJsonOps.asInt(payload.get("completionTokens")),
                    VKAiJsonOps.asInt(payload.get("totalTokens"))
            );
            return new VKAiChatResponse(text, finishReason, usage, 0, requestId, statusCode, List.of());
        } catch (Exception e) {
            return null;
        }
    }

    static String joinBaseAndPath(String baseUrl, String path) {
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (path == null || path.isBlank()) {
            return b;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }
}
