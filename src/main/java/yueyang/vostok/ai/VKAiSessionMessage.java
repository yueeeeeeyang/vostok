package yueyang.vostok.ai;

public class VKAiSessionMessage {
    private String sessionId;
    private long seq;
    private String role;
    private String content;
    private String model;
    private long timestamp;

    public String getSessionId() {
        return sessionId;
    }

    public VKAiSessionMessage sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public long getSeq() {
        return seq;
    }

    public VKAiSessionMessage seq(long seq) {
        this.seq = seq;
        return this;
    }

    public String getRole() {
        return role;
    }

    public VKAiSessionMessage role(String role) {
        this.role = role;
        return this;
    }

    public String getContent() {
        return content;
    }

    public VKAiSessionMessage content(String content) {
        this.content = content;
        return this;
    }

    public String getModel() {
        return model;
    }

    public VKAiSessionMessage model(String model) {
        this.model = model;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public VKAiSessionMessage timestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public VKAiSessionMessage copy() {
        return new VKAiSessionMessage()
                .sessionId(sessionId)
                .seq(seq)
                .role(role)
                .content(content)
                .model(model)
                .timestamp(timestamp);
    }
}
