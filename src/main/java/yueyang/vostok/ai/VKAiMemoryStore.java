package yueyang.vostok.ai;

import java.util.List;
import java.util.Map;

public interface VKAiMemoryStore {
    VKAiSession saveSession(VKAiSession session);

    VKAiSession findSession(String sessionId);

    void appendMessage(VKAiSessionMessage message);

    List<VKAiSessionMessage> listMessages(String sessionId);

    void deleteSession(String sessionId);

    /**
     * 更新会话 metadata（Ext 6）。
     * 默认实现基于 findSession + saveSession，实现类可以重写以提供原子更新。
     */
    default void updateSessionMetadata(String sessionId, Map<String, String> metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        VKAiSession session = findSession(sessionId);
        if (session == null) {
            return;
        }
        saveSession(session.metadata(metadata).updatedAt(System.currentTimeMillis()));
    }

    default void close() {
    }
}
