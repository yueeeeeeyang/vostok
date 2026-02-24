package yueyang.vostok.ai;

public class VKAiMessage {
    private String role;
    private String content;

    public VKAiMessage() {
    }

    public VKAiMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public VKAiMessage role(String role) {
        this.role = role;
        return this;
    }

    public String getContent() {
        return content;
    }

    public VKAiMessage content(String content) {
        this.content = content;
        return this;
    }
}
