package yueyang.vostok.ai.core;

import yueyang.vostok.data.annotation.VKColumn;
import yueyang.vostok.data.annotation.VKId;
import yueyang.vostok.util.annotation.VKEntity;

@VKEntity(table = "vk_ai_session_message")
public class VKAiDataSessionMessageEntity {
    @VKId
    private Long id;

    @VKColumn(name = "session_id")
    private String sessionId;

    @VKColumn(name = "seq")
    private Long seq;

    @VKColumn(name = "role")
    private String role;

    @VKColumn(name = "content")
    private String content;

    @VKColumn(name = "model")
    private String model;

    @VKColumn(name = "created_at")
    private Long createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
