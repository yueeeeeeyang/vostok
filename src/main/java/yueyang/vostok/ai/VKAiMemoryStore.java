package yueyang.vostok.ai;

import java.util.List;

public interface VKAiMemoryStore {
    VKAiSession saveSession(VKAiSession session);

    VKAiSession findSession(String sessionId);

    void appendMessage(VKAiSessionMessage message);

    List<VKAiSessionMessage> listMessages(String sessionId);

    void deleteSession(String sessionId);

    default void close() {
    }
}
