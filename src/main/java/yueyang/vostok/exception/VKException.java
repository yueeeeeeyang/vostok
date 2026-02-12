package yueyang.vostok.exception;

public class VKException extends RuntimeException {
    private final VKErrorCode errorCode;

    public VKException(VKErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VKException(VKErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public VKErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
