package yueyang.vostok.data.exception;

/**
 * 状态异常（例如未初始化）。
 */
public class VKStateException extends VKException {
    public VKStateException(VKErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public VKStateException(VKErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
