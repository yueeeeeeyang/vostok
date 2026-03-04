package yueyang.vostok.web.mvc;

/** 参数绑定异常（对应 4xx）。 */
public class VKMvcBindException extends RuntimeException {
    public VKMvcBindException(String message) {
        super(message);
    }

    public VKMvcBindException(String message, Throwable cause) {
        super(message, cause);
    }
}
