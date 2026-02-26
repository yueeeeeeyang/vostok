package yueyang.vostok.ai.core;

import yueyang.vostok.Vostok;
import yueyang.vostok.ai.VKAiMemoryStore;
import yueyang.vostok.ai.VKAiSession;
import yueyang.vostok.ai.VKAiSessionMessage;
import yueyang.vostok.data.query.VKCondition;
import yueyang.vostok.data.query.VKOperator;
import yueyang.vostok.data.query.VKOrder;
import yueyang.vostok.data.query.VKQuery;
import yueyang.vostok.util.json.VKJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VKAiDataMemoryStore implements VKAiMemoryStore {
    @Override
    public VKAiSession saveSession(VKAiSession session) {
        if (session == null || session.getSessionId() == null || session.getSessionId().isBlank()) {
            return null;
        }
        ensureDataStarted();
        VKAiDataSessionEntity row = toSessionEntity(session);
        VKAiDataSessionEntity existing = Vostok.Data.findById(VKAiDataSessionEntity.class, session.getSessionId());
        if (existing == null) {
            Vostok.Data.insert(row);
        } else {
            Vostok.Data.update(row);
        }
        return findSession(session.getSessionId());
    }

    @Override
    public VKAiSession findSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        ensureDataStarted();
        VKAiDataSessionEntity row = Vostok.Data.findById(VKAiDataSessionEntity.class, sessionId);
        return toSession(row);
    }

    @Override
    public void appendMessage(VKAiSessionMessage message) {
        if (message == null || message.getSessionId() == null || message.getSessionId().isBlank()) {
            return;
        }
        ensureDataStarted();
        Vostok.Data.insert(toMessageEntity(message));
    }

    @Override
    public List<VKAiSessionMessage> listMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        ensureDataStarted();
        VKQuery query = VKQuery.create()
                .where(VKCondition.of("sessionId", VKOperator.EQ, sessionId))
                .orderBy(VKOrder.asc("seq"));
        List<VKAiDataSessionMessageEntity> rows = Vostok.Data.query(VKAiDataSessionMessageEntity.class, query);
        List<VKAiSessionMessage> out = new ArrayList<>(rows.size());
        for (VKAiDataSessionMessageEntity row : rows) {
            out.add(toMessage(row));
        }
        return out;
    }

    @Override
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ensureDataStarted();
        VKQuery query = VKQuery.create().where(VKCondition.of("sessionId", VKOperator.EQ, sessionId));
        List<VKAiDataSessionMessageEntity> rows = Vostok.Data.query(VKAiDataSessionMessageEntity.class, query);
        for (VKAiDataSessionMessageEntity row : rows) {
            if (row.getId() != null) {
                Vostok.Data.delete(VKAiDataSessionMessageEntity.class, row.getId());
            }
        }
        Vostok.Data.delete(VKAiDataSessionEntity.class, sessionId);
    }

    private static VKAiDataSessionEntity toSessionEntity(VKAiSession session) {
        VKAiDataSessionEntity row = new VKAiDataSessionEntity();
        row.setSessionId(session.getSessionId());
        row.setCurrentModel(session.getCurrentModel());
        row.setCreatedAt(session.getCreatedAt());
        row.setUpdatedAt(session.getUpdatedAt());
        row.setMetadataJson(VKJson.toJson(session.getMetadata()));
        return row;
    }

    private static VKAiSession toSession(VKAiDataSessionEntity row) {
        if (row == null) {
            return null;
        }
        Map<String, String> metadata;
        try {
            metadata = row.getMetadataJson() == null || row.getMetadataJson().isBlank()
                    ? Map.of()
                    : VKJson.fromJson(row.getMetadataJson(), Map.class);
        } catch (Exception ignore) {
            metadata = Map.of();
        }
        return new VKAiSession()
                .sessionId(row.getSessionId())
                .currentModel(row.getCurrentModel())
                .createdAt(row.getCreatedAt() == null ? 0L : row.getCreatedAt())
                .updatedAt(row.getUpdatedAt() == null ? 0L : row.getUpdatedAt())
                .metadata(metadata);
    }

    private static VKAiDataSessionMessageEntity toMessageEntity(VKAiSessionMessage message) {
        VKAiDataSessionMessageEntity row = new VKAiDataSessionMessageEntity();
        row.setSessionId(message.getSessionId());
        row.setSeq(message.getSeq());
        row.setRole(message.getRole());
        row.setContent(message.getContent());
        row.setModel(message.getModel());
        row.setCreatedAt(message.getTimestamp());
        return row;
    }

    private static VKAiSessionMessage toMessage(VKAiDataSessionMessageEntity row) {
        return new VKAiSessionMessage()
                .sessionId(row.getSessionId())
                .seq(row.getSeq() == null ? 0L : row.getSeq())
                .role(row.getRole())
                .content(row.getContent())
                .model(row.getModel())
                .timestamp(row.getCreatedAt() == null ? 0L : row.getCreatedAt());
    }

    private static void ensureDataStarted() {
        if (!Vostok.Data.started()) {
            throw new IllegalStateException("Vostok.Data is not started, cannot use VKAiDataMemoryStore");
        }
    }
}
