package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiChatRequest;
import yueyang.vostok.ai.VKAiChatResponse;
import yueyang.vostok.ai.VKAiMemoryStore;
import yueyang.vostok.ai.VKAiSession;
import yueyang.vostok.ai.VKAiSessionMessage;
import yueyang.vostok.ai.exception.VKAiErrorCode;
import yueyang.vostok.ai.exception.VKAiException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VKAiSessionOps {
    private VKAiSessionOps() {
    }

    static VKAiSession createSession(VKAiMemoryStore store, String modelName) {
        long now = System.currentTimeMillis();
        VKAiSession session = new VKAiSession()
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .currentModel(modelName)
                .createdAt(now)
                .updatedAt(now);
        VKAiSession saved = store.saveSession(session);
        return saved == null ? session.copy() : saved;
    }

    static VKAiSession requireSession(VKAiMemoryStore store, String sessionId) {
        VKAiSession session = store.findSession(sessionId);
        if (session == null) {
            throw new VKAiException(VKAiErrorCode.CONFIG_ERROR, "Ai session not found: " + sessionId);
        }
        return session;
    }

    static VKAiSession switchSessionModel(VKAiMemoryStore store, String sessionId, String model) {
        if (model == null || model.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Session model is blank");
        }
        VKAiSession session = requireSession(store, sessionId);
        session.currentModel(model.trim()).updatedAt(System.currentTimeMillis());
        VKAiSession saved = store.saveSession(session);
        return saved == null ? session.copy() : saved;
    }

    static List<VKAiSessionMessage> sessionMessages(VKAiMemoryStore store, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Session id is blank");
        }
        return store.listMessages(sessionId);
    }

    static VKAiChatResponse chatSession(VKAiMemoryStore store,
                                        String sessionId,
                                        String userText,
                                        ChatExecutor chatExecutor) {
        if (userText == null || userText.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Session user text is blank");
        }
        VKAiSession session = requireSession(store, sessionId);
        List<VKAiSessionMessage> history = store.listMessages(sessionId);
        long nextSeq = history.isEmpty() ? 1L : history.get(history.size() - 1).getSeq() + 1L;
        long now = System.currentTimeMillis();

        VKAiSessionMessage userMsg = new VKAiSessionMessage()
                .sessionId(sessionId)
                .seq(nextSeq)
                .role("user")
                .content(userText)
                .model(session.getCurrentModel())
                .timestamp(now);
        store.appendMessage(userMsg);

        VKAiChatRequest request = new VKAiChatRequest()
                .model(session.getCurrentModel())
                .historyTrimEnabled(false);
        for (VKAiSessionMessage msg : history) {
            request.message(msg.getRole(), msg.getContent());
        }
        request.message("user", userText);

        VKAiChatResponse response = chatExecutor.chat(request);
        VKAiSessionMessage assistant = new VKAiSessionMessage()
                .sessionId(sessionId)
                .seq(nextSeq + 1L)
                .role("assistant")
                .content(response.getText())
                .model(session.getCurrentModel())
                .timestamp(System.currentTimeMillis());
        store.appendMessage(assistant);

        session.updatedAt(System.currentTimeMillis());
        store.saveSession(session);
        return response;
    }

    static void deleteSession(VKAiMemoryStore store, String sessionId) {
        store.deleteSession(sessionId);
    }

    /**
     * Ext 6：更新会话 metadata。
     * 调用 store.updateSessionMetadata 以支持各实现自定义原子性保证。
     */
    static VKAiSession updateSessionMetadata(VKAiMemoryStore store, String sessionId, Map<String, String> metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new VKAiException(VKAiErrorCode.INVALID_ARGUMENT, "Session id is blank");
        }
        requireSession(store, sessionId);
        store.updateSessionMetadata(sessionId, metadata);
        return requireSession(store, sessionId);
    }

    @FunctionalInterface
    interface ChatExecutor {
        VKAiChatResponse chat(VKAiChatRequest request);
    }
}
