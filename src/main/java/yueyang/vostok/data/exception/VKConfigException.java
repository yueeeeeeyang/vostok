package yueyang.vostok.data.exception;

/**
 * 配置异常。
 */
public class VKConfigException extends VKException {
    public VKConfigException(String message) {
        super(VKErrorCode.CONFIG_ERROR, message);
    }

    public VKConfigException(String message, Throwable cause) {
        super(VKErrorCode.CONFIG_ERROR, message, cause);
    }
}
