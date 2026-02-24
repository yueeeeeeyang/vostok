package yueyang.vostok.ai.exception;

public class VKAiException extends RuntimeException {
    private final VKAiErrorCode code;
    private final Integer statusCode;

    public VKAiException(VKAiErrorCode code, String message) {
        super(message);
        this.code = code;
        this.statusCode = null;
    }

    public VKAiException(VKAiErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = null;
    }

    public VKAiException(VKAiErrorCode code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public VKAiErrorCode getCode() {
        return code;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
