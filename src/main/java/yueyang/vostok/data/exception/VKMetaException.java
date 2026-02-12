package yueyang.vostok.data.exception;

/**
 * 元数据异常。
 */
public class VKMetaException extends VKException {
    public VKMetaException(String message) {
        super(VKErrorCode.META_ERROR, message);
    }

    public VKMetaException(String message, Throwable cause) {
        super(VKErrorCode.META_ERROR, message, cause);
    }
}
