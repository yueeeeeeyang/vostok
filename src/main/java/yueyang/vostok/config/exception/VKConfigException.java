package yueyang.vostok.config.exception;

public class VKConfigException extends RuntimeException {
    private final VKConfigErrorCode errorCode;

    public VKConfigException(VKConfigErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VKConfigException(VKConfigErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public VKConfigErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
