package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiMemoryStore;
import yueyang.vostok.ai.VKAiSession;
import yueyang.vostok.ai.VKAiSessionMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class VKAiInMemoryMemoryStore implements VKAiMemoryStore {
    private final ConcurrentHashMap<String, VKAiSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<VKAiSessionMessage>> messages = new ConcurrentHashMap<>();

    @Override
    public VKAiSession saveSession(VKAiSession session) {
        if (session == null || session.getSessionId() == null || session.getSessionId().isBlank()) {
            return null;
        }
        VKAiSession copied = session.copy();
        sessions.put(copied.getSessionId(), copied);
        messages.computeIfAbsent(copied.getSessionId(), k -> new ArrayList<>());
        return copied.copy();
    }

    @Override
    public VKAiSession findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        VKAiSession session = sessions.get(sessionId);
        return session == null ? null : session.copy();
    }

    @Override
    public void appendMessage(VKAiSessionMessage message) {
        if (message == null || message.getSessionId() == null || message.getSessionId().isBlank()) {
            return;
        }
        List<VKAiSessionMessage> list = messages.computeIfAbsent(message.getSessionId(), k -> new ArrayList<>());
        synchronized (list) {
            list.add(message.copy());
            list.sort(Comparator.comparingLong(VKAiSessionMessage::getSeq));
        }
    }

    @Override
    public List<VKAiSessionMessage> listMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        List<VKAiSessionMessage> list = messages.get(sessionId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            List<VKAiSessionMessage> out = new ArrayList<>(list.size());
            for (VKAiSessionMessage it : list) {
                out.add(it.copy());
            }
            out.sort(Comparator.comparingLong(VKAiSessionMessage::getSeq));
            return out;
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(sessionId);
        messages.remove(sessionId);
    }

    @Override
    public void close() {
        sessions.clear();
        messages.clear();
    }
}
