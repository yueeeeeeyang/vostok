package yueyang.vostok.web.mvc;

/** 控制器方法调用异常（对应 5xx）。 */
public class VKMvcInvokeException extends RuntimeException {
    public VKMvcInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}
