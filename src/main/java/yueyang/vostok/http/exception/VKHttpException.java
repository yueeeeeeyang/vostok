package yueyang.vostok.http.exception;

public class VKHttpException extends RuntimeException {
    private final VKHttpErrorCode code;
    private final Integer statusCode;

    public VKHttpException(VKHttpErrorCode code, String message) {
        super(message);
        this.code = code;
        this.statusCode = null;
    }

    public VKHttpException(VKHttpErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = null;
    }

    public VKHttpException(VKHttpErrorCode code, String message, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public VKHttpErrorCode getCode() {
        return code;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
