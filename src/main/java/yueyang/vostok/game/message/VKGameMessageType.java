package yueyang.vostok.game.message;

public enum VKGameMessageType {
    PLAYER_CHAT(false, false),
    PLAYER_EMOTE(false, false),
    SYSTEM_NOTICE(true, false),
    SYSTEM_ALERT(true, true),
    SYSTEM_PENALTY(true, true);

    private final boolean systemMessage;
    private final boolean requireAck;

    VKGameMessageType(boolean systemMessage, boolean requireAck) {
        this.systemMessage = systemMessage;
        this.requireAck = requireAck;
    }

    public boolean isSystemMessage() {
        return systemMessage;
    }

    public boolean isRequireAck() {
        return requireAck;
    }
}
