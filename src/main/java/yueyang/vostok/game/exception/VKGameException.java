package yueyang.vostok.game.exception;

public class VKGameException extends RuntimeException {
    private final VKGameErrorCode code;

    public VKGameException(VKGameErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public VKGameException(VKGameErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public VKGameErrorCode getCode() {
        return code;
    }
}
