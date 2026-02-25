package yueyang.vostok.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public class VKAiSession {
    private String sessionId;
    private String profileName;
    private String currentModel;
    private long createdAt;
    private long updatedAt;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public String getSessionId() {
        return sessionId;
    }

    public VKAiSession sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public String getProfileName() {
        return profileName;
    }

    public VKAiSession profileName(String profileName) {
        this.profileName = profileName;
        return this;
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public VKAiSession currentModel(String currentModel) {
        this.currentModel = currentModel;
        return this;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public VKAiSession createdAt(long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public VKAiSession updatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Map<String, String> getMetadata() {
        return Map.copyOf(metadata);
    }

    public VKAiSession metadata(Map<String, String> metadata) {
        this.metadata.clear();
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
        return this;
    }

    public VKAiSession putMetadata(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            this.metadata.put(key.trim(), value);
        }
        return this;
    }

    public VKAiSession copy() {
        return new VKAiSession()
                .sessionId(sessionId)
                .profileName(profileName)
                .currentModel(currentModel)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .metadata(metadata);
    }
}
