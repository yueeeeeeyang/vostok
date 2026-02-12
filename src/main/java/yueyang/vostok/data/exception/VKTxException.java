package yueyang.vostok.data.exception;

/**
 * 事务异常。
 */
public class VKTxException extends VKException {
    public VKTxException(String message) {
        super(VKErrorCode.TX_ERROR, message);
    }

    public VKTxException(String message, Throwable cause) {
        super(VKErrorCode.TX_ERROR, message, cause);
    }
}
