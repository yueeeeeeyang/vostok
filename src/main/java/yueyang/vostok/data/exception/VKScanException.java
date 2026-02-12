package yueyang.vostok.data.exception;

/**
 * 扫描异常。
 */
public class VKScanException extends VKException {
    public VKScanException(String message) {
        super(VKErrorCode.SCAN_ERROR, message);
    }

    public VKScanException(String message, Throwable cause) {
        super(VKErrorCode.SCAN_ERROR, message, cause);
    }
}
