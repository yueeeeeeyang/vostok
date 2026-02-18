package yueyang.vostok.file.exception;

public class VKFileException extends RuntimeException {
    private final VKFileErrorCode errorCode;

    public VKFileException(VKFileErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VKFileException(VKFileErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public VKFileErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
