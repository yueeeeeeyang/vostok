package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.VKAiMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VKAiChatRequestOps {
    private VKAiChatRequestOps() {
    }

    static List<VKAiMessage> trimHistoryMessages(VKAiChatRequest request) {
        List<VKAiMessage> raw = request.getMessages();
        if (raw.isEmpty() || !request.isHistoryTrimEnabled()) {
            return raw;
        }
        int maxMessages = request.getHistoryMaxMessages() == null ? 12 : Math.max(1, request.getHistoryMaxMessages());
        int maxChars = request.getHistoryMaxChars() == null ? 4000 : Math.max(128, request.getHistoryMaxChars());

        List<VKAiMessage> reversed = new ArrayList<>();
        int totalChars = 0;
        for (int i = raw.size() - 1; i >= 0; i--) {
            VKAiMessage msg = raw.get(i);
            if (msg == null || msg.getRole() == null || msg.getRole().isBlank()) {
                continue;
            }
            String content = msg.getContent() == null ? "" : msg.getContent().trim();
            if (reversed.isEmpty() && content.length() > maxChars) {
                content = content.substring(content.length() - maxChars);
            }
            if (!reversed.isEmpty() && totalChars + content.length() > maxChars) {
                break;
            }
            if (reversed.size() >= maxMessages) {
                break;
            }
            reversed.add(new VKAiMessage(msg.getRole(), content));
            totalChars += content.length();
        }
        List<VKAiMessage> out = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            out.add(reversed.get(i));
        }
        return out;
    }

    static Set<String> normalizeToolAllowlist(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String name : raw) {
            if (name != null && !name.isBlank()) {
                out.add(name.trim());
            }
        }
        return Set.copyOf(out);
    }
}
