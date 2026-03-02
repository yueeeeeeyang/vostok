package yueyang.vostok.ai.core;

import yueyang.vostok.ai.VKAiMemoryStore;
import yueyang.vostok.ai.VKAiSession;
import yueyang.vostok.ai.VKAiSessionMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话/消息存储。
 *
 * Perf 3：appendMessage 改为二分查找有序插入，替代"插入后全量排序"；
 *         listMessages 无需重排，直接返回已排序副本，省去重复 sort 开销。
 */
public class VKAiInMemoryMemoryStore implements VKAiMemoryStore {
    private final ConcurrentHashMap<String, VKAiSession> sessions = new ConcurrentHashMap<>();
    // value 始终保持按 seq 有序的 ArrayList
    private final ConcurrentHashMap<String, List<VKAiSessionMessage>> messages = new ConcurrentHashMap<>();

    private static final Comparator<VKAiSessionMessage> SEQ_CMP = Comparator.comparingLong(VKAiSessionMessage::getSeq);

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
            VKAiSessionMessage copy = message.copy();
            // Perf 3：二分查找有序插入，避免全量 sort
            int idx = Collections.binarySearch(list, copy, SEQ_CMP);
            // binarySearch 返回负值时 -(idx+1) 为插入位置
            int pos = idx >= 0 ? idx : -(idx + 1);
            list.add(pos, copy);
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
            // 列表已通过有序插入维护顺序，无需重排
            List<VKAiSessionMessage> out = new ArrayList<>(list.size());
            for (VKAiSessionMessage it : list) {
                out.add(it.copy());
            }
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
    public void updateSessionMetadata(String sessionId, Map<String, String> metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        VKAiSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        // 更新 metadata 并写回（sessions map 存的是可变对象，用 copy 换新引用保证可见性）
        VKAiSession updated = session.copy().metadata(metadata).updatedAt(System.currentTimeMillis());
        sessions.put(sessionId, updated);
    }

    @Override
    public void close() {
        sessions.clear();
        messages.clear();
    }
}
