package yueyang.vostok.ai.core;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.util.annotation.VKEntity;

@VKEntity(table = "vk_ai_session")
public class VKAiDataSessionEntity {
    @VKId(auto = false)
    @VKColumn(name = "session_id")
    private String sessionId;

    @VKColumn(name = "profile_name")
    private String profileName;

    @VKColumn(name = "current_model")
    private String currentModel;

    @VKColumn(name = "created_at")
    private Long createdAt;

    @VKColumn(name = "updated_at")
    private Long updatedAt;

    @VKColumn(name = "metadata_json")
    private String metadataJson;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}
