package yueyang.vostok.exception;

/**
 * 参数非法异常。
 */
public class VKArgumentException extends VKException {
    public VKArgumentException(String message) {
        super(VKErrorCode.INVALID_ARGUMENT, message);
    }

    public VKArgumentException(String message, Throwable cause) {
        super(VKErrorCode.INVALID_ARGUMENT, message, cause);
    }
}
