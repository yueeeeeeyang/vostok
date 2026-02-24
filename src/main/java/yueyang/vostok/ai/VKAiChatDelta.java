package yueyang.vostok.ai;

public class VKAiChatDelta {
    private final String contentDelta;
    private final boolean done;

    public VKAiChatDelta(String contentDelta, boolean done) {
        this.contentDelta = contentDelta;
        this.done = done;
    }

    public String getContentDelta() {
        return contentDelta;
    }

    public boolean isDone() {
        return done;
    }
}
