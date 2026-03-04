package yueyang.vostok.office.exception;

/** Office 模块统一运行时异常。 */
public class VKOfficeException extends RuntimeException {
    private final VKOfficeErrorCode errorCode;

    public VKOfficeException(VKOfficeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public VKOfficeException(VKOfficeErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public VKOfficeErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
