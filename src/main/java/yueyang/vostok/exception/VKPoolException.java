package yueyang.vostok.exception;

/**
 * 连接池异常。
 */
public class VKPoolException extends VKException {
    public VKPoolException(String message) {
        super(VKErrorCode.POOL_ERROR, message);
    }

    public VKPoolException(String message, Throwable cause) {
        super(VKErrorCode.POOL_ERROR, message, cause);
    }
}
