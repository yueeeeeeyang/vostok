package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiUsage;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;
import yueyang.vostok.ai.tool.VKAiToolCall;
import yueyang.vostok.util.json.VKJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VKAiJsonOps {
    private VKAiJsonOps() {
    }

    static ParsedProviderResponse parseProviderResponse(String body) {
        Map<?, ?> root;
        try {
            root = VKJson.fromJson(body, Map.class);
        } catch (Exception e) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR,
                    "Failed to parse provider response as JSON", e);
        }

        Object idObj = root.get("id");
        String providerRequestId = idObj == null ? null : String.valueOf(idObj);

        List<?> choices = asList(root.get("choices"));
        if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new VKAiException(VKAiErrorCode.SERIALIZATION_ERROR, "Provider response missing choices[0]");
        }

        String finishReason = choice.get("finish_reason") == null ? null : String.valueOf(choice.get("finish_reason"));
        Map<?, ?> message = asMap(choice.get("message"));
        String text = message.get("content") == null ? "" : String.valueOf(message.get("content"));

        Map<?, ?> usageMap = asMap(root.get("usage"));
        int promptTokens = asInt(usageMap.get("prompt_tokens"));
        int completionTokens = asInt(usageMap.get("completion_tokens"));
        int totalTokensVal = asInt(usageMap.get("total_tokens"));
        VKAiUsage usage = new VKAiUsage(promptTokens, completionTokens, totalTokensVal);

        List<VKAiToolCall> toolCalls = parseToolCalls(message.get("tool_calls"));
        return new ParsedProviderResponse(providerRequestId, text, finishReason, usage, toolCalls);
    }

    static List<VKAiToolCall> parseToolCalls(Object raw) {
        List<?> list = asList(raw);
        if (list.isEmpty()) {
            return List.of();
        }
        List<VKAiToolCall> out = new ArrayList<>();
        for (Object item : list) {
            Map<?, ?> m = asMap(item);
            if (m.isEmpty()) {
                continue;
            }
            String id = m.get("id") == null ? null : String.valueOf(m.get("id"));
            Map<?, ?> fn = asMap(m.get("function"));
            String name = fn.get("name") == null ? null : String.valueOf(fn.get("name"));
            String args = fn.get("arguments") == null ? "{}" : String.valueOf(fn.get("arguments"));
            if (name == null || name.isBlank()) {
                continue;
            }
            out.add(new VKAiToolCall(id, name, args));
        }
        return out;
    }

    static Map<String, Object> parseJsonOrEmptyObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            Map<?, ?> map = VKJson.fromJson(json, Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    static Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    static int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return 0;
        }
    }

    static double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return 0.0;
        }
    }

    static List<Double> toDoubleList(List<?> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Double> out = new ArrayList<>(input.size());
        for (Object it : input) {
            out.add(asDouble(it));
        }
        return out;
    }

    record ParsedProviderResponse(
            String providerRequestId,
            String text,
            String finishReason,
            VKAiUsage usage,
            List<VKAiToolCall> toolCalls
    ) {
    }
}
